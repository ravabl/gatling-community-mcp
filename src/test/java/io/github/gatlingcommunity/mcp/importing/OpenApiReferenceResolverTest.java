package io.github.gatlingcommunity.mcp.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiReferenceResolverTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void resolvesLocalReferencesWithoutMutatingParsedInput() throws Exception {
        var root = json.readValue("""
                {
                  "paths": {
                    "/orders": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/CreateOrder"}
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "CreateOrder": {
                        "type": "object",
                        "properties": {"customerId": {"type": "string"}}
                      }
                    }
                  }
                }
                """, new TypeReference<Map<String, Object>>() {
        });
        var original = json.writeValueAsString(root);
        var warnings = new ArrayList<ImportWarning>();

        var resolvedPaths = new OpenApiReferenceResolver(root, warnings).resolveMap(root.get("paths"), "$.paths");

        assertThat(resolvedPaths.toString()).contains("customerId").doesNotContain("$ref");
        assertThat(json.writeValueAsString(root)).isEqualTo(original);
        assertThat(warnings).isEmpty();
    }
}
