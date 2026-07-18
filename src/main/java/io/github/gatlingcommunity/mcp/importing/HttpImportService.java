package io.github.gatlingcommunity.mcp.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.gatlingcommunity.mcp.authoring.AssertionPlan;
import io.github.gatlingcommunity.mcp.authoring.AuthPlan;
import io.github.gatlingcommunity.mcp.authoring.CheckPlan;
import io.github.gatlingcommunity.mcp.authoring.CookiePlan;
import io.github.gatlingcommunity.mcp.authoring.HttpResourcePlan;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestOptionsPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.authoring.InjectionProfilePlan;
import io.github.gatlingcommunity.mcp.authoring.MultipartPartPlan;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class HttpImportService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options"
    );
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key"
    );
    private static final Pattern ABSOLUTE_HTTP_URL = Pattern.compile("^(https?)://([^/?#]+)(.*)$");
    private static final Pattern POSTMAN_STATUS = Pattern.compile("pm\\.response\\.to\\.have\\.status\\((\\d{3})\\)");
    private static final Pattern POSTMAN_SCHEMA_VERSION = Pattern.compile("/v(\\d+\\.\\d+\\.\\d+)/collection\\.json");
    private static final Pattern OPENAPI_PATH_PARAMETER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.-]*)}");

    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private final FeederInferenceService feederInference = new FeederInferenceService();
    private final CorrelationInferenceService correlationInference = new CorrelationInferenceService();

    public HttpImportResult importOpenApi(String document, HttpImportOptions options) {
        var root = parseDocument(document);
        var warnings = new ArrayList<ImportWarning>();
        var referenceResolver = new OpenApiReferenceResolver(root, warnings);
        var paths = map(root.get("paths"));
        var requests = new ArrayList<HttpRequestPlan>();
        var endpoints = new ArrayList<EndpointInventory.Endpoint>();
        var securitySchemes = referenceResolver.resolveMap(
                map(map(root.get("components")).get("securitySchemes")),
                "$.components.securitySchemes"
        );
        var baseUrl = firstAbsoluteServerUrl(root)
                .or(() -> swaggerBaseUrl(root))
                .or(() -> optional(options.baseUrl()))
                .orElse("https://example.test");
        var sourceVersion = openApiVersion(root);

        for (var pathEntry : paths.entrySet()) {
            var path = pathEntry.getKey();
            var pathPointer = "$.paths['%s']".formatted(path);
            var pathItem = referenceResolver.resolveMap(pathEntry.getValue(), pathPointer);
            for (var methodEntry : pathItem.entrySet()) {
                var method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                var operation = referenceResolver.resolveMap(methodEntry.getValue(), pathPointer + "." + method);
                operation = withPathItemParameters(pathItem, operation);
                warnUnsupportedOpenApiOperation(path, method, operation, warnings);
                var request = openApiRequest(path, method, operation, root, securitySchemes, warnings);
                requests.add(request);
                endpoints.add(EndpointInventory.Endpoint.fromRequest(
                        request,
                        openApiTags(operation),
                        "$.paths['%s'].%s".formatted(path, method)
                ));
            }
        }
        warnUnsupportedOpenApiRoot(root, warnings);
        if (requests.isEmpty()) {
            warnings.add(new ImportWarning("warning", "import.openapi.no.operations",
                    "$.paths", "No HTTP operations were found in the OpenAPI document."));
        }

        var inventory = EndpointInventory.fromEndpoints(endpoints, openApiAuthSchemes(root, securitySchemes), List.of());
        return result("OPENAPI", sourceVersion, options, sourceTitle(root, "Imported OpenAPI"), baseUrl,
                requests, warnings, inventory, document, openApiResponseExamples(root, referenceResolver));
    }

    public HttpImportResult importHar(String document, HttpImportOptions options) {
        var root = parseDocument(document);
        var warnings = new ArrayList<ImportWarning>();
        var requests = new ArrayList<HttpRequestPlan>();
        var log = map(root.get("log"));
        var sourceVersion = text(log.get("version"), "1.2");
        var entries = list(log.get("entries"));
        var firstBaseUrl = "";
        var primaryRequestByPage = new LinkedHashMap<String, Integer>();

        for (int i = 0; i < entries.size(); i++) {
            var entry = map(entries.get(i));
            var request = map(entry.get("request"));
            var response = map(entry.get("response"));
            var method = text(request.get("method"), "GET").toUpperCase(Locale.ROOT);
            var url = text(request.get("url"), "");
            if (firstBaseUrl.isBlank()) {
                firstBaseUrl = baseUrl(url).orElse("");
            }
            var path = pathWithQuery(url).orElse("/");
            var headers = headersFromList(request.get("headers"), warnings, "$.log.entries[%d].request.headers".formatted(i));
            var cookies = cookiesFromHar(request.get("cookies"));
            var body = text(map(request.get("postData")).get("text"), "");
            var status = text(response.get("status"), "200");
            var requestPlan = new HttpRequestPlan(
                    "%s %s".formatted(method, path),
                    method,
                    path,
                    headers,
                    Map.of(),
                    Map.of(),
                    List.of(),
                    body,
                    List.of(),
                    AuthPlan.none(),
                    cookies,
                    HttpRequestOptionsPlan.defaults(),
                    List.of(statusCheck(status))
            );
            var pageRef = text(entry.get("pageref"), "");
            if (!pageRef.isBlank() && isHarResource(entry) && primaryRequestByPage.containsKey(pageRef)) {
                var primaryIndex = primaryRequestByPage.get(pageRef);
                requests.set(primaryIndex, withAdditionalResource(requests.get(primaryIndex), requestPlan));
                continue;
            }
            requests.add(requestPlan);
            if (!pageRef.isBlank() && !isHarResource(entry)) {
                primaryRequestByPage.putIfAbsent(pageRef, requests.size() - 1);
            }
        }
        if (requests.isEmpty()) {
            warnings.add(new ImportWarning("warning", "import.har.no.entries",
                    "$.log.entries", "No HAR entries were found."));
        }

        return result("HAR", sourceVersion, options, "Imported HAR", firstBaseUrl, requests, warnings,
                null, document, harResponseExamples(root));
    }

    public HttpImportResult importCurl(String command, HttpImportOptions options) {
        var warnings = new ArrayList<ImportWarning>();
        var tokens = shellTokens(command);
        if (!tokens.isEmpty() && "curl".equals(tokens.getFirst())) {
            tokens = tokens.subList(1, tokens.size());
        }

        var headers = new LinkedHashMap<String, String>();
        var bodyParts = new ArrayList<String>();
        var queryParts = new ArrayList<String>();
        var method = "";
        var url = "";
        var getWithData = false;

        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if ("-X".equals(token) || "--request".equals(token)) {
                method = requireNext(tokens, ++i, token).toUpperCase(Locale.ROOT);
            } else if (token.startsWith("-X") && token.length() > 2) {
                method = token.substring(2).toUpperCase(Locale.ROOT);
            } else if (token.startsWith("--request=")) {
                method = token.substring("--request=".length()).toUpperCase(Locale.ROOT);
            } else if ("-H".equals(token) || "--header".equals(token)) {
                putHeader(headers, requireNext(tokens, ++i, token), warnings, "curl.headers");
            } else if (token.startsWith("--header=")) {
                putHeader(headers, token.substring("--header=".length()), warnings, "curl.headers");
            } else if (isDataOption(token)) {
                var value = requireNext(tokens, ++i, token);
                if ("--data-urlencode".equals(token) && getWithData) {
                    queryParts.add(value);
                } else {
                    bodyParts.add(value);
                }
                if ("--json".equals(token)) {
                    headers.putIfAbsent("Content-Type", "application/json");
                }
            } else if (token.startsWith("--data-raw=") || token.startsWith("--data=") || token.startsWith("--data-urlencode=")) {
                var value = token.substring(token.indexOf('=') + 1);
                if (token.startsWith("--data-urlencode=") && getWithData) {
                    queryParts.add(value);
                } else {
                    bodyParts.add(value);
                }
            } else if (token.startsWith("-d") && token.length() > 2) {
                bodyParts.add(token.substring(2));
            } else if ("-G".equals(token) || "--get".equals(token)) {
                getWithData = true;
            } else if ("--url".equals(token)) {
                url = requireNext(tokens, ++i, token);
            } else if (token.startsWith("--url=")) {
                url = token.substring("--url=".length());
            } else if (token.startsWith("--json=")) {
                bodyParts.add(token.substring("--json=".length()));
                headers.putIfAbsent("Content-Type", "application/json");
            } else if (!token.startsWith("-") && url.isBlank()) {
                url = token;
            }
        }

        if (method.isBlank()) {
            method = bodyParts.isEmpty() || getWithData ? "GET" : "POST";
        }
        var path = withQueryParts(pathWithQuery(url).orElse("/"), queryParts);
        var body = getWithData ? "" : String.join("&", bodyParts);
        var requests = List.of(new HttpRequestPlan(
                "%s %s".formatted(method, path),
                method,
                path,
                headers,
                body,
                List.of(statusCheck("200"))
        ));

        return result("CURL", "command", options, "Imported curl", baseUrl(url).orElse(""), requests, warnings,
                null, command, List.of());
    }

    public HttpImportResult importPostmanCollection(String document, HttpImportOptions options) {
        var root = parseDocument(document);
        var warnings = new ArrayList<ImportWarning>();
        var requests = new ArrayList<HttpRequestPlan>();
        var firstBaseUrl = new StringBuilder();
        var sourceVersion = postmanCollectionVersion(root);
        collectPostmanItems(list(root.get("item")), requests, warnings, firstBaseUrl);
        if (requests.isEmpty()) {
            warnings.add(new ImportWarning("warning", "import.postman.no.requests",
                    "$.item", "No Postman request items were found."));
        }

        return result("POSTMAN", sourceVersion, options, sourceTitle(root, "Imported Postman Collection"),
                firstBaseUrl.toString(), requests, warnings, null, document, postmanResponseExamples(root));
    }

    private HttpRequestPlan openApiRequest(String path,
                                           String method,
                                           Map<String, Object> operation,
                                           Map<String, Object> root,
                                           Map<String, Object> securitySchemes,
                                           List<ImportWarning> warnings) {
        var requestBody = map(operation.get("requestBody"));
        var content = map(requestBody.get("content"));
        var body = "";
        var headers = new LinkedHashMap<String, String>();
        var queryParams = openApiQueryParams(operation);
        var formParams = Map.<String, String>of();
        var multipartParts = List.<MultipartPartPlan>of();
        var auth = openApiAuth(operation, root, securitySchemes);
        if (!content.isEmpty()) {
            var mediaEntry = firstMedia(content);
            if (mediaEntry.isPresent()) {
                var mediaType = mediaEntry.orElseThrow().getKey();
                headers.put("Content-Type", mediaType);
                var media = map(mediaEntry.orElseThrow().getValue());
                if ("application/x-www-form-urlencoded".equals(mediaType)) {
                    formParams = formParamsFromOpenApiMedia(media);
                } else if ("multipart/form-data".equals(mediaType)) {
                    multipartParts = multipartPartsFromOpenApiMedia(media);
                } else {
                    body = bodyFromOpenApiMedia(media);
                }
            }
        } else {
            var bodyParameter = swaggerBodyParameter(operation);
            if (!bodyParameter.isEmpty()) {
                headers.put("Content-Type", swaggerContentType(operation, root));
                body = toJson(exampleFromSchema(map(bodyParameter.get("schema")), ""));
            }
        }
        if (path.contains("{")) {
            warnings.add(new ImportWarning("warning", "import.openapi.path.parameters",
                    "$.paths.%s".formatted(path), "Path parameters were preserved as Gatling path placeholders."));
        }
        var resolvedPath = openApiPath(path);
        return new HttpRequestPlan(
                text(operation.get("operationId"), "%s %s".formatted(method.toUpperCase(Locale.ROOT), path)),
                method.toUpperCase(Locale.ROOT),
                resolvedPath,
                headers,
                queryParams,
                formParams,
                multipartParts,
                body,
                List.of(),
                auth,
                List.of(),
                HttpRequestOptionsPlan.defaults(),
                List.of(statusCheck(openApiStatus(operation)))
        );
    }

    private void collectPostmanItems(Object items,
                                     List<HttpRequestPlan> requests,
                                     List<ImportWarning> warnings,
                                     StringBuilder firstBaseUrl) {
        for (var item : list(items)) {
            var itemMap = map(item);
            if (itemMap.containsKey("item")) {
                collectPostmanItems(itemMap.get("item"), requests, warnings, firstBaseUrl);
                continue;
            }
            var request = map(itemMap.get("request"));
            if (request.isEmpty()) {
                continue;
            }
            var method = text(request.get("method"), "GET").toUpperCase(Locale.ROOT);
            var rawUrl = postmanUrl(request.get("url"));
            if (firstBaseUrl.isEmpty()) {
                baseUrl(rawUrl).ifPresent(firstBaseUrl::append);
            }
            var path = pathWithQuery(rawUrl).orElse("/");
            var headers = headersFromPostman(request.get("header"), warnings);
            var body = text(map(request.get("body")).get("raw"), "");
            var status = postmanStatus(itemMap).orElse("200");
            requests.add(new HttpRequestPlan(
                    text(itemMap.get("name"), "%s %s".formatted(method, path)),
                    method,
                    path,
                    headers,
                    body,
                    List.of(statusCheck(status))
            ));
        }
    }

    private HttpImportResult result(String sourceType,
                                    String sourceVersion,
                                    HttpImportOptions options,
                                    String defaultScenarioName,
                                    String discoveredBaseUrl,
                                    List<HttpRequestPlan> requests,
                                    List<ImportWarning> warnings) {
        return result(sourceType, sourceVersion, options, defaultScenarioName, discoveredBaseUrl, requests,
                warnings, null, "", List.of());
    }

    private HttpImportResult result(String sourceType,
                                    String sourceVersion,
                                    HttpImportOptions options,
                                    String defaultScenarioName,
                                    String discoveredBaseUrl,
                                    List<HttpRequestPlan> requests,
                                    List<ImportWarning> warnings,
                                    EndpointInventory endpointInventory) {
        return result(sourceType, sourceVersion, options, defaultScenarioName, discoveredBaseUrl, requests,
                warnings, endpointInventory, "", List.of());
    }

    private HttpImportResult result(String sourceType,
                                    String sourceVersion,
                                    HttpImportOptions options,
                                    String defaultScenarioName,
                                    String discoveredBaseUrl,
                                    List<HttpRequestPlan> requests,
                                    List<ImportWarning> warnings,
                                    EndpointInventory endpointInventory,
                                    String rawImportText,
                                    List<Object> responseExamples) {
        var baseUrl = optional(options.baseUrl())
                .or(() -> optional(discoveredBaseUrl))
                .orElse("https://example.test");
        var plan = new HttpSimulationPlan(
                options.simulationClassName(),
                optional(options.scenarioName()).orElse(defaultScenarioName),
                trimTrailingSlash(baseUrl),
                requests,
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );
        var resolvedInventory = endpointInventory == null ? EndpointInventory.fromRequests(requests) : endpointInventory;
        var feederCandidates = feederInference.inferRequestPlaceholderCandidates(requests);
        var correlationCandidates = correlationInference.inferCandidates(responseExamples);
        var correlationUsages = correlationInference.findUsagesInText(rawImportText, correlationCandidates);
        var resolvedWarnings = new ArrayList<>(warnings == null ? List.<ImportWarning>of() : warnings);
        addInferenceWarnings(resolvedWarnings, feederCandidates, correlationCandidates, correlationUsages,
                resolvedInventory, rawImportText);
        return new HttpImportResult(sourceType, sourceVersion, plan, resolvedWarnings, resolvedInventory,
                feederCandidates, correlationCandidates, correlationUsages);
    }

    private Map<String, Object> parseDocument(String document) {
        try {
            return json.readValue(document, MAP_TYPE);
        } catch (JsonProcessingException jsonFailure) {
            try {
                return yaml.readValue(document, MAP_TYPE);
            } catch (JsonProcessingException yamlFailure) {
                throw new IllegalArgumentException("Document is not valid JSON or YAML", yamlFailure);
            }
        }
    }

    private String bodyFromOpenApiMedia(Map<String, Object> media) {
        var example = media.get("example");
        if (example == null) {
            var examples = map(media.get("examples"));
            if (!examples.isEmpty()) {
                example = map(examples.values().iterator().next()).get("value");
            }
        }
        if (example != null) {
            return toJson(example);
        }
        var schema = map(media.get("schema"));
        if (!schema.isEmpty()) {
            return toJson(exampleFromSchema(schema, ""));
        }
        return "";
    }

    private Map<String, String> openApiQueryParams(Map<String, Object> operation) {
        var values = new LinkedHashMap<String, String>();
        for (var parameterItem : list(operation.get("parameters"))) {
            var parameter = map(parameterItem);
            if (!"query".equals(text(parameter.get("in"), ""))) {
                continue;
            }
            var name = text(parameter.get("name"), "");
            if (!name.isBlank()) {
                values.put(name, openApiParameterValue(parameter, name));
            }
        }
        return Map.copyOf(values);
    }

    private Map<String, Object> withPathItemParameters(Map<String, Object> pathItem,
                                                        Map<String, Object> operation) {
        var pathParameters = list(pathItem.get("parameters"));
        if (pathParameters.isEmpty()) {
            return operation;
        }
        var mergedParameters = new ArrayList<Object>(pathParameters);
        mergedParameters.addAll(list(operation.get("parameters")));
        var mergedOperation = new LinkedHashMap<>(operation);
        mergedOperation.put("parameters", List.copyOf(mergedParameters));
        return mergedOperation;
    }

    private String openApiPath(String path) {
        return OPENAPI_PATH_PARAMETER.matcher(path).replaceAll("#{$1}");
    }

    private String openApiParameterValue(Map<String, Object> parameter, String name) {
        if (parameter.containsKey("example")) {
            return scalar(parameter.get("example"));
        }
        var examples = map(parameter.get("examples"));
        if (!examples.isEmpty()) {
            return scalar(map(examples.values().iterator().next()).get("value"));
        }
        return scalar(exampleFromSchema(map(parameter.get("schema")), name));
    }

    private Map<String, String> formParamsFromOpenApiMedia(Map<String, Object> media) {
        var values = new LinkedHashMap<String, String>();
        map(map(media.get("schema")).get("properties")).forEach((name, property) ->
                values.put(name, scalar(exampleFromSchema(map(property), name))));
        return Map.copyOf(values);
    }

    private List<MultipartPartPlan> multipartPartsFromOpenApiMedia(Map<String, Object> media) {
        var parts = new ArrayList<MultipartPartPlan>();
        map(map(media.get("schema")).get("properties")).forEach((name, property) -> {
            var schema = map(property);
            if ("binary".equals(text(schema.get("format"), ""))) {
                parts.add(new MultipartPartPlan(name, "", "#{%s}".formatted(name),
                        "application/octet-stream", ""));
            } else {
                parts.add(new MultipartPartPlan(name, scalar(exampleFromSchema(schema, name)), "", "", ""));
            }
        });
        return List.copyOf(parts);
    }

    private AuthPlan openApiAuth(Map<String, Object> operation,
                                 Map<String, Object> root,
                                 Map<String, Object> securitySchemes) {
        var security = list(operation.get("security"));
        if (security.isEmpty()) {
            security = list(root.get("security"));
        }
        if (security.isEmpty()) {
            return AuthPlan.none();
        }
        for (var requirement : security) {
            for (var schemeName : map(requirement).keySet()) {
                var scheme = map(securitySchemes.get(schemeName));
                var type = text(scheme.get("type"), "");
                var httpScheme = text(scheme.get("scheme"), "");
                if ("http".equals(type) && "bearer".equals(httpScheme)) {
                    return new AuthPlan("bearer", "", "", "#{token}", "Authorization");
                }
                if ("http".equals(type) && "basic".equals(httpScheme)) {
                    return new AuthPlan("basic", "#{username}", "#{password}", "", "Authorization");
                }
                if ("apiKey".equals(type)) {
                    return new AuthPlan("apiKey", "", "", "#{apiKey}",
                            text(scheme.get("name"), "X-API-Key"));
                }
            }
        }
        return AuthPlan.none();
    }

    private Map<String, Object> swaggerBodyParameter(Map<String, Object> operation) {
        for (var parameter : list(operation.get("parameters"))) {
            var value = map(parameter);
            if ("body".equals(text(value.get("in"), ""))) {
                return value;
            }
        }
        return Map.of();
    }

    private String swaggerContentType(Map<String, Object> operation, Map<String, Object> root) {
        var operationConsumes = list(operation.get("consumes"));
        if (!operationConsumes.isEmpty()) {
            return text(operationConsumes.getFirst(), "application/json");
        }
        var rootConsumes = list(root.get("consumes"));
        if (!rootConsumes.isEmpty()) {
            return text(rootConsumes.getFirst(), "application/json");
        }
        return "application/json";
    }

    private Object exampleFromSchema(Map<String, Object> schema, String propertyName) {
        if (schema.containsKey("example")) {
            return schema.get("example");
        }
        var type = text(schema.get("type"), "");
        var properties = map(schema.get("properties"));
        if ("object".equals(type) || !properties.isEmpty()) {
            var values = new LinkedHashMap<String, Object>();
            properties.forEach((name, property) -> values.put(name, exampleFromSchema(map(property), name)));
            return values;
        }
        if ("array".equals(type)) {
            return List.of(exampleFromSchema(map(schema.get("items")), propertyName));
        }
        if ("integer".equals(type)) {
            return 1;
        }
        if ("number".equals(type)) {
            return 1.0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        return propertyName.isBlank() ? "value" : "#{%s}".formatted(propertyName);
    }

    private String openApiStatus(Map<String, Object> operation) {
        var responses = map(operation.get("responses"));
        for (var key : responses.keySet()) {
            if (key.matches("2\\d\\d")) {
                return key;
            }
        }
        for (var key : responses.keySet()) {
            if (key.matches("\\d{3}")) {
                return key;
            }
        }
        return "200";
    }

    private Optional<Map.Entry<String, Object>> firstMedia(Map<String, Object> content) {
        if (content.containsKey("application/json")) {
            return Optional.of(Map.entry("application/json", content.get("application/json")));
        }
        return content.entrySet().stream().findFirst();
    }

    private Optional<String> firstAbsoluteServerUrl(Map<String, Object> root) {
        return list(root.get("servers")).stream()
                .map(HttpImportService::map)
                .map(server -> text(server.get("url"), ""))
                .filter(value -> value.startsWith("http://") || value.startsWith("https://"))
                .findFirst();
    }

    private Optional<String> swaggerBaseUrl(Map<String, Object> root) {
        if (!root.containsKey("swagger")) {
            return Optional.empty();
        }
        var host = text(root.get("host"), "");
        if (host.isBlank()) {
            return Optional.empty();
        }
        var schemes = list(root.get("schemes"));
        var scheme = schemes.isEmpty() ? "https" : text(schemes.getFirst(), "https");
        var basePath = text(root.get("basePath"), "");
        if (!basePath.isBlank() && !basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        return Optional.of("%s://%s%s".formatted(scheme, host, basePath));
    }

    private String openApiVersion(Map<String, Object> root) {
        return text(root.get("openapi"), text(root.get("swagger"), "unknown"));
    }

    private void warnUnsupportedOpenApiRoot(Map<String, Object> root, List<ImportWarning> warnings) {
        if (root.containsKey("webhooks")) {
            warnings.add(new ImportWarning("warning", "import.unsupported.openapi.feature", "$.webhooks",
                    "OpenAPI webhooks are detected but v1 import only builds client-side HTTP request flows."));
        }
    }

    private void warnUnsupportedOpenApiOperation(String path,
                                                 String method,
                                                 Map<String, Object> operation,
                                                 List<ImportWarning> warnings) {
        var operationPath = "$.paths['%s'].%s".formatted(path, method);
        if (operation.containsKey("callbacks")) {
            warnings.add(new ImportWarning("warning", "import.unsupported.openapi.feature",
                    operationPath + ".callbacks",
                    "OpenAPI callbacks are detected but v1 import does not generate callback server flows."));
        }
        var responses = map(operation.get("responses"));
        for (var responseEntry : responses.entrySet()) {
            if (map(responseEntry.getValue()).containsKey("links")) {
                warnings.add(new ImportWarning("warning", "import.unsupported.openapi.feature",
                        operationPath + ".responses." + responseEntry.getKey() + ".links",
                        "OpenAPI response links are detected but v1 import only records correlation candidates."));
            }
        }
    }

    private List<String> openApiTags(Map<String, Object> operation) {
        return list(operation.get("tags")).stream()
                .map(value -> text(value, ""))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> openApiAuthSchemes(Map<String, Object> root,
                                            Map<String, Object> resolvedSecuritySchemes) {
        var schemes = new LinkedHashSet<String>();
        schemes.addAll(resolvedSecuritySchemes.keySet());
        var swaggerSchemes = map(root.get("securityDefinitions"));
        schemes.addAll(swaggerSchemes.keySet());
        return List.copyOf(schemes);
    }

    private List<Object> openApiResponseExamples(Map<String, Object> root,
                                                  OpenApiReferenceResolver referenceResolver) {
        var examples = new ArrayList<Object>();
        for (var pathEntry : map(root.get("paths")).entrySet()) {
            var pathPointer = "$.paths['%s']".formatted(pathEntry.getKey());
            var pathItem = referenceResolver.resolveMap(pathEntry.getValue(), pathPointer);
            for (var methodEntry : pathItem.entrySet()) {
                var method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                var operation = referenceResolver.resolveMap(methodEntry.getValue(), pathPointer + "." + method);
                var responses = map(operation.get("responses"));
                for (var response : responses.values()) {
                    collectOpenApiResponseExamples(map(response), examples);
                }
            }
        }
        return examples;
    }

    private void collectOpenApiResponseExamples(Map<String, Object> response, List<Object> examples) {
        for (var media : map(response.get("content")).values()) {
            var mediaMap = map(media);
            if (mediaMap.containsKey("example")) {
                examples.add(mediaMap.get("example"));
            }
            for (var example : map(mediaMap.get("examples")).values()) {
                var value = map(example).get("value");
                if (value != null) {
                    examples.add(value);
                }
            }
            var schema = map(mediaMap.get("schema"));
            if (!schema.isEmpty()) {
                examples.add(exampleFromSchema(schema, ""));
            }
        }
    }

    private List<Object> harResponseExamples(Map<String, Object> root) {
        var examples = new ArrayList<Object>();
        for (var entry : list(map(root.get("log")).get("entries"))) {
            var response = map(map(entry).get("response"));
            var text = text(map(response.get("content")).get("text"), "");
            if (!text.isBlank()) {
                examples.add(text);
            }
        }
        return examples;
    }

    private List<CookiePlan> cookiesFromHar(Object value) {
        var cookies = new ArrayList<CookiePlan>();
        for (var item : list(value)) {
            var cookie = map(item);
            var name = text(cookie.get("name"), "");
            if (!name.isBlank()) {
                cookies.add(new CookiePlan(
                        name,
                        text(cookie.get("value"), ""),
                        text(cookie.get("domain"), ""),
                        text(cookie.get("path"), "/")
                ));
            }
        }
        return List.copyOf(cookies);
    }

    private boolean isHarResource(Map<String, Object> entry) {
        var request = map(entry.get("request"));
        var response = map(entry.get("response"));
        var mimeType = text(map(response.get("content")).get("mimeType"), "").toLowerCase(Locale.ROOT);
        var path = pathWithQuery(text(request.get("url"), "")).orElse("");
        return mimeType.contains("javascript")
                || mimeType.contains("css")
                || mimeType.startsWith("image/")
                || path.matches(".*\\.(js|css|png|jpg|jpeg|gif|svg|ico)(\\?.*)?$");
    }

    private HttpRequestPlan withAdditionalResource(HttpRequestPlan request, HttpRequestPlan resourceRequest) {
        var resources = new ArrayList<>(request.resources());
        resources.add(new HttpResourcePlan(
                resourceRequest.name(),
                resourceRequest.method(),
                resourceRequest.path(),
                resourceRequest.headers()
        ));
        return new HttpRequestPlan(
                request.name(),
                request.method(),
                request.path(),
                request.headers(),
                request.queryParams(),
                request.formParams(),
                request.multipartParts(),
                request.body(),
                resources,
                request.auth(),
                request.cookies(),
                request.options(),
                request.checks()
        );
    }

    private List<Object> postmanResponseExamples(Map<String, Object> root) {
        var examples = new ArrayList<Object>();
        collectPostmanResponseExamples(list(root.get("item")), examples);
        return examples;
    }

    private void collectPostmanResponseExamples(Object items, List<Object> examples) {
        for (var item : list(items)) {
            var itemMap = map(item);
            if (itemMap.containsKey("item")) {
                collectPostmanResponseExamples(itemMap.get("item"), examples);
            }
            for (var response : list(itemMap.get("response"))) {
                var body = text(map(response).get("body"), "");
                if (!body.isBlank()) {
                    examples.add(body);
                }
            }
        }
    }

    private void addInferenceWarnings(List<ImportWarning> warnings,
                                      List<String> feederCandidates,
                                      List<String> correlationCandidates,
                                      List<String> correlationUsages,
                                      EndpointInventory endpointInventory,
                                      String rawImportText) {
        for (var candidate : feederCandidates) {
            warnings.add(new ImportWarning("info", "import.feeder.candidate", "$.requests",
                    "Request path or request-body representation placeholder '%s' can be backed by a Gatling feeder."
                            .formatted(candidate)));
        }
        for (var candidate : correlationCandidates) {
            warnings.add(new ImportWarning("info", "import.correlation.candidate", "$.responses",
                    "Response field '%s' looks like a value that may need a Gatling check/saveAs correlation."
                            .formatted(candidate)));
        }
        var authDetected = !endpointInventory.authSchemes().isEmpty()
                || (rawImportText != null && rawImportText.toLowerCase(Locale.ROOT).contains("authorization"));
        if (authDetected) {
            warnings.add(new ImportWarning("info", "import.auth.detected", "$",
                    "Authentication hints were detected; validate headers and token correlation before load runs."));
        }
        for (var usage : correlationUsages) {
            warnings.add(new ImportWarning("info", "import.correlation.usage", "$.requests",
                    "Placeholder '%s' is reused after being detected as a correlation candidate.".formatted(usage)));
        }
    }

    private String postmanCollectionVersion(Map<String, Object> root) {
        var schema = text(map(root.get("info")).get("schema"), "");
        var matcher = POSTMAN_SCHEMA_VERSION.matcher(schema);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }

    private String sourceTitle(Map<String, Object> root, String fallback) {
        var info = map(root.get("info"));
        return text(info.get("title"), text(info.get("name"), fallback));
    }

    private Map<String, String> headersFromList(Object value, List<ImportWarning> warnings, String path) {
        var headers = new LinkedHashMap<String, String>();
        for (var item : list(value)) {
            var header = map(item);
            putHeader(headers, text(header.get("name"), ""), text(header.get("value"), ""), warnings, path);
        }
        return Map.copyOf(headers);
    }

    private Map<String, String> headersFromPostman(Object value, List<ImportWarning> warnings) {
        var headers = new LinkedHashMap<String, String>();
        for (var item : list(value)) {
            var header = map(item);
            putHeader(headers, text(header.get("key"), text(header.get("name"), "")),
                    text(header.get("value"), ""), warnings, "$.item.request.header");
        }
        return Map.copyOf(headers);
    }

    private void putHeader(Map<String, String> headers,
                           String headerLine,
                           List<ImportWarning> warnings,
                           String path) {
        var separator = headerLine.indexOf(':');
        if (separator < 1) {
            return;
        }
        putHeader(headers, headerLine.substring(0, separator), headerLine.substring(separator + 1).trim(), warnings, path);
    }

    private void putHeader(Map<String, String> headers,
                           String name,
                           String value,
                           List<ImportWarning> warnings,
                           String path) {
        if (name == null || name.isBlank()) {
            return;
        }
        var normalized = name.trim();
        var lower = normalized.toLowerCase(Locale.ROOT);
        if ("host".equals(lower) || "content-length".equals(lower)) {
            return;
        }
        if (SENSITIVE_HEADERS.contains(lower)) {
            headers.put(normalized, redactHeader(value));
            warnings.add(new ImportWarning(
                    "warning",
                    "import.secret.header.redacted",
                    path,
                    "Sensitive header '%s' was redacted before building the Gatling plan.".formatted(normalized)
            ));
            return;
        }
        headers.put(normalized, value == null ? "" : value.trim());
    }

    private String redactHeader(String value) {
        var text = value == null ? "" : value.trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return "Bearer <redacted>";
        }
        if (text.toLowerCase(Locale.ROOT).startsWith("basic ")) {
            return "Basic <redacted>";
        }
        return "<redacted>";
    }

    private String postmanUrl(Object value) {
        if (value instanceof String text) {
            return text;
        }
        var url = map(value);
        var raw = text(url.get("raw"), "");
        if (!raw.isBlank()) {
            return raw;
        }
        var protocol = text(url.get("protocol"), "https");
        var host = join(url.get("host"), ".");
        var path = join(url.get("path"), "/");
        var query = postmanQuery(url.get("query"));
        return "%s://%s/%s%s".formatted(protocol, host, path, query);
    }

    private String postmanQuery(Object value) {
        var parts = new ArrayList<String>();
        for (var item : list(value)) {
            var query = map(item);
            var key = text(query.get("key"), "");
            if (!key.isBlank()) {
                parts.add("%s=%s".formatted(key, text(query.get("value"), "")));
            }
        }
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    private Optional<String> postmanStatus(Map<String, Object> item) {
        for (var event : list(item.get("event"))) {
            var eventMap = map(event);
            if (!"test".equals(text(eventMap.get("listen"), ""))) {
                continue;
            }
            var exec = map(eventMap.get("script")).get("exec");
            var script = exec instanceof List<?> lines
                    ? String.join("\n", lines.stream().map(String::valueOf).toList())
                    : text(exec, "");
            var matcher = POSTMAN_STATUS.matcher(script);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private CheckPlan statusCheck(String status) {
        return new CheckPlan("status", "", "is", status, "");
    }

    private Optional<String> baseUrl(String rawUrl) {
        try {
            var uri = URI.create(rawUrl);
            if (uri.getScheme() == null || uri.getRawAuthority() == null) {
                return Optional.empty();
            }
            return Optional.of("%s://%s".formatted(uri.getScheme(), uri.getRawAuthority()));
        } catch (IllegalArgumentException ignored) {
            var matcher = ABSOLUTE_HTTP_URL.matcher(rawUrl == null ? "" : rawUrl);
            if (matcher.matches()) {
                return Optional.of("%s://%s".formatted(matcher.group(1), matcher.group(2)));
            }
            return Optional.empty();
        }
    }

    private Optional<String> pathWithQuery(String rawUrl) {
        try {
            var uri = URI.create(rawUrl);
            var path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                path += "?" + uri.getRawQuery();
            }
            return Optional.of(path);
        } catch (IllegalArgumentException ignored) {
            var value = rawUrl == null ? "" : rawUrl;
            var matcher = ABSOLUTE_HTTP_URL.matcher(value);
            if (matcher.matches()) {
                var path = matcher.group(3);
                return Optional.of(path.isBlank() ? "/" : path);
            }
            return optional(value).map(text -> text.startsWith("/") ? text : "/");
        }
    }

    private String withQueryParts(String path, List<String> queryParts) {
        if (queryParts.isEmpty()) {
            return path;
        }
        var separator = path.contains("?") ? "&" : "?";
        return path + separator + String.join("&", queryParts);
    }

    private List<String> shellTokens(String command) {
        var normalized = command == null ? "" : command.replace("\\\n", " ");
        var tokens = new ArrayList<String>();
        var token = new StringBuilder();
        char quote = 0;
        var escaping = false;
        for (int i = 0; i < normalized.length(); i++) {
            var ch = normalized.charAt(i);
            if (escaping) {
                token.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    token.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(ch);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private boolean isDataOption(String token) {
        return "-d".equals(token)
                || "--data".equals(token)
                || "--data-raw".equals(token)
                || "--data-binary".equals(token)
                || "--data-ascii".equals(token)
                || "--data-urlencode".equals(token)
                || "--json".equals(token);
    }

    private String requireNext(List<String> tokens, int index, String option) {
        if (index >= tokens.size()) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return tokens.get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static List<?> list(Object value) {
        if (value instanceof List<?> raw) {
            return raw;
        }
        return List.of();
    }

    private static String join(Object value, String separator) {
        if (value instanceof List<?> raw) {
            return String.join(separator, raw.stream().map(String::valueOf).toList());
        }
        return text(value, "");
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        var text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exc) {
            throw new IllegalArgumentException("Cannot serialize imported example as JSON", exc);
        }
    }

    private String scalar(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return toJson(value);
        }
        return String.valueOf(value);
    }

    private String trimTrailingSlash(String value) {
        if (value.length() > 1 && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
