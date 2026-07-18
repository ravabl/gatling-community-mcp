# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM maven:3.9.11-eclipse-temurin-25

LABEL org.opencontainers.image.title="MCP Gatling-community" \
      org.opencontainers.image.description="Local MCP server for Gatling Community authoring." \
      org.opencontainers.image.source="https://github.com/ravabl/gatling-community-mcp" \
      org.opencontainers.image.url="https://github.com/ravabl/gatling-community-mcp" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser \
    && mkdir -p /workspace \
    && chown 10001:10001 /workspace
WORKDIR /opt/gatling-community-mcp
ENV GATLING_MCP_WORKSPACE_ROOTS=/workspace

COPY --from=build /workspace/target/gatling-community-mcp-*-all.jar ./gatling-community-mcp.jar

USER 10001
EXPOSE 8765
ENTRYPOINT ["java", "-jar", "/opt/gatling-community-mcp/gatling-community-mcp.jar"]
