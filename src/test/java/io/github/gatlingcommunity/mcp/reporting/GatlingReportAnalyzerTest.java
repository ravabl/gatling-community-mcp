package io.github.gatlingcommunity.mcp.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatlingReportAnalyzerTest {
    private final GatlingReportAnalyzer analyzer = new GatlingReportAnalyzer();

    @Test
    void analyzesGatlingStatsJsAndFindsSlowAndFailedRequests() {
        var result = analyzer.analyzeReportContent(statsJs(), new ReportAnalysisOptions(
                "AUTO",
                1.0,
                1_000,
                2_000
        ));

        assertThat(result.sourceType()).isEqualTo("GATLING_STATS_JS");
        assertThat(result.globalStats().total()).isEqualTo(100);
        assertThat(result.globalStats().ok()).isEqualTo(97);
        assertThat(result.globalStats().ko()).isEqualTo(3);
        assertThat(result.globalStats().errorRate()).isEqualTo(3.0);
        assertThat(result.globalStats().p95Ms()).isEqualTo(1_200);
        assertThat(result.requestStats())
                .extracting(RequestStats::name)
                .containsExactly("GET /api/orders", "POST /login");
        assertThat(result.findings())
                .extracting(ReportFinding::code)
                .contains(
                        "report.error-rate.high",
                        "report.p95.high",
                        "report.request.failures"
                );
    }

    @Test
    void analyzesGatlingStatsJsonContent() {
        var result = analyzer.analyzeReportContent(statsJson(), ReportAnalysisOptions.defaults());

        assertThat(result.sourceType()).isEqualTo("GATLING_STATS_JSON");
        assertThat(result.globalStats().name()).isEqualTo("Global Information");
        assertThat(result.requestStats()).hasSize(2);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void analyzesSimulationLogBestEffortAndWarnsAboutInstability() {
        var result = analyzer.analyzeLogContent("""
                REQUEST\t\tGET /api/orders\t1000\t1250\tOK
                REQUEST\t\tGET /api/orders\t2000\t2600\tKO\tstatus.find.is(200), but actually found 500
                REQUEST\t\tPOST /login\t3000\t3100\tOK
                """, new LogAnalysisOptions("SIMULATION_LOG", 1.0, 500, 1_000));

        assertThat(result.sourceType()).isEqualTo("SIMULATION_LOG");
        assertThat(result.globalStats().total()).isEqualTo(3);
        assertThat(result.globalStats().ok()).isEqualTo(2);
        assertThat(result.globalStats().ko()).isEqualTo(1);
        assertThat(result.requestStats())
                .filteredOn(stats -> stats.name().equals("GET /api/orders"))
                .singleElement()
                .satisfies(stats -> {
                    assertThat(stats.total()).isEqualTo(2);
                    assertThat(stats.ko()).isEqualTo(1);
                    assertThat(stats.p95Ms()).isEqualTo(600);
                });
        assertThat(result.warnings())
                .extracting(ReportWarning::code)
                .contains("log.simulationlog.unstable");
        assertThat(result.findings())
                .extracting(ReportFinding::code)
                .contains("report.request.failures", "report.p95.high");
    }

    @Test
    void detectsRuntimeLogFailurePatterns() {
        var result = analyzer.analyzeLogContent("""
                12:00:01.001 ERROR i.g.h.e.r.DefaultStatsProcessor - java.net.ConnectException: Connection refused
                12:00:02.001 WARN  i.g.c.f.BatchableFeederSource - Feeder is now empty, stopping engine
                12:00:03.001 ERROR i.g.h.c.HttpClient - javax.net.ssl.SSLHandshakeException: PKIX path building failed
                12:00:04.001 ERROR i.g.h.c.HttpClient - io.netty.handler.timeout.ReadTimeoutException
                """, new LogAnalysisOptions("RUNTIME_LOG", 1.0, 1_000, 2_000));

        assertThat(result.sourceType()).isEqualTo("RUNTIME_LOG");
        assertThat(result.findings())
                .extracting(ReportFinding::code)
                .contains(
                        "log.connection.refused",
                        "log.feeder.empty",
                        "log.tls.handshake",
                        "log.timeout"
                );
        assertThat(result.globalStats().total()).isZero();
    }

    private static String statsJs() {
        return "var stats = " + statsJson() + ";";
    }

    private static String statsJson() {
        return """
                {
                  "type": "GROUP",
                  "name": "Global Information",
                  "stats": {
                    "numberOfRequests": {"total": "100", "ok": "97", "ko": "3"},
                    "minResponseTime": {"total": "10"},
                    "maxResponseTime": {"total": "2100"},
                    "meanResponseTime": {"total": "220"},
                    "percentiles1": {"total": "80"},
                    "percentiles2": {"total": "160"},
                    "percentiles3": {"total": "1200"},
                    "percentiles4": {"total": "2000"},
                    "meanNumberOfRequestsPerSecond": {"total": "16.6"}
                  },
                  "contents": {
                    "req_orders": {
                      "type": "REQUEST",
                      "name": "GET /api/orders",
                      "stats": {
                        "numberOfRequests": {"total": "70", "ok": "69", "ko": "1"},
                        "minResponseTime": {"total": "20"},
                        "maxResponseTime": {"total": "2100"},
                        "meanResponseTime": {"total": "300"},
                        "percentiles1": {"total": "100"},
                        "percentiles2": {"total": "250"},
                        "percentiles3": {"total": "1450"},
                        "percentiles4": {"total": "2100"},
                        "meanNumberOfRequestsPerSecond": {"total": "11.6"}
                      }
                    },
                    "req_login": {
                      "type": "REQUEST",
                      "name": "POST /login",
                      "stats": {
                        "numberOfRequests": {"total": "30", "ok": "28", "ko": "2"},
                        "minResponseTime": {"total": "10"},
                        "maxResponseTime": {"total": "1300"},
                        "meanResponseTime": {"total": "120"},
                        "percentiles1": {"total": "70"},
                        "percentiles2": {"total": "110"},
                        "percentiles3": {"total": "900"},
                        "percentiles4": {"total": "1300"},
                        "meanNumberOfRequestsPerSecond": {"total": "5.0"}
                      }
                    }
                  }
                }
                """;
    }
}
