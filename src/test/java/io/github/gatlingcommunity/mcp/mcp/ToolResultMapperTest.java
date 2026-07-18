package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolResultMapperTest {
    private final ToolResultMapper mapper = new ToolResultMapper();

    @Test
    void mapsSuccessfulResponseToSdkResult() {
        var response = mapper.ok("generated", Map.of("code", "class Example {}"));

        var sdk = mapper.toSdk(response);

        assertThat(response.error()).isFalse();
        assertThat(response.structured()).containsEntry("code", "class Example {}");
        assertThat(sdk.isError()).isFalse();
        assertThat(sdk.structuredContent()).isEqualTo(Map.of("code", "class Example {}"));
        assertThat(sdk.content().toString()).contains("generated");
    }

    @Test
    void preservesCompleteStructuredError() {
        var structured = Map.<String, Object>of(
                "code", "input.invalid",
                "severity", "error",
                "path", "$.plan",
                "message", "Invalid plan",
                "suggestion", "Correct the plan"
        );

        var response = mapper.error("failed", structured);

        assertThat(response.error()).isTrue();
        assertThat(response.structured()).isEqualTo(structured);
    }

    @Test
    void normalizesPartialErrorAndStringifiesDetails() {
        var response = mapper.error("", Map.of(
                "code", "",
                "nested", Map.of("token", "redacted"),
                "items", List.of("a", "b")
        ));

        assertThat(response.structured())
                .containsEntry("code", "tool.error")
                .containsEntry("severity", "error")
                .containsEntry("path", "$")
                .containsEntry("message", "Tool call failed")
                .containsEntry("suggestion", "Inspect the tool input and retry with supported arguments.");
        assertThat(response.structured().get("details").toString())
                .contains("nested={token=redacted}", "items=[a, b]");
    }

    @Test
    void normalizesNullResponseFields() {
        var response = new ToolResultMapper.ToolResponse(null, true, null);
        var error = mapper.error(null, null);

        assertThat(response.text()).isEmpty();
        assertThat(response.structured()).isEmpty();
        assertThat(error.structured()).containsEntry("message", "Tool call failed");
    }
}
