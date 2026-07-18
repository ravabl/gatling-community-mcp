#!/usr/bin/env python3
import argparse
import json
import os
import queue
import subprocess
import sys
import tempfile
import threading
import time


EXPECTED_TOOLS = {
    "gatling_interaction_status",
    "gatling_resolve_capabilities",
    "gatling_detect_project",
    "gatling_get_project_context",
    "gatling_explain_project_context",
    "gatling_resolve_effective_context",
    "gatling_generate_simulation",
    "gatling_analyze_simulation",
    "gatling_validate_feature_usage",
    "gatling_list_dsl_methods",
    "gatling_validate_method_chain",
    "gatling_explain_dsl_method",
    "gatling_find_replacement_method",
    "gatling_explain_version_constraints",
    "gatling_plan_simulation",
    "gatling_plan_http_flow",
    "gatling_plan_correlation",
    "gatling_plan_checks",
    "gatling_plan_injection",
    "gatling_plan_assertions",
    "gatling_validate_simulation_plan",
    "gatling_generate_from_plan",
    "gatling_explain_generation_decisions",
    "gatling_generate_patch",
    "gatling_validate_generated_code",
    "gatling_compile_check",
    "gatling_import_openapi",
    "gatling_import_har",
    "gatling_import_curl",
    "gatling_import_postman_collection",
    "gatling_extract_endpoints_from_codebase",
    "gatling_analyze_report",
    "gatling_analyze_log",
    "gatling_explain_errors",
    "gatling_suggest_load_model",
    "gatling_check_feeder_risk",
    "gatling_check_correlation_risk",
    "gatling_check_assertion_quality",
}


class McpSmokeError(RuntimeError):
    pass


class McpProcess:
    def __init__(self, command, timeout):
        self.command = command
        self.timeout = timeout
        self.responses = queue.Queue()
        self.stderr = []
        self.stdout_lines = []
        self.behavioral_tools = set()
        self.next_id = 1
        self.process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        threading.Thread(target=self._read_stdout, daemon=True).start()
        threading.Thread(target=self._read_stderr, daemon=True).start()

    def _read_stdout(self):
        assert self.process.stdout is not None
        for line in self.process.stdout:
            line = line.strip()
            if not line:
                continue
            try:
                self.stdout_lines.append(line)
                self.responses.put(json.loads(line))
            except json.JSONDecodeError as exc:
                self.stdout_lines.append(line)
                self.responses.put({"_invalid_json": line, "_error": str(exc)})

    def _read_stderr(self):
        assert self.process.stderr is not None
        for line in self.process.stderr:
            self.stderr.append(line.rstrip())

    def request(self, method, params=None):
        request_id = self.next_id
        self.next_id += 1
        message = {"jsonrpc": "2.0", "id": request_id, "method": method}
        if params is not None:
            message["params"] = params
        self._send(message)
        return self._read_response(request_id)

    def notify(self, method, params=None):
        message = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        self._send(message)

    def _send(self, message):
        if self.process.poll() is not None:
            raise McpSmokeError(f"MCP process exited early with code {self.process.returncode}")
        assert self.process.stdin is not None
        self.process.stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
        self.process.stdin.flush()

    def _read_response(self, request_id):
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            try:
                response = self.responses.get(timeout=0.2)
            except queue.Empty:
                if self.process.poll() is not None:
                    raise McpSmokeError(f"MCP process exited while waiting for response id={request_id}")
                continue
            if "_invalid_json" in response:
                raise McpSmokeError(f"Invalid JSON on stdout: {response['_invalid_json']}")
            if response.get("id") != request_id:
                continue
            if "error" in response:
                raise McpSmokeError(f"MCP error for {request_id}: {response['error']}")
            return response["result"]
        raise McpSmokeError(f"Timed out waiting for response id={request_id}")

    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)


def require(condition, message):
    if not condition:
        raise McpSmokeError(message)


def text_content(call_result):
    return "\n".join(
        item.get("text", "")
        for item in call_result.get("content", [])
        if item.get("type") == "text" or "text" in item
    )


def call_tool(client, name, arguments):
    client.behavioral_tools.add(name)
    return client.request("tools/call", {"name": name, "arguments": arguments})


def run_smoke(command, project_path, timeout):
    client = McpProcess(command, timeout)
    try:
        initialized = client.request("initialize", {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "clientInfo": {"name": "gatling-community-mcp-smoke", "version": "0.2.0"},
        })
        require(initialized["serverInfo"]["name"] == "gatling-community-mcp", "unexpected server name")
        require("completions" in initialized["capabilities"], "server did not declare completions capability")
        client.notify("notifications/initialized")

        tools = client.request("tools/list")["tools"]
        tool_names = {tool["name"] for tool in tools}
        require(EXPECTED_TOOLS.issubset(tool_names), f"missing tools: {EXPECTED_TOOLS - tool_names}")
        for tool in tools:
            require(tool.get("title"), f"tool {tool['name']} is missing title")
            require("Gatling" in tool.get("description", ""), f"tool {tool['name']} has weak description")
            require(tool.get("inputSchema", {}).get("additionalProperties") is False,
                    f"tool {tool['name']} is not strict on input")
            require(tool.get("outputSchema", {}).get("type") == "object",
                    f"tool {tool['name']} is missing output schema")
            require(tool.get("outputSchema", {}).get("additionalProperties") is False,
                    f"tool {tool['name']} is not strict on output")
            require("anyOf" in tool.get("outputSchema", {}),
                    f"tool {tool['name']} output schema has no success/error contract")
            require("required\": []" not in json.dumps(tool.get("outputSchema", {}), sort_keys=True),
                    f"tool {tool['name']} output schema still allows an empty object")
            require("[string, number, boolean, object, array, integer]" not in json.dumps(tool.get("outputSchema", {})),
                    f"tool {tool['name']} still uses generic union output schema")
            examples = tool.get("_meta", {}).get("examples") or tool.get("meta", {}).get("examples")
            require(examples, f"tool {tool['name']} is missing examples metadata")
            require("arguments" in json.dumps(examples), f"tool {tool['name']} examples do not include arguments")
            annotations = tool.get("annotations", {})
            if tool["name"] == "gatling_compile_check":
                require(annotations.get("readOnlyHint") is False,
                        "gatling_compile_check must not be annotated as read-only")
                require(annotations.get("idempotentHint") is True,
                        "gatling_compile_check must be idempotent")
            else:
                require(annotations.get("readOnlyHint") is True, f"tool {tool['name']} is not read-only annotated")
            require(annotations.get("destructiveHint") is False, f"tool {tool['name']} is not non-destructive annotated")

        resources = client.request("resources/list")["resources"]
        resource_uris = {resource["uri"] for resource in resources}
        require("gatling://community-plugins/kafka" in resource_uris, "kafka resource is missing")
        require("gatling://authoring/http/golden-flows" in resource_uris, "HTTP golden-flow resource is missing")
        require("gatling://analysis/reports" in resource_uris, "report analysis resource is missing")
        require("gatling://mcp/interactions" in resource_uris, "MCP interaction resource is missing")
        require("gatling://compatibility/protocol-matrix" in resource_uris,
                "protocol compatibility matrix is missing")
        capability_only_protocols = ("WEBSOCKET", "SSE", "JMS", "MQTT", "GRPC")
        for protocol in capability_only_protocols:
            capability_notes_uri = f"gatling://examples/java/{protocol.lower()}/capability-notes"
            require(capability_notes_uri in resource_uris,
                    f"{protocol} capability notes resource is missing")

        resource_templates = client.request("resources/templates/list")["resourceTemplates"]
        resource_template_uris = {template["uriTemplate"] for template in resource_templates}
        require("gatling://methods/http/{language}/{version}" in resource_template_uris,
                "HTTP method resource template is missing")
        require("gatling://versions/{version}/features" in resource_template_uris,
                "version features resource template is missing")
        require("gatling://examples/{language}/{protocol}/{pattern}" in resource_template_uris,
                "example resource template is missing")

        prompts = client.request("prompts/list")["prompts"]
        prompt_names = {prompt["name"] for prompt in prompts}
        require("create_simulation" in prompt_names, "create_simulation prompt is missing")
        require("analyze_report" in prompt_names, "analyze_report prompt is missing")
        create_prompt_definition = next(prompt for prompt in prompts if prompt["name"] == "create_simulation")
        create_prompt_args = {argument["name"] for argument in create_prompt_definition.get("arguments", [])}
        require({"gatlingVersion", "language", "buildTool", "protocol", "goal"}.issubset(create_prompt_args),
                "create_simulation prompt arguments are incomplete")

        kafka_resource = client.request("resources/read", {"uri": "gatling://community-plugins/kafka"})
        kafka_text = kafka_resource["contents"][0]["text"]
        require("strict-verified" in kafka_text, "kafka resource verification policy is wrong")
        require("1.0.6" in kafka_text, "kafka resource plugin version is wrong")
        require("Gatling 3.13.5" in kafka_text, "kafka resource Gatling compatibility is wrong")
        require("releases/tag/v1.0.6" in kafka_text, "kafka resource source link is wrong")
        golden_flow_resource = client.request("resources/read", {"uri": "gatling://authoring/http/golden-flows"})
        require("gatling_plan_simulation" in golden_flow_resource["contents"][0]["text"], "golden flow text is wrong")
        example_resource = client.request("resources/read", {"uri": "gatling://examples/java/http/login-token-orders"})
        require("jsonPath(\"$.token\").saveAs(\"jwtToken\")" in example_resource["contents"][0]["text"],
                "example resource missed JWT correlation")
        require("gatling_generate_from_plan" in example_resource["contents"][0]["text"],
                "example resource missed tool workflow")
        analysis_resource = client.request("resources/read", {"uri": "gatling://analysis/reports"})
        require("gatling_analyze_report" in analysis_resource["contents"][0]["text"], "analysis resource text is wrong")
        interaction_resource = client.request("resources/read", {"uri": "gatling://mcp/interactions"})
        require("gatling_interaction_status" in interaction_resource["contents"][0]["text"],
                "interaction resource text is wrong")
        protocol_matrix_resource = client.request("resources/read", {"uri": "gatling://compatibility/protocol-matrix"})
        protocol_matrix_text = protocol_matrix_resource["contents"][0]["text"]
        require("HTTP" in protocol_matrix_text and "generationMode=deep" in protocol_matrix_text,
                "protocol matrix does not distinguish HTTP deep generation")
        for protocol in capability_only_protocols:
            require(protocol in protocol_matrix_text and "generationMode=capability-only" in protocol_matrix_text,
                    f"protocol matrix does not describe {protocol} as capability-only")
            capability_notes_uri = f"gatling://examples/java/{protocol.lower()}/capability-notes"
            capability_notes = client.request("resources/read", {"uri": capability_notes_uri})["contents"][0]["text"]
            require(protocol in capability_notes, f"{protocol} capability notes miss protocol identity")
            require("capability metadata" in capability_notes and "context" in capability_notes
                    and "version" in capability_notes and "dependency" in capability_notes,
                    f"{protocol} capability notes miss safe context guidance")
            require("Full code generation is disabled" in capability_notes,
                    f"{protocol} capability notes promise deep generation")
            require("extends Simulation" not in capability_notes and "class " not in capability_notes,
                    f"{protocol} capability notes contain fabricated deep code")

        create_prompt = client.request("prompts/get", {"name": "create_simulation", "arguments": {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "goal": "login and call orders",
        }})
        prompt_text = create_prompt["messages"][0]["content"]["text"]
        require("Generate a Gatling Community simulation" in prompt_text, "create_simulation prompt text is wrong")
        require("gatling_plan_simulation" in prompt_text, "create_simulation prompt does not guide planning")
        require("3.9.5" in prompt_text and "JAVA" in prompt_text, "create_simulation prompt ignored arguments")

        language_completion = client.request("completion/complete", {
            "ref": {"type": "ref/prompt", "name": "create_simulation"},
            "argument": {"name": "language", "value": "JA"},
        })
        require("JAVA" in language_completion["completion"]["values"], "language completion missed JAVA")
        require("JAVASCRIPT" in language_completion["completion"]["values"], "language completion missed JAVASCRIPT")

        version_completion = client.request("completion/complete", {
            "ref": {"type": "ref/resource", "uri": "gatling://methods/http/{language}/{version}"},
            "argument": {"name": "version", "value": "3.9"},
        })
        require("3.9" in version_completion["completion"]["values"], "version completion missed 3.9")

        pattern_completion = client.request("completion/complete", {
            "ref": {"type": "ref/resource", "uri": "gatling://examples/{language}/{protocol}/{pattern}"},
            "argument": {"name": "pattern", "value": "login"},
        })
        require("login-token-orders" in pattern_completion["completion"]["values"],
                "example pattern completion missed login-token-orders")

        interaction_status = call_tool(client, "gatling_interaction_status", {})
        require(interaction_status["structuredContent"]["elicitation"] is False,
                "default smoke client unexpectedly reports elicitation")
        require(interaction_status["structuredContent"]["sampling"] is False,
                "default smoke client unexpectedly reports sampling")

        http_caps = call_tool(client, "gatling_resolve_capabilities", {
            "gatlingVersion": "3.15",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
        })
        require(http_caps["structuredContent"]["supported"] is True, "HTTP capability is not supported")
        require(http_caps["structuredContent"]["generationMode"] == "deep", "HTTP capability mode is not deep")

        for protocol in capability_only_protocols:
            protocol_caps = call_tool(client, "gatling_resolve_capabilities", {
                "gatlingVersion": "3.15",
                "language": "JAVA",
                "buildTool": "MAVEN",
                "protocol": protocol,
            })
            require(protocol_caps["structuredContent"]["supported"] is True,
                    f"{protocol} capability is not supported")
            require(protocol_caps["structuredContent"]["generationMode"] == "capability-only",
                    f"{protocol} must be capability-only")
            require(protocol.lower() in protocol_caps["structuredContent"]["features"],
                    f"{protocol} capability features miss protocol identity")
            require("protocol.generation.disabled.v1" in json.dumps(protocol_caps["structuredContent"]["warnings"]),
                    f"{protocol} capability warning is missing")

        detected = call_tool(client, "gatling_detect_project", {"path": project_path})
        require(detected["structuredContent"]["buildTool"] == "MAVEN", "project detection did not find Maven")

        project_context = call_tool(client, "gatling_get_project_context", {"path": project_path})
        require(project_context["structuredContent"]["buildTool"] == "MAVEN",
                "project context did not find Maven")
        require("sourceRoots" in project_context["structuredContent"],
                "project context did not return source roots")
        require("dependencyState" in project_context["structuredContent"],
                "project context did not return dependency state")

        explained_context = call_tool(client, "gatling_explain_project_context", {"path": project_path})
        require("buildTool=MAVEN" in text_content(explained_context),
                "project context explanation missed build tool")

        codebase_endpoints = call_tool(client, "gatling_extract_endpoints_from_codebase", {
            "path": project_path,
            "languageHint": "JAVA",
            "maxFiles": 2000,
        })
        endpoint_inventory = json.dumps(codebase_endpoints["structuredContent"].get("endpointInventory", {}))
        require("POST /login" in endpoint_inventory,
                "codebase endpoint extraction missed POST /login")
        require("GET /orders/#{userId}" in endpoint_inventory,
                "codebase endpoint extraction missed GET /orders/#{userId}")

        effective_context = call_tool(client, "gatling_resolve_effective_context", {
            "path": project_path,
            "protocol": "HTTP",
        })
        require(effective_context["structuredContent"]["buildTool"] == "MAVEN",
                "effective context did not keep Maven")
        require(effective_context["structuredContent"]["protocol"] == "HTTP",
                "effective context did not resolve protocol")

        with tempfile.TemporaryDirectory(prefix="gatling-mcp-outside-") as outside:
            with open(os.path.join(outside, "pom.xml"), "w", encoding="utf-8") as pom:
                pom.write("<project><properties><gatling.version>3.15.1</gatling.version></properties></project>")
            outside_result = call_tool(client, "gatling_detect_project", {"path": outside})
            require(outside_result.get("isError") is True, "outside workspace path was not rejected")
            require(outside_result["structuredContent"]["code"] == "path.outside_workspace",
                    "outside workspace path returned wrong error code")

        java_sim = call_tool(client, "gatling_generate_simulation", {
            "gatlingVersion": "3.15",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "simulationClassName": "ApiSimulation",
        })
        require("class ApiSimulation extends Simulation" in text_content(java_sim), "Java HTTP simulation was not generated")
        for protocol in capability_only_protocols:
            generated = call_tool(client, "gatling_generate_simulation", {
                "gatlingVersion": "3.15",
                "language": "JAVA",
                "buildTool": "MAVEN",
                "protocol": protocol,
                "simulationClassName": "CapabilityOnlySimulation",
            })
            require(text_content(generated) == "", f"{protocol} returned fabricated deep code")
            require(len(generated["structuredContent"]["warnings"]) > 0,
                    f"{protocol} generated result missed capability warning")

        dsl_methods = call_tool(client, "gatling_list_dsl_methods", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "protocol": "HTTP",
            "category": "CHECK",
        })
        require(dsl_methods["structuredContent"]["matchedGatlingLine"] == "3.9", "patch version did not match 3.9")
        require(dsl_methods["structuredContent"]["count"] >= 10, "method catalog returned too few checks")

        chain = call_tool(client, "gatling_validate_method_chain", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "methods": ["scenario", "exec", "get", "check", "status.is"],
        })
        require(chain["structuredContent"]["valid"] is False,
                "method chain validator accepted get without http")
        require("method_chain.missing_predecessor" in json.dumps(chain["structuredContent"]["findings"]),
                "method chain validator missed predecessor finding")

        incompatible_chain = call_tool(client, "gatling_validate_method_chain", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "methods": ["Simulation", "http", "post", "body.StringBody", "formParam"],
        })
        require(incompatible_chain["structuredContent"]["valid"] is False,
                "method chain validator accepted incompatible request body shape")
        require("method_chain.incompatible_method" in json.dumps(incompatible_chain["structuredContent"]["findings"]),
                "method chain validator missed incompatible method finding")

        method_explanation = call_tool(client, "gatling_explain_dsl_method", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "method": "jsonPath.saveAs",
        })
        require(method_explanation["structuredContent"]["found"] is True,
                "DSL method explanation did not find jsonPath.saveAs")
        require("CORRELATION" in json.dumps(method_explanation["structuredContent"]["method"]),
                "DSL method explanation missed category")

        replacement = call_tool(client, "gatling_find_replacement_method", {
            "gatlingVersion": "3.11.2",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "method": "heavisideUsers",
        })
        require(replacement["structuredContent"]["found"] is True,
                "replacement method lookup did not find heavisideUsers replacement")
        require("stressPeakUsers" in json.dumps(replacement["structuredContent"]["replacements"]),
                "replacement method lookup missed stressPeakUsers")

        compile_plan = call_tool(client, "gatling_compile_check", {
            "path": project_path,
            "buildTool": "MAVEN",
            "execute": False,
        })
        require(compile_plan["structuredContent"]["executed"] is False,
                "compile check planning should not execute in smoke")
        require(compile_plan["structuredContent"]["success"] is True,
                "compile check planning should be successful")
        require("test-compile" in json.dumps(compile_plan["structuredContent"]["command"]),
                "compile check did not select Maven test-compile")

        planned = call_tool(client, "gatling_plan_simulation", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "simulationClassName": "OrdersSimulation",
            "goal": "login, extract JWT token, call /api/orders with Authorization header, assert 200 and non-empty orders",
            "baseUrl": "https://api.example.test",
        })
        require(planned["structuredContent"]["valid"] is True, "planned HTTP simulation is invalid")
        require("jwtToken" in json.dumps(planned["structuredContent"]["plan"]), "planned flow missed JWT correlation")

        version_constraints = call_tool(client, "gatling_explain_version_constraints", {
            "gatlingVersion": "3.11.2",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
        })
        require(version_constraints["structuredContent"]["matchedGatlingLine"] == "3.11",
                "version constraints did not resolve the 3.11 line")
        require(version_constraints["structuredContent"]["dslMethodCount"] > 0,
                "version constraints did not return DSL methods")

        http_flow = call_tool(client, "gatling_plan_http_flow", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "simulationClassName": "AliasFlowSimulation",
            "goal": "login, extract JWT token, call /api/orders, assert 200",
            "baseUrl": "https://api.example.test",
        })
        require(http_flow["structuredContent"]["valid"] is True, "HTTP flow alias produced an invalid plan")
        require(http_flow["structuredContent"]["plan"]["simulationClassName"] == "AliasFlowSimulation",
                "HTTP flow alias ignored the simulation class")

        correlations = call_tool(client, "gatling_plan_correlation", {
            "goal": "extract $.token from login response and reuse it as Authorization header",
        })
        require(len(correlations["structuredContent"]["correlations"]) > 0,
                "correlation planner returned no correlation guidance")
        checks = call_tool(client, "gatling_plan_checks", {
            "goal": "login must return 200 and the orders response must contain a non-empty array",
        })
        require(len(checks["structuredContent"]["checks"]) > 0, "checks planner returned no checks")
        injection = call_tool(client, "gatling_plan_injection", {})
        require(injection["structuredContent"]["injectionProfile"]["type"] == "rampAndConstant",
                "injection planner returned the wrong model")
        assertions = call_tool(client, "gatling_plan_assertions", {})
        require(len(assertions["structuredContent"]["assertions"]) > 0,
                "assertion planner returned no assertions")

        validated_plan = call_tool(client, "gatling_validate_simulation_plan", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "plan": planned["structuredContent"]["plan"],
        })
        require(validated_plan["structuredContent"]["valid"] is True,
                "planned simulation did not validate through the public validator")
        decisions = call_tool(client, "gatling_explain_generation_decisions", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "plan": planned["structuredContent"]["plan"],
        })
        require(decisions["structuredContent"]["decisionCount"] > 0,
                "generation decision explainer returned no decisions")
        patch = call_tool(client, "gatling_generate_patch", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "plan": planned["structuredContent"]["plan"],
            "targetPath": "src/test/java/OrdersSimulation.java",
        })
        require(patch["structuredContent"]["writesFiles"] is False,
                "patch generator must not write files")
        require("OrdersSimulation" in patch["structuredContent"]["unifiedDiff"],
                "patch generator missed the simulation class")

        import_target = {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
        }
        imported_openapi = call_tool(client, "gatling_import_openapi", {
            **import_target,
            "document": """{"openapi":"3.0.0","info":{"title":"Local API","version":"1"},"paths":{"/health":{"get":{"responses":{"200":{"description":"ok"}}}}}}""",
        })
        require(imported_openapi["structuredContent"]["sourceType"] == "OPENAPI",
                "OpenAPI import returned the wrong source type")
        require(imported_openapi["structuredContent"]["requestCount"] == 1,
                "OpenAPI import did not return one request")
        imported_har = call_tool(client, "gatling_import_har", {
            **import_target,
            "document": """{"log":{"version":"1.2","entries":[{"request":{"method":"GET","url":"https://api.example.test/health","headers":[]}}]}}""",
        })
        require(imported_har["structuredContent"]["sourceType"] == "HAR",
                "HAR import returned the wrong source type")
        require(imported_har["structuredContent"]["requestCount"] == 1,
                "HAR import did not return one request")
        imported_postman = call_tool(client, "gatling_import_postman_collection", {
            **import_target,
            "document": """{"info":{"name":"Local API","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},"item":[{"name":"health","request":{"method":"GET","url":"https://api.example.test/health"}}]}""",
        })
        require(imported_postman["structuredContent"]["sourceType"] == "POSTMAN",
                "Postman import returned the wrong source type")
        require(imported_postman["structuredContent"]["requestCount"] == 1,
                "Postman import did not return one request")

        generated_from_plan = call_tool(client, "gatling_generate_from_plan", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "plan": planned["structuredContent"]["plan"],
        })
        require("jsonPath(\"$.token\").saveAs(\"jwtToken\")" in text_content(generated_from_plan),
                "generated plan code missed token correlation")

        generated_validation = call_tool(client, "gatling_validate_generated_code", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "code": text_content(generated_from_plan),
        })
        require(generated_validation["structuredContent"]["valid"] is True,
                "generated code did not pass static validation")
        require(generated_validation["structuredContent"]["compileReadiness"]["status"] == "ready",
                "generated code was not marked compile-ready")
        require("test-compile" in json.dumps(generated_validation["structuredContent"]["recommendedCompileCommand"]),
                "generated code validation did not recommend Maven test-compile")

        unsafe_validation = call_tool(client, "gatling_validate_generated_code", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "code": "class Broken extends Simulation { { setUp(scenario(\"s\").doIf(session -> true).then(exec(http(\"x\").get(\"/\").check(status().is(200))))); } }",
        })
        require(unsafe_validation["structuredContent"]["valid"] is False,
                "unsafe generated code should not validate")
        require("generated_code.unsafe_condition_always_true" in json.dumps(unsafe_validation["structuredContent"]["findings"]),
                "unsafe generated code validation missed always-true doIf")

        imported_curl = call_tool(client, "gatling_import_curl", {
            "gatlingVersion": "3.9.5",
            "language": "JAVA",
            "buildTool": "MAVEN",
            "protocol": "HTTP",
            "curl": "curl -X POST 'https://api.example.test/login' -H 'Content-Type: application/json' --data-raw '{\"username\":\"#{username}\",\"password\":\"#{password}\"}'",
            "simulationClassName": "ImportedCurlSimulation",
        })
        require(imported_curl["structuredContent"]["sourceType"] == "CURL", "curl import source type is wrong")
        require(imported_curl["structuredContent"]["sourceVersion"] == "command", "curl import source version is wrong")
        require(imported_curl["structuredContent"]["requestCount"] == 1, "curl import request count is wrong")
        require(imported_curl["structuredContent"]["valid"] is True, "curl import produced invalid plan")
        require("#{username}" in json.dumps(imported_curl["structuredContent"]["plan"]),
                "curl import missed request body")

        kafka_sim = call_tool(client, "gatling_generate_simulation", {
            "gatlingVersion": "3.13.5",
            "language": "SCALA",
            "buildTool": "SBT",
            "protocol": "KAFKA",
            "plugin": "KAFKA",
            "pluginVersion": "1.0.6",
            "javaVersion": "17",
            "simulationClassName": "KafkaSimulation",
        })
        require("org.galaxio.gatling.kafka.Predef._" in text_content(kafka_sim), "Kafka plugin simulation was not generated")
        require(kafka_sim["structuredContent"]["plugin"]["version"] == "1.0.6",
                "Kafka plugin result missed the exact verified plugin version")
        require(kafka_sim["structuredContent"]["plugin"]["verifiedAgainstGatlingVersion"] == "3.13.5",
                "Kafka plugin result missed the exact verified Gatling version")

        validation = call_tool(client, "gatling_validate_feature_usage", {
            "gatlingVersion": "3.11",
            "language": "SCALA",
            "buildTool": "SBT",
            "protocol": "HTTP",
            "code": "Thread.sleep(1000); http(\"x\").get(\"/${id}\")",
        })
        require(len(validation["structuredContent"]["findings"]) >= 2,
                "validation did not report expected findings")

        analysis = call_tool(client, "gatling_analyze_simulation", {
            "gatlingVersion": "3.11",
            "language": "SCALA",
            "buildTool": "SBT",
            "protocol": "HTTP",
            "code": "scenario(\"API\").exec(http(\"GET session\").get(\"/session\").check(status.is(200)))",
        })
        require("GET session" in analysis["structuredContent"]["requestNames"], "analysis did not extract request")

        analyzed_report = call_tool(client, "gatling_analyze_report", {
            "reportContent": """
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
                  "contents": {
                    "req_orders": {
                      "type": "REQUEST",
                      "name": "GET /api/orders",
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
                      }
                    }
                  }
                };
            """,
            "errorRateThreshold": 1.0,
            "p95ThresholdMs": 1000,
        })
        require(analyzed_report["structuredContent"]["sourceType"] == "GATLING_STATS_JS",
                "report analyzer source type is wrong")
        require(analyzed_report["structuredContent"]["requestCount"] == 1,
                "report analyzer request count is wrong")
        require("report.p95.high" in json.dumps(analyzed_report["structuredContent"]["findings"]),
                "report analyzer missed p95 finding")

        analyzed_log = call_tool(client, "gatling_analyze_log", {
            "logText": "REQUEST\\t\\tGET /api/orders\\t1000\\t1700\\tKO\\tstatus 500",
            "logType": "SIMULATION_LOG",
        })
        require(analyzed_log["structuredContent"]["sourceType"] == "SIMULATION_LOG",
                "log analyzer source type is wrong")
        require("log.simulationlog.unstable" in json.dumps(analyzed_log["structuredContent"]["warnings"]),
                "simulation log instability warning is missing")

        explained_errors = call_tool(client, "gatling_explain_errors", {
            "errorText": "ReadTimeoutException while calling GET /api/orders; status.find.is(200), but actually found 500",
        })
        require("log.timeout" in json.dumps(explained_errors["structuredContent"]["findings"]),
                "focused error explainer missed timeout finding")
        require("rootCauseHypotheses" in explained_errors["structuredContent"],
                "focused error explainer response is missing hypotheses")

        no_pattern_errors = call_tool(client, "gatling_explain_errors", {
            "errorText": "all requests completed successfully",
        })
        require(no_pattern_errors["structuredContent"]["confidence"] == "low",
                "no-pattern error explainer should have low confidence")
        require("errors.no_known_patterns" in json.dumps(no_pattern_errors["structuredContent"]["findings"]),
                "no-pattern error explainer missed info finding")
        require(no_pattern_errors["structuredContent"]["rootCauseHypotheses"] == [],
                "no-pattern error explainer should not invent hypotheses")

        load_model = call_tool(client, "gatling_suggest_load_model", {
            "targetRps": 50,
            "durationSeconds": 300,
            "rampSeconds": 60,
            "p95Ms": 250,
            "workloadType": "open",
        })
        require(load_model["structuredContent"]["suggestedModel"]["estimatedConcurrentUsers"] == 13,
                "load model estimate is wrong")
        require(load_model["structuredContent"]["injectionProfile"]["type"] == "rampAndConstant",
                "load model did not return open workload profile")

        weak_plan = {
            "simulationClassName": "WeakSimulation",
            "scenarioName": "Weak",
            "baseUrl": "https://api.example.test",
            "requests": [{
                "name": "GET /api/orders",
                "method": "GET",
                "path": "/api/orders",
                "headers": {"Authorization": "Bearer #{jwtToken}"},
            }],
            "feeders": [{
                "name": "users",
                "type": "csv",
                "source": "users.csv",
                "strategy": "queue",
            }],
            "steps": [{"type": "feed", "feederName": "missing"}],
            "injectionProfile": {"type": "rampAndConstant"},
            "assertions": [],
        }
        feeder_risk = call_tool(client, "gatling_check_feeder_risk", {
            "plan": weak_plan,
            "sampleCsv": "username,password\nu1,p1\n",
            "expectedUsers": 10,
        })
        require("feeder.undefined" in json.dumps(feeder_risk["structuredContent"]["findings"]),
                "feeder risk checker missed undefined feeder")

        correlation_risk = call_tool(client, "gatling_check_correlation_risk", {
            "plan": weak_plan,
        })
        require("correlation.reference_without_save" in json.dumps(correlation_risk["structuredContent"]["findings"]),
                "correlation risk checker missed missing saveAs")

        assertion_quality = call_tool(client, "gatling_check_assertion_quality", {
            "plan": weak_plan,
        })
        require("assertion.missing_global_assertions" in json.dumps(assertion_quality["structuredContent"]["findings"]),
                "assertion quality checker missed missing global assertions")
        require("assertion.request_status_checks_missing" in json.dumps(assertion_quality["structuredContent"]["findings"]),
                "assertion quality checker missed missing status checks")

        # Exercise the stdio transport after the full public surface. This catches
        # concurrent unicast-sink regressions in the Java SDK transport adapter.
        for iteration in range(128):
            interaction_status = call_tool(client, "gatling_interaction_status", {})
            require(
                interaction_status["structuredContent"]["elicitation"] is False,
                f"stdio transport stress call {iteration + 1} returned an invalid payload",
            )

        require(client.behavioral_tools == EXPECTED_TOOLS,
                f"behavioral tool coverage mismatch: missing={EXPECTED_TOOLS - client.behavioral_tools} "
                f"unexpected={client.behavioral_tools - EXPECTED_TOOLS}")

        require(not any("Exception" in line for line in client.stderr), "stderr contains Exception")
    except Exception as exc:
        stderr_tail = "\n".join(client.stderr[-40:])
        stdout_tail = "\n".join(client.stdout_lines[-20:])
        raise McpSmokeError(
            f"{exc}\n--- stdout tail ---\n{stdout_tail}\n--- stderr tail ---\n{stderr_tail}"
        ) from exc
    finally:
        stderr = "\n".join(client.stderr)
        client.close()
    return stderr


def main():
    parser = argparse.ArgumentParser(description="Run a stdio MCP smoke test against gatling-community-mcp.")
    parser.add_argument("--project-path", default=os.getcwd(), help="Path visible to the MCP server for gatling_detect_project.")
    parser.add_argument("--timeout", type=float, default=45.0, help="Response timeout in seconds.")
    parser.add_argument("command", nargs=argparse.REMAINDER, help="Command after --, for example: -- java -jar target/app.jar")
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("missing command after --")
    try:
        run_smoke(command, args.project_path, args.timeout)
    except Exception as exc:
        print(f"MCP smoke failed: {exc}", file=sys.stderr)
        return 1

    print("MCP smoke passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
