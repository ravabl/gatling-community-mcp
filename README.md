# gatling-community-mcp

**English** | [Русский](README_RU.md)

[![Java 25](https://img.shields.io/badge/Java-25-blue)](https://openjdk.org/projects/jdk/25/)
[![Maven](https://img.shields.io/badge/build-Maven-blue)](https://maven.apache.org/)
[![Container image](https://img.shields.io/badge/container-ghcr.io%2Fravabl%2Fgatling--community--mcp-blue)](https://github.com/ravabl/gatling-community-mcp/pkgs/container/gatling-community-mcp)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

`gatling-community-mcp` is an unofficial local MCP server for authoring
Gatling Community simulations. It helps generate, review, and validate Gatling
scripts from local MCP-compatible LLMs and agents.

Maintainer: [ravabl](https://github.com/ravabl).

## Quick Start

### Docker

Run the MCP HTTP daemon locally and keep the project workspace read-only:

```bash
docker pull ghcr.io/ravabl/gatling-community-mcp:0.4.0
docker run --rm -d \
  --name mcp-gatling-community \
  --publish 127.0.0.1:8765:8765 \
  --env GATLING_MCP_WORKSPACE_ROOTS=/workspace \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:0.4.0 \
  http --bind 0.0.0.0 --port 8765
curl -fsS http://127.0.0.1:8765/healthz
```

The MCP endpoint is `http://127.0.0.1:8765/mcp`. Replace the source path with
the absolute path to the Gatling project. Foreground stdio client configuration
and bridge mode are documented below.

### Standalone JAR

Java 25 is required. Download the release JAR and run the same local HTTP mode:

```bash
curl -fLO https://github.com/ravabl/gatling-community-mcp/releases/download/v0.4.0/gatling-community-mcp-0.4.0-all.jar
java -version
java -jar gatling-community-mcp-0.4.0-all.jar http --bind 127.0.0.1 --port 8765
```

For a foreground command-based MCP client, use `java -jar
gatling-community-mcp-0.4.0-all.jar` without the `http` arguments. The client
must own the process `stdin` and `stdout`.

## What It Does

- Resolves Gatling Community capabilities for versions `3.7` to `3.15`.
- Detects Maven, Gradle, sbt, npm, and TypeScript npm Gatling projects.
- Resolves project-aware context: source roots, test roots, detected Gatling
  version, Java/Node versions, existing simulations, dependencies, community
  plugin state, package naming, and style conventions.
- Generates HTTP simulations for Java, Kotlin, Scala, JavaScript, and
  TypeScript.
- Generates verified JVM community plugin examples for Kafka, JDBC, AMQP, and
  Picatinny.
- Exposes capability-only guidance for WebSocket, SSE, JMS, MQTT, and gRPC;
  full code generation is disabled for these five protocols.
- Imports OpenAPI JSON/YAML, HAR, curl, and Postman Collection input into
  structured HTTP simulation plans.
- Analyzes local Gatling report artifacts and runtime logs for failed requests,
  slow percentiles, and common operational failures.
- Explains Gatling errors and checks feeder, correlation, assertion, and load
  model risks through focused RCA-oriented tools.
- Analyzes simulations and validates common authoring mistakes.
- Lists and explains DSL methods, validates ordered method chains, and suggests
  known replacement methods for renamed or removed Gatling APIs.
- Runs compile-only project checks through whitelisted Maven, Gradle, sbt, or
  npm commands in an isolated temporary project copy.
- Supports MCP interaction features for capable clients: progress
  notifications, elicitation for missing planning details, and sampling for
  advisory reviews.
- Exposes rich MCP tools with strict input schemas, strict success/error output
  schemas, descriptive metadata, prompt arguments, resource templates, and
  completions for local authoring workflows.

## MCP Surface

The server exposes:

- Tools with `title`, Gatling-specific descriptions, strict `inputSchema`,
  strict success/error `outputSchema` contracts, and non-destructive
  annotations. Most tools are read-only; `gatling_compile_check` is explicitly
  marked non-destructive but not read-only because it executes a compile command
  in a temporary copy.
- Resources for version history, compatibility, method catalogs, community
  plugins, authoring patterns, imports, and golden HTTP flows.
- Resource templates such as `gatling://methods/http/{language}/{version}` and
  `gatling://versions/{version}/features`, and
  `gatling://examples/{language}/{protocol}/{pattern}`.
- Prompts with declared arguments, including `create_simulation` and
  `import_http_plan`, which guide the LLM through plan, validate, and generate.
- Completion suggestions for prompt and resource-template arguments such as
  `gatlingVersion`, `language`, `buildTool`, `protocol`, import source type,
  method category, method name, and example pattern.

### Non-HTTP Capability Resources

The source-data-driven capability matrix is available at
`gatling://compatibility/protocol-matrix`. HTTP keeps deep generation. The
following five Java resources provide protocol-specific prerequisites,
dependency/version checks, and deterministic capability warnings in
`capability-only` mode:

- `gatling://examples/java/websocket/capability-notes`
- `gatling://examples/java/sse/capability-notes`
- `gatling://examples/java/jms/capability-notes`
- `gatling://examples/java/mqtt/capability-notes`
- `gatling://examples/java/grpc/capability-notes`

Full code generation is disabled for WebSocket, SSE, JMS, MQTT, and gRPC. The
server returns capability metadata and warnings without fabricating a source
snippet. HTTP generation and verified JVM community-plugin examples remain
available through their existing paths.

## Verified Community Plugins

Plugin generation is opt-in and uses exact, source-backed compatibility rows.
The current verified targets are:

| Plugin | Plugin version | Verified Gatling | Java | Source |
| --- | --- | --- | --- | --- |
| Kafka | `1.0.6` | `3.13.5` | `17+` | [release](https://github.com/galax-io/gatling-kafka-plugin/releases/tag/v1.0.6) |
| JDBC | `1.2.0` | `3.13.5` | `11+` | [release](https://github.com/galax-io/gatling-jdbc-plugin/releases/tag/v1.2.0) |
| AMQP | `1.3.3` | `3.13.5` | `17+` | [release](https://github.com/galax-io/gatling-amqp-plugin/releases/tag/v1.3.3) |
| Picatinny | `1.24.0` | `3.13.5` | `17+` | [release](https://github.com/galax-io/gatling-picatinny/releases/tag/v1.24.0) |

Java and Kotlin targets use Maven or Gradle; Scala targets use sbt. The server
returns `plugin.compatibility.unverified`, `plugin.dsl.unsupported`,
`plugin.build-tool.unsupported`, or `plugin.java.unsupported` and generates no
code when the requested combination is not verified. Generated Kafka and AMQP
examples include request-reply checks and correlation settings; JDBC includes a
result check; Picatinny includes a feeder, transaction boundaries, and an
assertion. Runtime services, drivers, brokers, topics, queues, and plugin-specific
repositories remain explicit project prerequisites.

## Project Context And Workspace Roots

Filesystem tools are read-only and restricted to configured workspace roots.
By default, a local JAR run allows the current working directory. Container
images set `GATLING_MCP_WORKSPACE_ROOTS=/workspace`, matching the documented
bind mount target.

Use `GATLING_MCP_WORKSPACE_ROOTS` to allow one or more comma-separated roots:

```bash
GATLING_MCP_WORKSPACE_ROOTS=/absolute/path/to/project \
  java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Project-aware tools:

- `gatling_get_project_context`: returns build tool, source roots, test roots,
  detected Gatling version, Java/Node version, simulation files, dependency
  state, plugin state, package naming, style conventions, and warnings.
- `gatling_explain_project_context`: returns the same structured data plus a
  compact explanation for an LLM.
- `gatling_resolve_effective_context`: merges explicit overrides with detected
  project context and returns a concrete `gatlingVersion`, `language`,
  `buildTool`, `protocol`, and runtime versions for follow-up tool calls.

Paths outside the configured roots are rejected with structured error code
`path.outside_workspace`.

## DSL Method Understanding

Use these tools when an LLM needs precise Gatling DSL facts without guessing
method names from memory:

- `gatling_list_dsl_methods`: returns method rows filtered by
  `gatlingVersion`, `language`, `protocol`, and optional `category`.
- `gatling_explain_dsl_method`: returns category, version gates, DSL-specific
  syntax, official/source URL, confidence level, parent context, required
  predecessor, incompatible methods, and compile-risk notes.
- `gatling_validate_method_chain`: validates an ordered method chain such as
  `Simulation -> http -> baseUrl -> scenario -> exec -> http -> post -> check
  -> jsonPath.saveAs`.
- `gatling_find_replacement_method`: maps known renamed/removed methods such as
  `heavisideUsers` to `stressPeakUsers` for active Gatling version gates.

The method-chain validator is a semantic guardrail. It checks version/DSL
availability, required predecessors, and incompatible method combinations before
generation. The semantic matrix is source-data driven and includes DSL-specific
syntax, compile-risk notes, and rules such as `body.StringBody` versus
`formParam`, `check` requiring an HTTP request builder, and `jsonPath.saveAs`
requiring `check`. Final source validity should still be checked with
`gatling_compile_check`.

Example resource:

```text
gatling://examples/java/http/login-token-orders
```

## Rich HTTP Plan Model

HTTP generation is plan-first. Tools produce and validate a structured
`HttpSimulationPlan` before code is rendered.

First-class plan fields include:

- scenario `steps`: request, feed, pause, group, repeat/during/forever loops,
  and conditional branches.
- request shape: headers, query params, form params, multipart body parts,
  resources, checks, auth, cookies, redirects, silent requests, and request
  bodies.
- protocol options: base URL, protocol-level headers, HTTP/2, redirect policy,
  and proxy settings.
- feeders, injection profiles, and global assertions.

Validation, troubleshooting, method requirements, generation decisions, and
renderers use the same traversal model, so nested requests are visible instead
of being silently ignored.

## Renderer Parity

`gatling_generate_from_plan` renders rich HTTP plans for Java, Kotlin, Scala,
JavaScript, and TypeScript. Golden tests cover parity for feeders, groups,
pauses, loops, conditionals, query/form/multipart fields, resources, auth,
cookies, redirects, HTTP/2, and proxy settings.

The renderers are intentionally conservative. They generate code for local
authoring and compile-check workflows; they do not execute load tests.

## Generated Code Validation

`gatling_validate_generated_code` performs deterministic static checks before a
project compile check:

- language/import shape checks for Java, Kotlin, Scala, JavaScript, and
  TypeScript.
- required simulation/setup structure.
- dropped rich-feature markers.
- missing request checks.
- unsafe placeholder conditionals such as always-true `doIf`.
- correlation references that use `#{var}` without a detected earlier
  `saveAs`.

The tool returns `compileReadiness` and a recommended compile command for the
selected build tool. It is not a full compiler; use `gatling_compile_check` for
compile-only verification in an isolated project copy.

## Compile Check

`gatling_compile_check` performs compile-only validation. It never runs a load
test and never accepts an arbitrary shell command from the caller.

Whitelisted commands:

- Maven: `mvn -q -DskipTests test-compile` or project `./mvnw`.
- Gradle: `gradle testClasses` or project `./gradlew`.
- sbt: `sbt Test/compile`.
- npm: `npm exec tsc -- --noEmit` when `tsconfig.json` exists, otherwise
  `npm run build --if-present`.

The tool copies the project to a temporary directory, excludes heavy/generated
folders such as `.git`, `target`, `build`, `.gradle`, and `node_modules`, then
runs the compile command there. Use `"execute": false` to return the planned
command without running it.

The published container image includes Java 25 and Maven, so Maven
`test-compile` checks work without a project wrapper. Gradle, sbt, and npm
checks require the corresponding wrapper in the project or a custom runtime
image that contains those tools.

## HTTP Imports

Import tools do not write files and do not generate code directly. They convert
external HTTP material into a structured simulation plan, validate that plan,
and return warnings before generation.

- `gatling_import_openapi`: OpenAPI JSON or YAML, including Swagger/OpenAPI
  2.0 and OpenAPI 3.x documents.
- `gatling_import_har`: HAR JSON entries, including HAR 1.1 and current 1.2.
- `gatling_import_curl`: one curl command, including classic `-d`/`--data-*`
  syntax and modern `--json` syntax.
- `gatling_import_postman_collection`: Postman Collection v2.0 and v2.1 JSON.

The import output can be passed to `gatling_generate_from_plan` when
`valid=true`. The server detects the source version automatically and returns
it as `sourceVersion`. Sensitive headers such as `Authorization`, `Cookie`, and
`x-api-key` are redacted in the returned plan.

### Local OpenAPI References

OpenAPI import resolves only local JSON Pointer references beginning with
`#/components/...`. Resolution is bounded to `depth 8` and works from new
internal maps and lists, so the parsed input document is not mutated.
Operation and path-item parameters, request bodies and schemas, response
content/examples, and security schemes use resolved local references where
needed. A path such as `/orders/{orderId}` becomes `/orders/#{orderId}`, which
also becomes a feeder candidate.

The importer performs no network fetches. It returns deterministic warnings:
remote references produce `import.openapi.remote_ref_unsupported`; unsupported
local locations produce `import.openapi.ref_unsupported`; circular local
references produce `import.openapi.ref_circular`; unresolved local components
produce `import.openapi.ref_unresolved`; and chains beyond the limit produce
`import.openapi.ref_depth_exceeded`. The client-flow constructs callbacks,
webhooks, and response links remain unsupported and return their existing
warnings.

## Expert Demo

Use this section as a review-ready local demo flow. It covers project context detection, OpenAPI/curl
imports, plan validation, method-chain validation, code generation,
compile-only checking, and report/log analysis with the sample inputs in
`examples/openapi/orders-api.yaml` and `examples/curl/login-orders.sh`.

## Report And Log Analysis

Report and log analysis tools are read-only. They do not start Gatling, modify
reports, or write files.

- `gatling_analyze_report`: accepts `reportPath` or `reportContent`, detects
  Gatling `stats.js` and `stats.json`, and returns `globalStats`,
  `requestStats`, `topSlowRequests`, `topFailedRequests`, `findings`, and
  `warnings`.
- `gatling_analyze_log`: accepts `logPath` or `logText`, detects runtime logs
  and best-effort `simulation.log` request events, and returns runtime findings
  such as connection refused, timeout, TLS handshake failure, DNS failure, JVM
  OOM, and feeder exhaustion.

Prefer `gatling_analyze_report` when a Gatling HTML report is available. Raw
`simulation.log` parsing is intentionally marked best-effort because that file
is not a stable public integration format.

Example:

```json
{
  "reportPath": "/absolute/path/to/target/gatling/orderssimulation-20260704080000000",
  "contentType": "AUTO",
  "errorRateThreshold": 1.0,
  "p95ThresholdMs": 1000,
  "p99ThresholdMs": 2000
}
```

## Focused Troubleshooting

Use focused troubleshooting tools when an LLM needs a concrete next action
rather than a generic report summary:

- `gatling_explain_errors`: converts Gatling runtime errors, failed checks, and
  stack traces into findings, root-cause hypotheses, verification steps, and
  next actions. If no known pattern is found, it returns
  `errors.no_known_patterns` with low confidence and does not invent root-cause
  hypotheses.
- `gatling_suggest_load_model`: suggests an initial open or closed workload
  model from target RPS, expected users, duration, ramp, and p95 latency.
- `gatling_check_feeder_risk`: checks undefined feeders, finite queue/shuffle
  feeders, row-count risk, and session placeholders not backed by feeder data.
- `gatling_check_correlation_risk`: checks `#{session}` references against
  earlier `check(...saveAs(...))` correlation and common feeder-provided values.
- `gatling_check_assertion_quality`: checks missing status checks, error-rate
  assertions, latency budgets, and weak quality gates.

All five tools return the same structured RCA shape: `findings`,
`rootCauseHypotheses`, `verificationSteps`, `nextActions`, `confidence`,
`assumptions`, and `risks`.

Feeder, correlation, and assertion checks use the rich HTTP step traversal, so
requests nested inside groups, loops, and conditionals are analyzed.

## MCP Interaction Features

P4 interaction features are optional and degrade cleanly when the client does
not support them.

- `gatling_interaction_status`: reports current client support for
  `elicitation`, `sampling`, and progress notifications.
- Progress: tools emit MCP progress notifications only when the incoming
  request includes `_meta.progressToken`.
- Elicitation: `gatling_plan_simulation` can ask the client for missing
  `goal`, `baseUrl`, and `simulationClassName` when `useElicitation=true`.
- Sampling: `gatling_plan_simulation`, `gatling_analyze_report`, and
  `gatling_analyze_log` can ask the client model for an advisory review when
  `useSampling=true`.

Sampling output is advisory. Deterministic structured fields such as `plan`,
`findings`, `requestStats`, and `warnings` remain the authoritative MCP output.

## Safety And Observability

- All filesystem paths are resolved under configured workspace roots. Paths
  outside those roots return structured `path.outside_workspace` tool errors.
- Every public tool is guarded by recursive input limits for depth, node count,
  total text size, and per-string size. Oversized input returns structured
  `input.*` tool errors before dispatch.
- Tool lifecycle audit events are emitted as JSON lines to `stderr`:
  `tool.started`, `tool.finished`, and `tool.failed`. Audit events include tool
  name, argument keys, estimated text size, result size, and stable error code.
  They do not include raw request bodies, generated code, feeder data, or raw
  header values.
- Secrets in logs, sampling prompts, troubleshooting evidence, and errors are
  masked before they are returned or logged. Sensitive `Authorization` values
  are returned with redacted token placeholders.

## Runtime Modes

The default mode is `stdio`. That is the best option when the MCP client can
launch a local process or container.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar
```

The HTTP daemon mode is for users who want to start the MCP server once in the
background and connect tools to `http://127.0.0.1:8765/mcp`.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar \
  http --bind 127.0.0.1 --port 8765
```

The bridge mode is for MCP clients that only support `command` and `args`, but
you still want to connect to an already running HTTP daemon.

```bash
java -jar target/gatling-community-mcp-0.4.0-all.jar \
  bridge --url http://127.0.0.1:8765/mcp
```

HTTP mode is local-first. Keep it on loopback (`127.0.0.1`) or publish the
container port only to loopback.

## Build From Source

On macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"

mvn test
mvn -q -DskipTests package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- "$JAVA_HOME/bin/java" -jar target/gatling-community-mcp-0.4.0-all.jar
```

On Linux and other Unix-like systems, with JDK 25 already installed and
`JAVA_HOME` pointing to it:

```bash
: "${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

mvn test
mvn -q -DskipTests package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- "$JAVA_HOME/bin/java" -jar target/gatling-community-mcp-0.4.0-all.jar
```

Expected result: Maven tests and packaging succeed, and the smoke runner ends
with `MCP smoke passed`.

Release images are published to GitHub Container Registry as `latest`, minor
tags such as `0.4`, and exact release tags such as `0.4.0`. GitHub releases
also update the Docker Hub mirror automatically.

## Docker Hub Retention

The Docker Hub mirror keeps three tags for the current stable release:

- `0.4.0`: exact release tag for reproducible installs.
- `0.4`: moving minor alias for the latest `0.4.x` release.
- `latest`: moving stable alias for quick local setup.

Do not rely on `latest` alone in saved MCP client configs. Use an exact release
tag when reproducibility matters.

The release workflow applies Docker Hub tag retention through
`.github/workflows/dockerhub-retention.yml` after the image is published and
smoke-tested. For early pre-1.0 releases, old Docker Hub mirror tags are pruned
together with old unreferenced manifest digests.
After the project has external users depending on exact tags, increase the
retention window rather than deleting old stable tags immediately.

The current release is multi-arch. Keep the `linux/amd64` and `linux/arm64`
manifests attached to `0.4.0`, `0.4`, and `latest`.

## Connect With Stdio

Use this mode when the MCP client can launch a process. The client sends
JSON-RPC messages to `stdin` and reads MCP responses from `stdout`.

Important container rules:

- Use an absolute host path in the bind mount. Most MCP clients do not
  shell-expand `$PWD` inside JSON config.
- Keep `-i`; the stdio transport requires an open `stdin`.
- Do not use `-d`; the MCP client must own the child process pipes.
- Keep the project mount read-only unless a future tool explicitly needs write
  access.

Docker-based MCP client configuration:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "--name",
        "mcp-gatling-community",
        "--label",
        "org.opencontainers.image.title=MCP Gatling-community",
        "--env",
        "GATLING_MCP_WORKSPACE_ROOTS=/workspace",
        "--mount",
        "type=bind,source=/absolute/path/to/project,target=/workspace,readonly",
        "ghcr.io/ravabl/gatling-community-mcp:latest"
      ]
    }
  }
}
```

Apple Containers MCP client configuration:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "container",
      "args": [
        "run",
        "--rm",
        "-i",
        "--name",
        "mcp-gatling-community",
        "--label",
        "org.opencontainers.image.title=MCP Gatling-community",
        "--env",
        "GATLING_MCP_WORKSPACE_ROOTS=/workspace",
        "--mount",
        "type=bind,source=/absolute/path/to/project,target=/workspace,readonly",
        "ghcr.io/ravabl/gatling-community-mcp:latest"
      ]
    }
  }
}
```

Local JAR MCP client configuration:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/target/gatling-community-mcp-0.4.0-all.jar"],
      "env": {
        "GATLING_MCP_WORKSPACE_ROOTS": "/absolute/path/to/project"
      }
    }
  }
}
```

The server writes MCP JSON-RPC messages only to `stdout`. Logs go to `stderr`.

## Run As A Local HTTP Daemon

Use HTTP daemon mode when you want to start the server manually and leave it
running in the background.

Docker:

```bash
docker run --rm -d \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --publish 127.0.0.1:8765:8765 \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:latest \
  http --bind 0.0.0.0 --port 8765
```

Apple Containers:

```bash
container run --rm --detach \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --publish 127.0.0.1:8765:8765 \
  --mount "type=bind,source=/absolute/path/to/project,target=/workspace,readonly" \
  ghcr.io/ravabl/gatling-community-mcp:latest \
  http --bind 0.0.0.0 --port 8765
```

Check and stop the daemon:

```bash
curl -fsS http://127.0.0.1:8765/healthz
docker logs -f mcp-gatling-community
docker stop mcp-gatling-community
```

Direct HTTP MCP client configuration, for clients that support MCP HTTP
servers:

```json
{
  "mcpServers": {
    "gatling-community": {
      "url": "http://127.0.0.1:8765/mcp"
    }
  }
}
```

## Connect To The HTTP Daemon Through Bridge

Use bridge mode when the daemon is already running, but the MCP client only
accepts a local command.

Local JAR bridge:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/target/gatling-community-mcp-0.4.0-all.jar",
        "bridge",
        "--url",
        "http://127.0.0.1:8765/mcp"
      ]
    }
  }
}
```

Container bridge:

```json
{
  "mcpServers": {
    "gatling-community": {
      "command": "docker",
      "args": [
        "run",
        "--rm",
        "-i",
        "ghcr.io/ravabl/gatling-community-mcp:latest",
        "bridge",
        "--url",
        "http://host.docker.internal:8765/mcp"
      ]
    }
  }
}
```

On Linux Docker, add
`"--add-host", "host.docker.internal:host-gateway"` to the container bridge args
if `host.docker.internal` is not available.

## Manual Stdio Behavior

A foreground stdio run expects an MCP client or smoke runner to send JSON-RPC
messages. If you run it manually and close `stdin`, it can log an EOF parsing
error such as:

```text
MismatchedInputException: No content to map due to end-of-input
```

That means the MCP transport tried to read the next JSON-RPC message but the
input stream ended. For manual long-running use, prefer HTTP daemon mode.

For protocol validation, use the smoke runner:

```bash
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar
```

To validate a running HTTP daemon through the bridge:

```bash
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar \
  bridge --url http://127.0.0.1:8765/mcp
```

## Container Deployment

Use these commands to verify the same foreground stdio behavior with a locally
built image before publishing a release.

### Container Runtime Verification

Release-style verification was run locally with the `gatling-community-mcp:hardening`
tag: Docker and Apple Containers both built the image and completed the stdio smoke
test. Apple Containers verification requires its system service and builder to be
available. The `hardening` tag is only for local verification; use a release tag for
deployment.

Build and smoke-test with Docker:

```bash
docker build -t gatling-community-mcp:local .

./scripts/mcp-smoke.py \
  --project-path /workspace \
  -- docker run --rm -i \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --mount "type=bind,source=$PWD,target=/workspace,readonly" \
  gatling-community-mcp:local
```

Build and smoke-test with Apple Containers:

```bash
container system start
container builder start
container build -f Dockerfile -t gatling-community-mcp:local .

./scripts/mcp-smoke.py \
  --project-path /workspace \
  -- container run --rm -i \
  --name mcp-gatling-community \
  --label "org.opencontainers.image.title=MCP Gatling-community" \
  --env "GATLING_MCP_WORKSPACE_ROOTS=/workspace" \
  --mount "type=bind,source=$PWD,target=/workspace,readonly" \
  gatling-community-mcp:local
```

The runtime container name is `mcp-gatling-community`. The OCI title label is
`MCP Gatling-community`.

## How To Contribute

Use Java 25 and Maven. Keep the change small, add or update tests, and run:

```bash
mvn test
mvn package
./scripts/mcp-smoke.py \
  --project-path "$PWD" \
  -- java -jar target/gatling-community-mcp-0.4.0-all.jar
```

Open an issue first for compatibility changes, new plugin support, or protocol
behavior changes. Small documentation fixes can go straight to a pull request.
Pull requests should state what changed, why it changed, the linked issue when
applicable, and the exact verification commands that passed.

## Issue Flow

Use the issue category that matches the work:

- Bug report: something is broken or unsafe.
- Feature request: new MCP behavior or a new workflow.
- Documentation: unclear or missing docs.
- Compatibility: Gatling version, DSL, build tool, or protocol support.
- Community plugin: Kafka, JDBC, AMQP, Picatinny, or another JVM plugin.
- Question: usage help that is not yet a bug or feature request.

The templates ask for the minimum context needed to reproduce or decide the
issue. Blank issues are disabled to keep the backlog readable.

## Source Data Maintenance

Compatibility claims are stored under `src/main/resources/data`, not embedded
in prompts. A source-data change must include a primary documentation or
release URL, an explicit confidence level, version and DSL gates, semantic
rules for every added method, and focused positive and negative tests. Plugin
generation is enabled only for an exact verified target. Run the full Java 25
suite and MCP smoke before proposing an update. Maven enforces JaCoCo gates of
at least 85% line coverage and 60% branch coverage.

## Release Notes

### 0.4.0

- Adds richer existing-simulation analysis and chain-aware feature validation.
- Returns strict structured target, warning, finding, capability, and plugin
  identity data from the base authoring tools.
- Uses exact verified plugin targets and independent Java, Kotlin, and Scala
  examples for Kafka, JDBC, AMQP, and Picatinny.
- Adds complete recipe and project-template resources and expands the
  source-backed HTTP DSL catalog to 132 methods.
- Makes project-context discovery bounded and fast in read-only Docker and
  Apple Containers by scanning detected source roots and resolving Maven
  properties without combinatorial recursion.
- Consolidates public documentation into English, Russian, and Docker Hub
  overviews with Docker and standalone Quick Start paths.

## License

Licensed under Apache License 2.0. See `LICENSE`.
