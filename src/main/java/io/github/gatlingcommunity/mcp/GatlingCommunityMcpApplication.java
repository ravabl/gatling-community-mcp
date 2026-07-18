package io.github.gatlingcommunity.mcp;

import io.github.gatlingcommunity.mcp.analysis.SimulationAnalyzer;
import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import io.github.gatlingcommunity.mcp.detect.ProjectDetector;
import io.github.gatlingcommunity.mcp.filesystem.WorkspacePathPolicy;
import io.github.gatlingcommunity.mcp.generation.CommunityPluginSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.HttpSimulationGenerator;
import io.github.gatlingcommunity.mcp.generation.SimulationGenerator;
import io.github.gatlingcommunity.mcp.mcp.GatlingMcpServerFactory;
import io.github.gatlingcommunity.mcp.mcp.McpToolRegistry;
import io.github.gatlingcommunity.mcp.resources.GatlingPromptCatalog;
import io.github.gatlingcommunity.mcp.resources.GatlingResourceCatalog;
import io.github.gatlingcommunity.mcp.runtime.GatlingMcpRuntimeConfig;
import io.github.gatlingcommunity.mcp.runtime.HttpMcpServerRunner;
import io.github.gatlingcommunity.mcp.runtime.StdioHttpBridge;
import io.github.gatlingcommunity.mcp.validation.FeatureUsageValidator;
import io.github.gatlingcommunity.mcp.validation.SecretMasker;
import java.util.concurrent.CountDownLatch;

public final class GatlingCommunityMcpApplication {
    private GatlingCommunityMcpApplication() {
    }

    public static void main(String[] args) throws Exception {
        var config = GatlingMcpRuntimeConfig.from(args, System.getenv());
        switch (config.mode()) {
            case HELP -> System.out.print(GatlingMcpRuntimeConfig.usage());
            case STDIO -> runStdio();
            case HTTP -> runHttp(config);
            case BRIDGE -> new StdioHttpBridge(config).run(System.in, System.out, System.err);
        }
    }

    private static void runStdio() throws InterruptedException {
        var registry = createDefaultRegistry();
        var server = GatlingMcpServerFactory.createStdio(registry);
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        new CountDownLatch(1).await();
    }

    private static void runHttp(GatlingMcpRuntimeConfig config) throws InterruptedException {
        var runner = HttpMcpServerRunner.start(config, createDefaultRegistry());
        Runtime.getRuntime().addShutdownHook(new Thread(runner::close));
        System.err.printf(
                "gatling-community-mcp HTTP daemon listening on %s%n",
                runner.endpointUri()
        );
        new CountDownLatch(1).await();
    }

    public static McpToolRegistry createDefaultRegistry() {
        var sourceData = SourceDataRepository.loadDefault();
        var capabilityService = new CapabilityService(sourceData);
        var generator = new SimulationGenerator(
                capabilityService,
                new HttpSimulationGenerator(),
                new CommunityPluginSimulationGenerator()
        );
        var featureValidator = new FeatureUsageValidator(sourceData, new SecretMasker());
        var registry = McpToolRegistry.createDefault(
                capabilityService,
                generator,
                new ProjectDetector(),
                new SimulationAnalyzer(featureValidator),
                featureValidator,
                new GatlingResourceCatalog(sourceData),
                new GatlingPromptCatalog(),
                sourceData,
                WorkspacePathPolicy.fromEnvironment(System.getenv())
        );
        return registry;
    }
}
