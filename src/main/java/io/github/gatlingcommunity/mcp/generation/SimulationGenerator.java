package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.compatibility.CapabilityService;
import io.github.gatlingcommunity.mcp.core.model.Protocol;

public final class SimulationGenerator {
    private final CapabilityService capabilityService;
    private final HttpSimulationGenerator httpGenerator;
    private final CommunityPluginSimulationGenerator pluginGenerator;

    public SimulationGenerator(CapabilityService capabilityService,
                               HttpSimulationGenerator httpGenerator,
                               CommunityPluginSimulationGenerator pluginGenerator) {
        this.capabilityService = capabilityService;
        this.httpGenerator = httpGenerator;
        this.pluginGenerator = pluginGenerator;
    }

    public GeneratedSimulation generate(GenerationRequest request) {
        var capability = capabilityService.resolve(request.target(), request.protocol());
        if (!capability.supported() || "capability-only".equals(capability.generationMode())) {
            return new GeneratedSimulation(request.target(), "", capability.warnings(),
                    capability.generationMode(), capability.metadata());
        }
        if (request.protocol() == Protocol.HTTP) {
            return new GeneratedSimulation(request.target(), httpGenerator.generate(request), capability.warnings(),
                    capability.generationMode(), capability.metadata());
        }
        return new GeneratedSimulation(request.target(), pluginGenerator.generate(request), capability.warnings(),
                capability.generationMode(), capability.metadata());
    }
}
