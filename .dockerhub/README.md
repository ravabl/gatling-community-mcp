# gatling-community-mcp

Unofficial local MCP server for Gatling Community authoring.

Maintainer: [ravabl](https://github.com/ravabl)

- [GitHub repository](https://github.com/ravabl/gatling-community-mcp)
- [English README](https://github.com/ravabl/gatling-community-mcp/blob/main/README.md)
- [Russian README](https://github.com/ravabl/gatling-community-mcp/blob/main/README_RU.md)

## Quick Start

### Docker

Run the current release as a loopback-only MCP HTTP daemon with a read-only
project workspace:

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

The MCP endpoint is `http://127.0.0.1:8765/mcp`.

### Standalone JAR

Java 25 is required:

```bash
curl -fLO https://github.com/ravabl/gatling-community-mcp/releases/download/v0.4.0/gatling-community-mcp-0.4.0-all.jar
java -version
java -jar gatling-community-mcp-0.4.0-all.jar http --bind 127.0.0.1 --port 8765
```

For foreground stdio clients, omit the `http` arguments and let the MCP client
own the process `stdin` and `stdout`.

## Image

The canonical multi-arch image is published at GitHub Container Registry:

```bash
docker pull ghcr.io/ravabl/gatling-community-mcp:latest
docker pull ghcr.io/ravabl/gatling-community-mcp:0.4.0
```

Use exact tag `0.4.0` when reproducibility matters.

## Runtime Modes

- `stdio`: default foreground mode for an MCP client that owns the process
  `stdin` and `stdout`.
- `http`: local daemon mode serving MCP at `http://127.0.0.1:8765/mcp`.
- `bridge`: foreground stdio-to-HTTP bridge for command-only MCP clients.

The container defaults to `GATLING_MCP_WORKSPACE_ROOTS=/workspace`. Filesystem
access stays inside configured workspace roots, and paths outside them return
`path.outside_workspace`. The image includes Java 25 and Maven for Maven
compile-only checks. Other build tools require project wrappers or a custom
runtime image.

## Verified Behavior

- OpenAPI import resolves bounded local `#/components/...` references through
  `depth 8`, returns deterministic warnings for remote, circular, unsupported,
  unresolved, and over-depth references, and performs no network fetches.
- Project-context discovery scans detected source/test roots, prunes generated
  and dependency directories, and remains bounded on read-only container
  mounts.
- HTTP retains full plan and code generation.
- WebSocket, SSE, JMS, MQTT, and gRPC expose capability-only notes at
  `gatling://examples/java/{websocket|sse|jms|mqtt|grpc}/capability-notes`.
  Full code generation is disabled for those five protocols; the server
  returns metadata and warnings without fabricated snippets.

## Verified Community Plugins

Opt-in JVM examples use exact verified targets: Kafka `1.0.6`, JDBC `1.2.0`,
AMQP `1.3.3`, and Picatinny `1.24.0`, each verified against Gatling `3.13.5`.
Java and Kotlin use Maven or Gradle; Scala uses sbt. Unverified plugin version,
Gatling line, DSL, build tool, or Java combinations return a structured warning
and no generated code. Broker, database, driver, Redis, and plugin-repository
setup remains an explicit project prerequisite.

## Local MCP Clients

Use foreground stdio mode for a command-based MCP client config:

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

Use an absolute host path and keep `-i`. Do not put `-d` in the MCP client
config because the client must own the child process pipes.

For direct HTTP clients, start the daemon:

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

Check and stop it:

```bash
curl -fsS http://127.0.0.1:8765/healthz
docker logs -f mcp-gatling-community
docker stop mcp-gatling-community
```

Direct HTTP MCP client config:

```json
{
  "mcpServers": {
    "gatling-community": {
      "url": "http://127.0.0.1:8765/mcp"
    }
  }
}
```

For a command-only client connecting to that daemon, use bridge mode:

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
`"--add-host", "host.docker.internal:host-gateway"` when required.

If a client calls a tool with an empty JSON object, the server returns a
missing required argument error. Correct the client prompt or tool-call payload
before changing the server.

## Tags

- `latest`: latest stable release image.
- `0.4`: latest patch release in the `0.4` line.
- `0.4.0`: immutable release image for version `0.4.0`.

## Tag Retention

The Docker Hub mirror retains the current exact tag, minor alias, and stable
alias. Do not rely on `latest` alone in saved MCP client configs.

Older pre-1.0 mirror tags and old unreferenced manifest digests are pruned only
after the release workflow publishes and smoke-tests the replacement image.

## Platforms

- `linux/amd64`
- `linux/arm64`

## Release Verification

The release workflow runs Maven tests, builds and publishes the image, updates
this overview, and smoke-tests the published stdio, HTTP, and bridge modes.

Release artifacts: <https://github.com/ravabl/gatling-community-mcp/releases>

License: Apache License 2.0.
