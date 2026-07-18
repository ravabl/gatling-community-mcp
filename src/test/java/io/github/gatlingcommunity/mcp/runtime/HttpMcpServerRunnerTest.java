package io.github.gatlingcommunity.mcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.GatlingCommunityMcpApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpMcpServerRunnerTest {
    @Test
    void servesHealthAndRejectsBlockedBrowserOrigins() throws Exception {
        var config = httpConfig();

        try (var runner = HttpMcpServerRunner.start(config, GatlingCommunityMcpApplication.createDefaultRegistry())) {
            var http = HttpClient.newHttpClient();

            var health = http.send(HttpRequest.newBuilder(runner.healthUri()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).isEqualTo("ok");

            var blockedOrigin = http.send(HttpRequest.newBuilder(runner.endpointUri())
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .header("Content-Type", "application/json")
                            .header("Origin", "http://blocked.example")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(blockedOrigin.statusCode()).isEqualTo(403);
            assertThat(blockedOrigin.body()).contains("origin is not allowed");
        }
    }

    @Test
    void exposesMcpToolsOverStreamableHttp() throws Exception {
        withClient(client -> {
            var toolNames = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
            assertThat(toolNames).contains(
                    "gatling_resolve_capabilities",
                    "gatling_detect_project",
                    "gatling_generate_simulation",
                    "gatling_analyze_simulation",
                    "gatling_validate_feature_usage"
            );

            var result = client.callTool(new McpSchema.CallToolRequest(
                    "gatling_resolve_capabilities",
                    Map.of(
                            "gatlingVersion", "3.15",
                            "language", "JAVA",
                            "buildTool", "MAVEN",
                            "protocol", "HTTP"
                    )
            ));
            assertThat(result.structuredContent().toString()).contains("supported=true");
        });
    }

    @Test
    void exposesRichToolMetadataSchemasAndAnnotationsOverMcp() throws Exception {
        withClient(client -> {
            var tools = client.listTools().tools();
            var dynamicOutputProperties = new HashSet<String>();

            assertThat(tools).allSatisfy(tool -> {
                assertThat(tool.title()).as(tool.name()).isNotBlank();
                assertThat(tool.description()).as(tool.name()).contains("Gatling");
                assertThat(tool.inputSchema()).as(tool.name()).containsEntry("type", "object");
                assertThat(tool.inputSchema()).as(tool.name()).containsEntry("additionalProperties", false);
                assertThat(tool.outputSchema()).as(tool.name()).containsEntry("type", "object");
                assertThat(tool.outputSchema()).as(tool.name()).containsEntry("additionalProperties", false);
                assertThat(tool.outputSchema()).as(tool.name()).containsKey("anyOf");
                assertThat(tool.outputSchema().toString()).as(tool.name())
                        .doesNotContain("required=[]")
                        .doesNotContain("type=[string, number, boolean, object, array, integer]");
                collectDynamicOutputProperties(tool.outputSchema(), dynamicOutputProperties);
                assertThat(tool.meta()).as(tool.name()).containsKey("examples");
                assertThat(tool.meta().get("examples").toString()).as(tool.name()).contains("arguments");
                assertThat(tool.annotations()).as(tool.name()).isNotNull();
                if (tool.name().equals("gatling_compile_check")) {
                    assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isFalse();
                    assertThat(tool.annotations().openWorldHint()).as(tool.name()).isTrue();
                } else {
                    assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isTrue();
                    assertThat(tool.annotations().openWorldHint()).as(tool.name()).isFalse();
                }
                assertThat(tool.annotations().destructiveHint()).as(tool.name()).isFalse();
                assertThat(tool.annotations().idempotentHint()).as(tool.name()).isTrue();
            });
            assertThat(Set.of("details", "headers", "groups", "queryParams", "formParams",
                            "resolvedProperties", "sources"))
                    .containsAll(dynamicOutputProperties);

            var planTool = tools.stream()
                    .filter(tool -> tool.name().equals("gatling_plan_simulation"))
                    .findFirst()
                    .orElseThrow();
            assertThat(planTool.description()).contains("structured HTTP simulation plan");
            assertThat(planTool.outputSchema().toString()).contains(
                    "plan", "methodRequirements", "findings", "feeders", "steps",
                    "protocolOptions", "queryParams", "formParams", "multipartParts",
                    "resources", "auth", "cookies", "http2", "proxyHost");

            var validatePlanTool = tools.stream()
                    .filter(tool -> tool.name().equals("gatling_validate_simulation_plan"))
                    .findFirst()
                    .orElseThrow();
            assertThat(validatePlanTool.inputSchema().toString()).contains(
                    "feeders", "steps", "protocolOptions", "queryParams", "formParams",
                    "multipartParts", "resources", "auth", "cookies");

            var detectTool = tools.stream()
                    .filter(tool -> tool.name().equals("gatling_detect_project"))
                    .findFirst()
                    .orElseThrow();
            assertThat(detectTool.inputSchema().toString()).contains("path");
            assertThat(detectTool.outputSchema().toString()).contains("buildTool", "language", "gatlingVersion");
        });
    }

    @Test
    void exposesPromptArgumentsAndRendersArgumentAwarePromptsOverMcp() throws Exception {
        withClient(client -> {
            var createSimulation = client.listPrompts().prompts().stream()
                    .filter(prompt -> prompt.name().equals("create_simulation"))
                    .findFirst()
                    .orElseThrow();

            assertThat(createSimulation.title()).isEqualTo("Create Gatling Simulation");
            assertThat(createSimulation.description()).contains("structured authoring flow");
            assertThat(createSimulation.arguments())
                    .extracting(McpSchema.PromptArgument::name)
                    .contains("gatlingVersion", "language", "buildTool", "protocol", "goal");
            assertThat(createSimulation.arguments().stream()
                    .filter(argument -> argument.name().equals("goal"))
                    .findFirst()
                    .orElseThrow()
                    .required()).isTrue();

            var prompt = client.getPrompt(new McpSchema.GetPromptRequest(
                    "create_simulation",
                    Map.of(
                            "gatlingVersion", "3.9.5",
                            "language", "JAVA",
                            "buildTool", "MAVEN",
                            "protocol", "HTTP",
                            "goal", "login and call orders"
                    )
            ));
            var text = prompt.messages().getFirst().content().toString();
            assertThat(text).contains(
                    "gatling_plan_simulation",
                    "gatling_validate_simulation_plan",
                    "gatling_generate_from_plan",
                    "3.9.5",
                    "JAVA",
                    "login and call orders"
            );
        });
    }

    @Test
    void exposesResourceTemplatesOverMcp() throws Exception {
        withClient(client -> {
            var templates = client.listResourceTemplates().resourceTemplates();

            assertThat(templates)
                    .extracting(McpSchema.ResourceTemplate::uriTemplate)
                    .contains(
                            "gatling://methods/http/{language}/{version}",
                            "gatling://versions/{version}/features",
                            "gatling://versions/{version}/breaking-changes"
                    );
            assertThat(templates).allSatisfy(template -> {
                assertThat(template.title()).as(template.uriTemplate()).isNotBlank();
                assertThat(template.description()).as(template.uriTemplate()).contains("Gatling");
                assertThat(template.mimeType()).as(template.uriTemplate()).isEqualTo("text/plain");
                assertThat(template.annotations()).as(template.uriTemplate()).isNotNull();
            });
        });
    }

    @Test
    void supportsPromptAndResourceTemplateCompletionsOverMcp() throws Exception {
        withClient(client -> {
            assertThat(client.getServerCapabilities().completions()).isNotNull();

            var languageCompletion = client.completeCompletion(new McpSchema.CompleteRequest(
                    new McpSchema.PromptReference("create_simulation"),
                    new McpSchema.CompleteRequest.CompleteArgument("language", "JA")
            ));
            assertThat(languageCompletion.completion().values()).contains("JAVA", "JAVASCRIPT");
            assertThat(languageCompletion.completion().hasMore()).isFalse();

            var versionCompletion = client.completeCompletion(new McpSchema.CompleteRequest(
                    new McpSchema.ResourceReference("gatling://methods/http/{language}/{version}"),
                    new McpSchema.CompleteRequest.CompleteArgument("version", "3.9")
            ));
            assertThat(versionCompletion.completion().values()).contains("3.9");

            var categoryCompletion = client.completeCompletion(new McpSchema.CompleteRequest(
                    new McpSchema.PromptReference("create_simulation"),
                    new McpSchema.CompleteRequest.CompleteArgument("protocol", "H")
            ));
            assertThat(categoryCompletion.completion().values()).contains("HTTP");

            var sourceTypeCompletion = client.completeCompletion(new McpSchema.CompleteRequest(
                    new McpSchema.PromptReference("import_http_plan"),
                    new McpSchema.CompleteRequest.CompleteArgument("sourceType", "PO")
            ));
            assertThat(sourceTypeCompletion.completion().values()).contains("POSTMAN");
        });
    }

    @Test
    void importsCurlPlanOverStreamableHttpMcp() throws Exception {
        withClient(client -> {
            var result = client.callTool(new McpSchema.CallToolRequest(
                    "gatling_import_curl",
                    Map.of(
                            "gatlingVersion", "3.9.5",
                            "language", "JAVA",
                            "buildTool", "MAVEN",
                            "protocol", "HTTP",
                            "curl", "curl 'https://api.example.test/api/orders?state=open' -H 'Accept: application/json'"
                    )
            ));

            assertThat(result.isError()).isFalse();
            assertThat(result.structuredContent().toString())
                    .contains("sourceType=CURL", "sourceVersion=command", "requestCount=1",
                            "/api/orders?state=open", "valid=true");
        });
    }

    @Test
    void supportsProgressElicitationAndSamplingOverStreamableHttpMcp() throws Exception {
        var progress = new ArrayList<McpSchema.ProgressNotification>();
        var elicitations = new ArrayList<McpSchema.ElicitFormRequest>();
        var samplingRequests = new ArrayList<McpSchema.CreateMessageRequest>();

        withInteractiveClient(progress, elicitations, samplingRequests, client -> {
            var planned = client.callTool(McpSchema.CallToolRequest.builder("gatling_plan_simulation")
                    .arguments(Map.of(
                            "gatlingVersion", "3.9.5",
                            "language", "JAVA",
                            "buildTool", "MAVEN",
                            "protocol", "HTTP",
                            "goal", "",
                            "useElicitation", true
                    ))
                    .progressToken("plan-progress")
                    .build());

            assertThat(planned.isError()).isFalse();
            assertThat(planned.structuredContent().toString())
                    .contains("ElicitedOrdersSimulation", "interaction", "elicitation=completed");
            assertThat(elicitations).hasSize(1);
            assertThat(progress).extracting(McpSchema.ProgressNotification::message)
                    .contains("Planning simulation", "Validating simulation plan", "Simulation plan ready");

            var analyzed = client.callTool(McpSchema.CallToolRequest.builder("gatling_analyze_report")
                    .arguments(Map.of(
                            "reportContent", """
                                    var stats = {
                                      "type": "GROUP",
                                      "name": "Global Information",
                                      "stats": {
                                        "numberOfRequests": {"total": "10", "ok": "8", "ko": "2"},
                                        "minResponseTime": {"total": "10"},
                                        "maxResponseTime": {"total": "2500"},
                                        "meanResponseTime": {"total": "320"},
                                        "percentiles1": {"total": "100"},
                                        "percentiles2": {"total": "250"},
                                        "percentiles3": {"total": "1600"},
                                        "percentiles4": {"total": "2500"},
                                        "meanNumberOfRequestsPerSecond": {"total": "2.0"}
                                      },
                                      "contents": {}
                                    };
                                    """,
                            "useSampling", true
                    ))
                    .progressToken("report-progress")
                    .build());

            assertThat(analyzed.isError()).isFalse();
            assertThat(analyzed.structuredContent().toString())
                    .contains("sampling=completed", "Sampled Gatling run review");
            assertThat(samplingRequests).hasSize(1);
        });
    }

    private static GatlingMcpRuntimeConfig httpConfig() {
        return new GatlingMcpRuntimeConfig(
                GatlingMcpRuntimeConfig.Mode.HTTP,
                "127.0.0.1",
                0,
                "/mcp",
                List.of("http://allowed.example"),
                URI.create("http://127.0.0.1:8765/mcp")
        );
    }

    private static void withClient(ThrowingConsumer<io.modelcontextprotocol.client.McpSyncClient> assertion)
            throws Exception {
        var config = httpConfig();
        try (var runner = HttpMcpServerRunner.start(config, GatlingCommunityMcpApplication.createDefaultRegistry())) {
            var transport = HttpClientStreamableHttpTransport.builder(runner.baseUri().toString())
                    .endpoint(config.endpoint())
                    .requestBuilder(HttpRequest.newBuilder())
                    .build();

            try (var client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("gatling-community-mcp-test", "0.4.0"))
                    .requestTimeout(Duration.ofSeconds(5))
                    .initializationTimeout(Duration.ofSeconds(5))
                    .build()) {
                var initialized = client.initialize();
                assertThat(initialized.serverInfo().name()).isEqualTo("gatling-community-mcp");
                assertion.accept(client);
            }
        }
    }

    private static void withInteractiveClient(List<McpSchema.ProgressNotification> progress,
                                              List<McpSchema.ElicitFormRequest> elicitations,
                                              List<McpSchema.CreateMessageRequest> samplingRequests,
                                              ThrowingConsumer<io.modelcontextprotocol.client.McpSyncClient> assertion)
            throws Exception {
        var config = httpConfig();
        try (var runner = HttpMcpServerRunner.start(config, GatlingCommunityMcpApplication.createDefaultRegistry())) {
            var transport = HttpClientStreamableHttpTransport.builder(runner.baseUri().toString())
                    .endpoint(config.endpoint())
                    .requestBuilder(HttpRequest.newBuilder())
                    .build();

            try (var client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("gatling-community-mcp-test", "0.4.0"))
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .sampling()
                            .elicitation(true, false)
                            .build())
                    .elicitation(request -> {
                        elicitations.add(request);
                        return McpSchema.ElicitResult.builder(McpSchema.ElicitResult.Action.ACCEPT)
                                .content(Map.of(
                                        "goal", "login, extract JWT token, call /api/orders with Authorization header",
                                        "baseUrl", "https://api.example.test",
                                        "simulationClassName", "ElicitedOrdersSimulation"
                                ))
                                .build();
                    })
                    .sampling(request -> {
                        samplingRequests.add(request);
                        return McpSchema.CreateMessageResult.builder(
                                        McpSchema.Role.ASSISTANT,
                                        McpSchema.TextContent.builder("Sampled Gatling run review").build(),
                                        "test-model")
                                .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                                .build();
                    })
                    .progressConsumer(progress::add)
                    .requestTimeout(Duration.ofSeconds(5))
                    .initializationTimeout(Duration.ofSeconds(5))
                    .build()) {
                var initialized = client.initialize();
                assertThat(initialized.serverInfo().name()).isEqualTo("gatling-community-mcp");
                assertion.accept(client);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectDynamicOutputProperties(Map<String, Object> schema, Set<String> dynamicProperties) {
        var properties = schema.get("properties");
        if (properties instanceof Map<?, ?> rawProperties) {
            rawProperties.forEach((name, value) -> {
                if (value instanceof Map<?, ?> propertySchema) {
                    var additionalProperties = propertySchema.get("additionalProperties");
                    if (additionalProperties instanceof Map<?, ?>) {
                        dynamicProperties.add(String.valueOf(name));
                    }
                    collectDynamicOutputProperties((Map<String, Object>) propertySchema, dynamicProperties);
                }
            });
        }
        var items = schema.get("items");
        if (items instanceof Map<?, ?> itemSchema) {
            collectDynamicOutputProperties((Map<String, Object>) itemSchema, dynamicProperties);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
