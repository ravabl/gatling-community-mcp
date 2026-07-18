package io.github.gatlingcommunity.mcp.core.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gatlingcommunity.mcp.core.error.ToolException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputLimitsTest {
    @Test
    void returnsNullAsEmptyAndAcceptsBoundedText() {
        assertThat(InputLimits.requireMaxChars(null, 3, "$.code")).isEmpty();
        assertThat(InputLimits.requireMaxChars("abc", 3, "$.code")).isEqualTo("abc");
    }

    @Test
    void rejectsFieldSpecificCharacterLimit() {
        assertToolError(
                () -> InputLimits.requireMaxChars("abcd", 3, "$.code"),
                "input.too_large",
                "$.code"
        );
    }

    @Test
    void rejectsOversizedObjectKey() {
        assertToolError(
                () -> InputLimits.requireToolInputWithinLimits(Map.of("k".repeat(257), "value")),
                "input.key_too_large",
                "$"
        );
    }

    @Test
    void rejectsOversizedStringAndAggregateInput() {
        assertToolError(
                () -> InputLimits.requireToolInputWithinLimits(Map.of(
                        "value", "x".repeat(InputLimits.TOOL_ARGUMENT_MAX_STRING_CHARS + 1)
                )),
                "input.string_too_large",
                "$.value"
        );
        var half = "x".repeat(InputLimits.TOOL_ARGUMENT_MAX_TOTAL_CHARS / 2);
        assertToolError(
                () -> InputLimits.requireToolInputWithinLimits(Map.of("first", half, "second", half)),
                "input.total_size_exceeded",
                "$"
        );
    }

    @Test
    void rejectsDepthAndNodeCountLimits() {
        Object nested = "leaf";
        for (int i = 0; i <= InputLimits.TOOL_ARGUMENT_MAX_DEPTH; i++) {
            nested = List.of(nested);
        }
        var tooDeep = Map.of("value", nested);
        assertToolError(
                () -> InputLimits.requireToolInputWithinLimits(tooDeep),
                "input.depth_exceeded",
                "$.value[0]"
        );

        var tooManyNodes = Collections.nCopies(InputLimits.TOOL_ARGUMENT_MAX_NODES + 1, 1);
        assertToolError(
                () -> InputLimits.requireToolInputWithinLimits(Map.of("values", tooManyNodes)),
                "input.node_count_exceeded",
                "$.values["
        );
    }

    @Test
    void acceptsNullAndNormalNestedInput() {
        assertThatNoException().isThrownBy(() -> InputLimits.requireToolInputWithinLimits(null));
        assertThatNoException().isThrownBy(() -> InputLimits.requireToolInputWithinLimits(Map.of(
                "plan", Map.of("requests", List.of(Map.of("name", "login")))
        )));
    }

    private static void assertToolError(Runnable invocation, String code, String path) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(ToolException.class, exception -> {
                    assertThat(exception.error().code()).isEqualTo(code);
                    assertThat(exception.error().path()).startsWith(path);
                    assertThat(exception.error().details()).isNotEmpty();
                });
    }
}
