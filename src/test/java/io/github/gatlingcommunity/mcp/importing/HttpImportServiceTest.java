package io.github.gatlingcommunity.mcp.importing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.authoring.CookiePlan;
import io.github.gatlingcommunity.mcp.authoring.HttpResourcePlan;
import io.github.gatlingcommunity.mcp.authoring.MultipartPartPlan;
import org.junit.jupiter.api.Test;

class HttpImportServiceTest {
    private final HttpImportService service = new HttpImportService();

    @Test
    void importsOpenApiOperationsIntoHttpSimulationPlan() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "servers": [{"url": "https://api.example.test"}],
                  "paths": {
                    "/login": {
                      "post": {
                        "operationId": "login",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "required": ["username", "password"],
                                "properties": {
                                  "username": {"type": "string"},
                                  "password": {"type": "string"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {"201": {"description": "created"}}
                      }
                    },
                    "/api/orders": {
                      "get": {
                        "operationId": "listOrders",
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, new HttpImportOptions("OrdersSimulation", "Imported Orders API", ""));

        assertThat(result.sourceType()).isEqualTo("OPENAPI");
        assertThat(result.requestCount()).isEqualTo(2);
        assertThat(result.plan().simulationClassName()).isEqualTo("OrdersSimulation");
        assertThat(result.plan().scenarioName()).isEqualTo("Imported Orders API");
        assertThat(result.plan().baseUrl()).isEqualTo("https://api.example.test");
        assertThat(result.plan().requests())
                .extracting("name", "method", "path")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("login", "POST", "/login"),
                        org.assertj.core.groups.Tuple.tuple("listOrders", "GET", "/api/orders")
                );
        assertThat(result.plan().requests().getFirst().body())
                .contains("#{username}", "#{password}");
        assertThat(result.plan().requests().getFirst().checks().getFirst().toMap())
                .containsEntry("type", "status")
                .containsEntry("expected", "201");
    }

    @Test
    void returnsEndpointInventoryWithOpenApiTagsAndAuthSchemes() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": {"type": "http", "scheme": "bearer"}
                    }
                  },
                  "servers": [{"url": "https://api.example.test"}],
                  "paths": {
                    "/login": {
                      "post": {
                        "tags": ["auth"],
                        "operationId": "login",
                        "security": [{"bearerAuth": []}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    },
                    "/orders": {
                      "get": {
                        "tags": ["orders"],
                        "operationId": "listOrders",
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.endpointInventory().authSchemes()).containsExactly("bearerAuth");
        assertThat(result.endpointInventory().groups()).containsKeys("auth", "orders");
        assertThat(result.endpointInventory().groups().get("auth")).containsExactly("POST /login");
        assertThat(result.endpointInventory().groups().get("orders")).containsExactly("GET /orders");
        assertThat(result.endpointInventory().endpoints())
                .extracting("name", "method", "path", "tags")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("login", "POST", "/login", java.util.List.of("auth")),
                        org.assertj.core.groups.Tuple.tuple("listOrders", "GET", "/orders", java.util.List.of("orders"))
                );
    }

    @Test
    void importsOpenApiYamlDocuments() {
        var result = service.importOpenApi("""
                openapi: 3.0.3
                info:
                  title: YAML API
                servers:
                  - url: https://yaml.example.test
                paths:
                  /health:
                    get:
                      operationId: health
                      responses:
                        "204":
                          description: no content
                """, HttpImportOptions.defaults());

        assertThat(result.sourceType()).isEqualTo("OPENAPI");
        assertThat(result.plan().scenarioName()).isEqualTo("YAML API");
        assertThat(result.plan().baseUrl()).isEqualTo("https://yaml.example.test");
        assertThat(result.plan().requests().getFirst().name()).isEqualTo("health");
        assertThat(result.plan().requests().getFirst().checks().getFirst().toMap())
                .containsEntry("expected", "204");
    }

    @Test
    void resolvesLocalComponentParameterReferencesIntoPathFeeders() {
        var result = service.importOpenApi("""
                openapi: 3.0.3
                paths:
                  /orders/{orderId}:
                    get:
                      operationId: getOrder
                      parameters:
                        - $ref: '#/components/parameters/OrderId'
                      responses:
                        '200':
                          description: ok
                components:
                  parameters:
                    OrderId:
                      name: orderId
                      in: path
                      required: true
                      schema:
                        type: string
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();
        assertThat(request.path()).isEqualTo("/orders/#{orderId}");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "200");
        assertThat(result.feederCandidates()).contains("orderId");
    }

    @Test
    void describesPathPlaceholdersAsFeederCandidates() {
        var result = service.importCurl("curl https://api.example.test/orders/#{orderId}",
                HttpImportOptions.defaults());

        assertThat(result.feederCandidates()).containsExactly("orderId");
        assertThat(result.warnings())
                .filteredOn(warning -> "import.feeder.candidate".equals(warning.code()))
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.path()).isEqualTo("$.requests");
                    assertThat(warning.message())
                            .isEqualTo("Request path or request-body representation placeholder 'orderId' can be backed by a Gatling feeder.");
                });
    }

    @Test
    void resolvesLocalComponentSchemaReferencesForRequestBodies() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/CreateOrder"}
                            }
                          }
                        },
                        "responses": {"201": {"description": "created"}}
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
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();
        assertThat(request.body()).contains("#{customerId}");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "201");
        assertThat(result.feederCandidates()).contains("customerId");
    }

    @Test
    void warnsForRemoteOpenApiReferencesWithoutFetchingThem() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders": {
                      "get": {
                        "parameters": [{"$ref": "https://127.0.0.1.invalid/parameters.yaml#/OrderId"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("import.openapi.remote_ref_unsupported");
    }

    @Test
    void warnsForUnresolvedLocalOpenApiReferences() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders": {
                      "get": {
                        "parameters": [{"$ref": "#/components/parameters/MissingOrderId"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("import.openapi.ref_unresolved");
    }

    @Test
    void warnsForUnsupportedLocalOpenApiReferences() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders": {
                      "get": {
                        "parameters": [{"$ref": "#/paths/~1orders/get/parameters/0"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("import.openapi.ref_unsupported");
    }

    @Test
    void warnsForCircularLocalOpenApiReferences() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders/{orderId}": {
                      "get": {
                        "parameters": [{"$ref": "#/components/parameters/OrderId"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  },
                  "components": {
                    "parameters": {
                      "OrderId": {"$ref": "#/components/parameters/OrderId"}
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("import.openapi.ref_circular");
    }

    @Test
    void warnsWhenLocalOpenApiReferenceDepthExceedsEight() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders/{orderId}": {
                      "get": {
                        "parameters": [{"$ref": "#/components/parameters/Parameter1"}],
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  },
                  "components": {
                    "parameters": {
                      "Parameter1": {"$ref": "#/components/parameters/Parameter2"},
                      "Parameter2": {"$ref": "#/components/parameters/Parameter3"},
                      "Parameter3": {"$ref": "#/components/parameters/Parameter4"},
                      "Parameter4": {"$ref": "#/components/parameters/Parameter5"},
                      "Parameter5": {"$ref": "#/components/parameters/Parameter6"},
                      "Parameter6": {"$ref": "#/components/parameters/Parameter7"},
                      "Parameter7": {"$ref": "#/components/parameters/Parameter8"},
                      "Parameter8": {"$ref": "#/components/parameters/Parameter9"},
                      "Parameter9": {"name": "orderId", "in": "path", "schema": {"type": "string"}}
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("import.openapi.ref_depth_exceeded");
    }

    @Test
    void importsOpenApi31Documents() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.1.0",
                  "info": {"title": "OpenAPI 3.1 API"},
                  "servers": [{"url": "https://oas31.example.test"}],
                  "paths": {
                    "/users": {
                      "post": {
                        "operationId": "createUser",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "email": {"type": "string"},
                                  "active": {"type": "boolean"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {"202": {"description": "accepted"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.sourceType()).isEqualTo("OPENAPI");
        assertThat(result.sourceVersion()).isEqualTo("3.1.0");
        assertThat(result.plan().scenarioName()).isEqualTo("OpenAPI 3.1 API");
        assertThat(result.plan().baseUrl()).isEqualTo("https://oas31.example.test");
        assertThat(result.plan().requests().getFirst().body())
                .contains("#{email}", "true");
        assertThat(result.plan().requests().getFirst().checks().getFirst().toMap())
                .containsEntry("expected", "202");
    }

    @Test
    void importsOpenApi32Documents() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.2.0",
                  "info": {"title": "OpenAPI 3.2 API"},
                  "servers": [{"url": "https://oas32.example.test"}],
                  "paths": {
                    "/ping": {
                      "get": {
                        "operationId": "ping",
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.sourceVersion()).isEqualTo("3.2.0");
        assertThat(result.plan().scenarioName()).isEqualTo("OpenAPI 3.2 API");
        assertThat(result.plan().baseUrl()).isEqualTo("https://oas32.example.test");
        assertThat(result.plan().requests().getFirst().name()).isEqualTo("ping");
        assertThat(result.plan().requests().getFirst().path()).isEqualTo("/ping");
    }

    @Test
    void importsOpenApiRichHttpRequestFields() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.1.0",
                  "components": {
                    "securitySchemes": {
                      "bearerAuth": {"type": "http", "scheme": "bearer"}
                    }
                  },
                  "servers": [{"url": "https://api.example.test"}],
                  "paths": {
                    "/orders": {
                      "get": {
                        "operationId": "listOrders",
                        "security": [{"bearerAuth": []}],
                        "parameters": [
                          {"name": "state", "in": "query", "schema": {"type": "string"}},
                          {"name": "page", "in": "query", "example": 2}
                        ],
                        "responses": {"200": {"description": "ok"}}
                      }
                    },
                    "/login": {
                      "post": {
                        "operationId": "login",
                        "requestBody": {
                          "content": {
                            "application/x-www-form-urlencoded": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "username": {"type": "string"},
                                  "password": {"type": "string"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {"200": {"description": "ok"}}
                      }
                    },
                    "/upload": {
                      "post": {
                        "operationId": "upload",
                        "requestBody": {
                          "content": {
                            "multipart/form-data": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "file": {"type": "string", "format": "binary"},
                                  "metadata": {"type": "string"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {"201": {"description": "created"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        var orders = result.plan().requests().getFirst();
        var login = result.plan().requests().get(1);
        var upload = result.plan().requests().get(2);

        assertThat(orders.path()).isEqualTo("/orders");
        assertThat(orders.queryParams()).containsEntry("state", "#{state}").containsEntry("page", "2");
        assertThat(orders.auth().type()).isEqualTo("bearer");
        assertThat(orders.auth().token()).isEqualTo("#{token}");
        assertThat(login.formParams()).containsEntry("username", "#{username}")
                .containsEntry("password", "#{password}");
        assertThat(login.body()).isEmpty();
        assertThat(upload.multipartParts())
                .extracting(MultipartPartPlan::name)
                .contains("file", "metadata");
        assertThat(upload.multipartParts().getFirst().fileName()).isEqualTo("#{file}");
    }

    @Test
    void infersFeedersFromOpenApiFormUrlEncodedParameters() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/login": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "application/x-www-form-urlencoded": {
                              "schema": {
                                "type": "object",
                                "properties": {"username": {"type": "string"}}
                              }
                            }
                          }
                        },
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.plan().requests().getFirst().formParams()).containsEntry("username", "#{username}");
        assertThat(result.feederCandidates()).containsExactly("username");
    }

    @Test
    void infersFeedersFromOpenApiMultipartParts() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/upload": {
                      "post": {
                        "requestBody": {
                          "content": {
                            "multipart/form-data": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "file": {"type": "string", "format": "binary"},
                                  "metadata": {"type": "string"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {"201": {"description": "created"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.plan().requests().getFirst().multipartParts())
                .extracting(MultipartPartPlan::fileName, MultipartPartPlan::value)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("#{file}", ""),
                        org.assertj.core.groups.Tuple.tuple("", "#{metadata}")
                );
        assertThat(result.feederCandidates()).containsExactly("file", "metadata");
    }

    @Test
    void importsSwagger20Documents() {
        var result = service.importOpenApi("""
                {
                  "swagger": "2.0",
                  "info": {"title": "Swagger API"},
                  "host": "swagger.example.test",
                  "basePath": "/v1",
                  "schemes": ["https"],
                  "paths": {
                    "/orders": {
                      "post": {
                        "operationId": "createOrder",
                        "consumes": ["application/json"],
                        "parameters": [
                          {
                            "name": "body",
                            "in": "body",
                            "schema": {
                              "type": "object",
                              "properties": {
                                "orderId": {"type": "string"},
                                "quantity": {"type": "integer"}
                              }
                            }
                          }
                        ],
                        "responses": {"201": {"description": "created"}}
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceType()).isEqualTo("OPENAPI");
        assertThat(result.sourceVersion()).isEqualTo("2.0");
        assertThat(result.plan().scenarioName()).isEqualTo("Swagger API");
        assertThat(result.plan().baseUrl()).isEqualTo("https://swagger.example.test/v1");
        assertThat(request.name()).isEqualTo("createOrder");
        assertThat(request.headers()).containsEntry("Content-Type", "application/json");
        assertThat(request.body()).contains("#{orderId}", "1");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "201");
    }

    @Test
    void reportsWarningWhenOpenApiHasNoOperations() {
        var result = service.importOpenApi("""
                {"openapi":"3.0.3","paths":{}}
                """, HttpImportOptions.defaults());

        assertThat(result.requestCount()).isZero();
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning.code()).isEqualTo("import.openapi.no.operations"));
    }

    @Test
    void importsHarEntriesAndRedactsSensitiveHeaders() {
        var result = service.importHar("""
                {
                  "log": {
                    "entries": [
                      {
                        "request": {
                          "method": "GET",
                          "url": "https://api.example.test/api/orders?state=open",
                          "headers": [
                            {"name": "Authorization", "value": "Bearer real-token"},
                            {"name": "Accept", "value": "application/json"}
                          ]
                        },
                        "response": {"status": 200}
                      }
                    ]
                  }
                }
                """, new HttpImportOptions("HarSimulation", "HAR import", ""));

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceType()).isEqualTo("HAR");
        assertThat(result.sourceVersion()).isEqualTo("1.2");
        assertThat(result.plan().baseUrl()).isEqualTo("https://api.example.test");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/api/orders");
        assertThat(request.queryParams()).containsEntry("state", "open");
        assertThat(request.headers())
                .containsEntry("Accept", "application/json")
                .containsEntry("Authorization", "Bearer <redacted>");
        assertThat(result.warnings())
                .anySatisfy(warning -> assertThat(warning.code()).isEqualTo("import.secret.header.redacted"));
    }

    @Test
    void importsHarResourcesCookiesAndQueryParametersAsFirstClassFields() {
        var result = service.importHar("""
                {
                  "log": {
                    "version": "1.2",
                    "pages": [{"id": "page_1", "title": "Orders"}],
                    "entries": [
                      {
                        "pageref": "page_1",
                        "request": {
                          "method": "GET",
                          "url": "https://api.example.test/orders?state=open",
                          "headers": [{"name": "Accept", "value": "text/html"}],
                          "cookies": [{"name": "tenant", "value": "acme"}]
                        },
                        "response": {"status": 200, "content": {"mimeType": "text/html"}}
                      },
                      {
                        "pageref": "page_1",
                        "request": {
                          "method": "GET",
                          "url": "https://api.example.test/assets/app.js",
                          "headers": []
                        },
                        "response": {"status": 200, "content": {"mimeType": "application/javascript"}}
                      }
                    ]
                  }
                }
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();

        assertThat(request.path()).isEqualTo("/orders");
        assertThat(request.queryParams()).containsEntry("state", "open");
        assertThat(request.cookies()).extracting(CookiePlan::name).contains("tenant");
        assertThat(request.resources())
                .extracting(HttpResourcePlan::path)
                .contains("/assets/app.js");
    }

    @Test
    void importsHar11Entries() {
        var result = service.importHar("""
                {
                  "log": {
                    "version": "1.1",
                    "entries": [
                      {
                        "request": {
                          "method": "POST",
                          "url": "https://old-har.example.test/v1/search",
                          "headers": [{"name": "Content-Type", "value": "application/json"}],
                          "postData": {"text": "{\\"q\\":\\"#{query}\\"}"}
                        },
                        "response": {"status": 201}
                      }
                    ]
                  }
                }
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceVersion()).isEqualTo("1.1");
        assertThat(result.plan().baseUrl()).isEqualTo("https://old-har.example.test");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/search");
        assertThat(request.body()).contains("#{query}");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "201");
    }

    @Test
    void importsCurlCommandWithHeadersBodyAndStatusCheck() {
        var result = service.importCurl("""
                curl -X POST 'https://api.example.test/login' \
                  -H 'Content-Type: application/json' \
                  -H 'X-Tenant: acme' \
                  --data-raw '{"username":"#{username}","password":"#{password}"}'
                """, new HttpImportOptions("CurlSimulation", "curl import", ""));

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceType()).isEqualTo("CURL");
        assertThat(result.sourceVersion()).isEqualTo("command");
        assertThat(result.plan().baseUrl()).isEqualTo("https://api.example.test");
        assertThat(request.name()).isEqualTo("POST /login");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/login");
        assertThat(request.headers()).containsEntry("X-Tenant", "acme");
        assertThat(request.body()).contains("#{username}", "#{password}");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "200");
    }

    @Test
    void importsModernCurlJsonAndClassicGetDataSyntax() {
        var modern = service.importCurl("""
                curl --json '{"email":"#{email}"}' --url https://api.example.test/users
                """, HttpImportOptions.defaults());
        var modernRequest = modern.plan().requests().getFirst();

        assertThat(modernRequest.method()).isEqualTo("POST");
        assertThat(modernRequest.headers()).containsEntry("Content-Type", "application/json");
        assertThat(modernRequest.body()).contains("#{email}");

        var classic = service.importCurl("""
                curl -G 'https://api.example.test/search' --data-urlencode 'q=#{query}' --data-urlencode 'limit=10'
                """, HttpImportOptions.defaults());
        var classicRequest = classic.plan().requests().getFirst();

        assertThat(classicRequest.method()).isEqualTo("GET");
        assertThat(classicRequest.path()).isEqualTo("/search");
        assertThat(classicRequest.queryParams()).containsEntry("q", "#{query}").containsEntry("limit", "10");
        assertThat(classicRequest.body()).isEmpty();
    }

    @Test
    void importsPostmanCollectionItemsAndStatusTests() {
        var result = service.importPostmanCollection("""
                {
                  "info": {"name": "Orders collection", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
                  "item": [
                    {
                      "name": "Auth",
                      "item": [
                        {
                          "name": "Login",
                          "request": {
                            "method": "POST",
                            "url": "https://api.example.test/login",
                            "header": [{"key": "Content-Type", "value": "application/json"}],
                            "body": {"mode": "raw", "raw": "{\\"username\\":\\"#{username}\\",\\"password\\":\\"#{password}\\"}"}
                          },
                          "event": [{
                            "listen": "test",
                            "script": {"exec": ["pm.response.to.have.status(201);"]}
                          }]
                        }
                      ]
                    }
                  ]
                }
                """, new HttpImportOptions("PostmanSimulation", "", ""));

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceType()).isEqualTo("POSTMAN");
        assertThat(result.sourceVersion()).isEqualTo("2.1.0");
        assertThat(result.plan().scenarioName()).isEqualTo("Orders collection");
        assertThat(request.name()).isEqualTo("Login");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/login");
        assertThat(request.body()).contains("#{username}", "#{password}");
        assertThat(request.checks().getFirst().toMap()).containsEntry("expected", "201");
    }

    @Test
    void infersFeedersCorrelationsAndAuthFromPostmanCollection() {
        var result = service.importPostmanCollection("""
                {
                  "info": {"name": "Correlation collection", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
                  "item": [
                    {
                      "name": "Login",
                      "request": {
                        "method": "POST",
                        "url": "https://api.example.test/login",
                        "header": [{"key": "Content-Type", "value": "application/json"}],
                        "body": {"mode": "raw", "raw": "{\\"username\\":\\"{{username}}\\",\\"password\\":\\"{{password}}\\"}"}
                      },
                      "response": [
                        {
                          "name": "Login OK",
                          "body": "{\\"token\\":\\"abc\\",\\"userId\\":\\"u-1\\"}"
                        }
                      ]
                    },
                    {
                      "name": "Orders",
                      "request": {
                        "method": "GET",
                        "url": "https://api.example.test/orders/{{userId}}",
                        "header": [{"key": "Authorization", "value": "Bearer {{token}}"}]
                      }
                    }
                  ]
                }
                """, HttpImportOptions.defaults());

        assertThat(result.feederCandidates()).contains("username", "password");
        assertThat(result.correlationCandidates()).contains("token", "userId");
        assertThat(result.correlationUsages()).contains("token", "userId");
        assertThat(result.warnings())
                .extracting(ImportWarning::code)
                .contains("import.feeder.candidate", "import.correlation.candidate", "import.auth.detected");
    }

    @Test
    void warnsForUnsupportedOpenApiFeatures() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.1.0",
                  "paths": {
                    "/events": {
                      "post": {
                        "operationId": "createEvent",
                        "callbacks": {"onEvent": {}},
                        "responses": {"200": {"description": "ok"}}
                      }
                    }
                  },
                  "webhooks": {
                    "eventHook": {
                      "post": {"responses": {"200": {"description": "ok"}}}
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings())
                .filteredOn(warning -> "import.unsupported.openapi.feature".equals(warning.code()))
                .extracting(ImportWarning::path)
                .contains("$.paths['/events'].post.callbacks", "$.webhooks");
    }

    @Test
    void preservesWarningForOpenApiResponseLinks() {
        var result = service.importOpenApi("""
                {
                  "openapi": "3.0.3",
                  "paths": {
                    "/orders": {
                      "post": {
                        "responses": {
                          "201": {
                            "description": "created",
                            "links": {
                              "GetOrder": {
                                "operationId": "getOrder",
                                "parameters": {"orderId": "$response.body#/id"}
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """, HttpImportOptions.defaults());

        assertThat(result.warnings())
                .filteredOn(warning -> "import.unsupported.openapi.feature".equals(warning.code()))
                .extracting(ImportWarning::path)
                .contains("$.paths['/orders'].post.responses.201.links");
    }

    @Test
    void importsPostmanCollection20UrlObjectSyntax() {
        var result = service.importPostmanCollection("""
                {
                  "info": {
                    "name": "Postman 2.0 collection",
                    "schema": "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
                  },
                  "item": [
                    {
                      "name": "Search",
                      "request": {
                        "method": "GET",
                        "url": {
                          "protocol": "https",
                          "host": ["postman20", "example", "test"],
                          "path": ["api", "search"],
                          "query": [{"key": "q", "value": "#{query}"}]
                        },
                        "header": [{"key": "Accept", "value": "application/json"}]
                      }
                    }
                  ]
                }
                """, HttpImportOptions.defaults());

        var request = result.plan().requests().getFirst();
        assertThat(result.sourceVersion()).isEqualTo("2.0.0");
        assertThat(result.plan().baseUrl()).isEqualTo("https://postman20.example.test");
        assertThat(request.path()).isEqualTo("/api/search");
        assertThat(request.queryParams()).containsEntry("q", "#{query}");
        assertThat(request.headers()).containsEntry("Accept", "application/json");
    }
}
