package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import java.util.List;
import java.util.Map;

public final class GatlingMcpServerFactory {
    public static final String SERVER_NAME = "gatling-community-mcp";
    public static final String SERVER_VERSION = "0.4.0";

    private GatlingMcpServerFactory() {
    }

    public static McpSyncServer create(McpToolRegistry registry) {
        return createStdio(registry);
    }

    public static McpSyncServer createStdio(McpToolRegistry registry) {
        var transport = new SerializedStdioServerTransportProvider(
                new StdioServerTransportProvider(McpJsonDefaults.getMapper())
        );
        return build(McpServer.sync(transport), registry);
    }

    public static McpSyncServer createStreamable(
            McpStreamableServerTransportProvider transport,
            McpToolRegistry registry
    ) {
        return build(McpServer.sync(transport), registry);
    }

    private static McpSyncServer build(McpServer.SyncSpecification<?> specification, McpToolRegistry registry) {
        var resources = registry.resources().listUris().stream()
                .map(uri -> new McpServerFeatures.SyncResourceSpecification(
                        new McpSchema.Resource(uri, uri, null, null, "text/plain", null, null, Map.of(), List.of()),
                        (exchange, request) -> McpSchema.ReadResourceResult.builder(List.of(
                                McpSchema.TextResourceContents.builder(
                                                request.uri(),
                                                registry.resources().read(request.uri())
                                        )
                                        .mimeType("text/plain")
                                        .build()
                        )).build()
                ))
                .toList();
        var prompts = registry.prompts().listNames().stream()
                .map(name -> {
                    var definition = registry.prompts().getDefinition(name);
                    return new McpServerFeatures.SyncPromptSpecification(
                        McpSchema.Prompt.builder(definition.name())
                                .title(definition.title())
                                .description(definition.description())
                                .arguments(definition.arguments())
                                .build(),
                        (exchange, request) -> McpSchema.GetPromptResult.builder(List.of(
                                McpSchema.PromptMessage.builder(
                                        McpSchema.Role.USER,
                                        McpSchema.TextContent.builder(
                                                registry.prompts().render(request.name(), request.arguments())
                                        ).build()
                                ).build()
                        )).description(registry.prompts().getDefinition(request.name()).description()).build()
                    );
                })
                .toList();
        var resourceTemplates = registry.resourceTemplates().stream()
                .map(template -> new McpServerFeatures.SyncResourceTemplateSpecification(
                        McpSchema.ResourceTemplate.builder(template.uriTemplate(), template.name())
                                .title(template.title())
                                .description(template.description())
                                .mimeType(template.mimeType())
                                .annotations(McpSchema.Annotations.builder()
                                        .audience(List.of(McpSchema.Role.USER, McpSchema.Role.ASSISTANT))
                                        .priority(template.priority())
                                        .build())
                                .build(),
                        (exchange, request) -> McpSchema.ReadResourceResult.builder(List.of(
                                McpSchema.TextResourceContents.builder(
                                                request.uri(),
                                                registry.resources().read(request.uri())
                                        )
                                        .mimeType("text/plain")
                                        .build()
                        )).build()
                ))
                .toList();
        var completions = registry.completionReferences().stream()
                .map(reference -> new McpServerFeatures.SyncCompletionSpecification(
                        reference,
                        (exchange, request) -> registry.complete(request)
                ))
                .toList();

        return specification
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .validateToolInputs(true)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
                        .prompts(true)
                        .completions()
                        .build())
                .tools(registry.toolDefinitions().stream()
                        .map(definition -> McpServerFeatures.SyncToolSpecification.builder()
                                .tool(McpSchema.Tool.builder(definition.name(), definition.inputSchema())
                                        .title(definition.title())
                                        .description(definition.description())
                                        .outputSchema(definition.outputSchema())
                                        .annotations(definition.annotations())
                                        .meta(definition.meta())
                                        .build())
                                .callHandler((exchange, request) ->
                                        new ToolResultMapper().toSdk(registry.call(
                                                request.name(),
                                                request.arguments(),
                                                SdkMcpClientInteraction.from(exchange, request)
                                        )))
                                .build())
                        .toList())
                .resources(resources)
                .resourceTemplates(resourceTemplates)
                .prompts(prompts)
                .completions(completions)
                .build();
    }
}
