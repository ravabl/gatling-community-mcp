package io.github.gatlingcommunity.mcp.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;
import io.github.gatlingcommunity.mcp.core.model.Protocol;
import io.github.gatlingcommunity.mcp.core.model.TargetContext;
import io.github.gatlingcommunity.mcp.data.SourceDataRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DslMethodServiceTest {
    private final SourceDataRepository sourceData = SourceDataRepository.loadDefault();
    private final DslMethodService service = new DslMethodService(sourceData);
    private final TargetContext target = new TargetContext("3.9.5", "community", DslLanguage.JAVA, BuildTool.MAVEN,
            Optional.of("25"), Optional.empty(), Optional.empty());

    @Test
    void validatesSourceDrivenFeederAndAssertionMethods() {
        var feederChain = service.validateChain(target, Protocol.HTTP, List.of("csv", "recordsCount"));
        var missingFeederSource = service.validateChain(target, Protocol.HTTP, List.of("recordsCount"));
        var assertion = service.explainMethod(
                target, Protocol.HTTP, "details.requestsPerSec.between");

        assertThat(feederChain).containsEntry("valid", true);
        assertThat(missingFeederSource).containsEntry("valid", false);
        assertThat(missingFeederSource.get("findings").toString())
                .contains("method_chain.missing_predecessor", "feeder source");
        assertThat(assertion).containsEntry("found", true);
        assertThat(assertion.get("method").toString()).contains("requestsPerSec", "OFFICIAL_DOCS");
    }

    @Test
    void rejectsRawBodyAndFormParamsInBothOrders() {
        var rawThenForm = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "body.StringBody", "formParam"
        ));
        var formThenRaw = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "formParam", "body.StringBody"
        ));

        assertThat(rawThenForm).containsEntry("valid", false);
        assertThat(rawThenForm.get("findings").toString())
                .contains("method_chain.incompatible_method", "body.StringBody", "formParam");
        assertThat(formThenRaw).containsEntry("valid", false);
        assertThat(formThenRaw.get("findings").toString())
                .contains("method_chain.incompatible_method", "body.StringBody", "formParam");
    }

    @Test
    void enforcesHttpRequestAndCheckPredecessors() {
        var checkWithoutRequest = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "check", "status.is"
        ));
        var saveAsWithoutCheck = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "jsonPath.saveAs"
        ));

        assertThat(checkWithoutRequest).containsEntry("valid", false);
        assertThat(checkWithoutRequest.get("findings").toString())
                .contains("method_chain.missing_predecessor", "check", "HTTP request builder");
        assertThat(saveAsWithoutCheck).containsEntry("valid", false);
        assertThat(saveAsWithoutCheck.get("findings").toString())
                .contains("method_chain.missing_predecessor", "jsonPath.saveAs", "check");
    }

    @Test
    void requiresScenarioOrChainContextForControlFlow() {
        var result = service.validateChain(target, Protocol.HTTP, List.of("doIf"));

        assertThat(result).containsEntry("valid", false);
        assertThat(result.get("findings").toString())
                .contains("method_chain.missing_predecessor", "doIf", "scenario");
    }

    @Test
    void exposesDurationSemanticsForDuring() {
        var scalaTarget = new TargetContext("3.9.5", "community", DslLanguage.SCALA, BuildTool.SBT,
                Optional.of("25"), Optional.empty(), Optional.empty());

        var result = service.explainMethod(scalaTarget, Protocol.HTTP, "during");

        assertThat(result).containsEntry("found", true);
        assertThat(result.get("semantic").toString())
                .contains(".during(seconds.seconds).on(chain)", "duration", "not a repeat count");
    }

    @Test
    void gatesHttpConcurrentRequestsByGatling315() {
        var target314 = new TargetContext("3.14.0", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());
        var target315 = new TargetContext("3.15.1", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        assertThat(service.explainMethod(target314, Protocol.HTTP, "httpConcurrentRequests"))
                .containsEntry("found", false);
        assertThat(service.explainMethod(target315, Protocol.HTTP, "httpConcurrentRequests"))
                .containsEntry("found", true);
    }

    @Test
    void allowsFormParametersAndMultipartBodyPartsInBothOrders() {
        var formThenPart = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "formParam", "bodyPart"
        ));
        var partThenForm = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "bodyPart", "formParam"
        ));
        var formSequenceThenParts = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "formParamSeq", "bodyParts"
        ));
        var partsThenFormSequence = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "bodyParts", "formParamSeq"
        ));

        assertThat(formThenPart).containsEntry("valid", true);
        assertThat(partThenForm).containsEntry("valid", true);
        assertThat(formSequenceThenParts).containsEntry("valid", true);
        assertThat(partsThenFormSequence).containsEntry("valid", true);
    }

    @Test
    void rejectsFullBodiesWithMultipartPartsAndFormSequencesInBothOrders() {
        var fullThenPart = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "body.StringBody", "bodyPart"
        ));
        var partThenFull = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "bodyPart", "body.StringBody"
        ));
        var fullThenFormSequence = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "body.StringBody", "formParamSeq"
        ));
        var formSequenceThenFull = service.validateChain(target, Protocol.HTTP, List.of(
                "Simulation", "http", "post", "formParamSeq", "body.StringBody"
        ));

        for (var result : List.of(fullThenPart, partThenFull, fullThenFormSequence, formSequenceThenFull)) {
            assertThat(result).containsEntry("valid", false);
            assertThat(result.get("findings").toString()).contains("method_chain.incompatible_method");
        }
    }

    @Test
    void findsReplacementForRenamedHeavisideInjection() {
        var target311 = new TargetContext("3.11.2", "community", DslLanguage.JAVA, BuildTool.MAVEN,
                Optional.of("25"), Optional.empty(), Optional.empty());

        var result = service.findReplacement(target311, Protocol.HTTP, "heavisideUsers");

        assertThat(result).containsEntry("found", true);
        assertThat(result.get("replacements").toString())
                .contains("stressPeakUsers", "Gatling 3.11 renamed heavisideUsers");
    }
}
