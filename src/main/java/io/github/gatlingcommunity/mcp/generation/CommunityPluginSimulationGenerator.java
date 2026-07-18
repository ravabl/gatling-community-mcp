package io.github.gatlingcommunity.mcp.generation;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import io.github.gatlingcommunity.mcp.core.model.CommunityPlugin;
import io.github.gatlingcommunity.mcp.core.model.DslLanguage;

public final class CommunityPluginSimulationGenerator {
    public String generate(GenerationRequest request) {
        var context = request.target().pluginContext().orElseThrow();
        var body = switch (context.plugin()) {
            case KAFKA -> kafka(request.target().language(), request.simulationClassName());
            case JDBC -> jdbc(request.target().language(), request.simulationClassName());
            case AMQP -> amqp(request.target().language(), request.simulationClassName());
            case PICATINNY -> picatinny(request.target().language(), request.simulationClassName());
        };
        return dependencyHint(request.target().buildTool(), context.plugin(), context.pluginVersion()) + body;
    }

    private static String dependencyHint(BuildTool buildTool, CommunityPlugin plugin, String version) {
        var artifact = switch (plugin) {
            case KAFKA -> "gatling-kafka-plugin_2.13";
            case JDBC -> "gatling-jdbc-plugin_2.13";
            case AMQP -> "gatling-amqp-plugin_2.13";
            case PICATINNY -> "gatling-picatinny_2.13";
        };
        return switch (buildTool) {
            case MAVEN -> "// Maven test dependency: org.galaxio:%s:%s%n".formatted(artifact, version);
            case GRADLE -> "// Gradle gatling dependency: org.galaxio:%s:%s%n".formatted(artifact, version);
            case SBT -> "// sbt Test dependency: org.galaxio %% %s %s%n".formatted(
                    artifact.replace("_2.13", ""), version);
            case NPM, UNKNOWN -> "";
        };
    }

    private static String kafka(DslLanguage language, String name) {
        return switch (language) {
            case JAVA -> """
                    import static io.gatling.javaapi.core.CoreDsl.*;
                    import static org.galaxio.gatling.kafka.javaapi.KafkaDsl.*;
                    import io.gatling.javaapi.core.Simulation;
                    import java.time.Duration;
                    import java.util.Map;

                    public class %s extends Simulation {
                      {
                        var kafkaProtocol = kafka()
                          .producerSettings(Map.of("bootstrap.servers", System.getProperty("kafkaUrl")))
                          .consumeSettings(Map.of("bootstrap.servers", System.getProperty("kafkaUrl")))
                          .timeout(Duration.ofSeconds(15));
                        var scn = scenario("Kafka request-reply")
                          .exec(kafka("send request").requestReply()
                            .requestTopic("#{requestTopic}")
                            .replyTopic("#{replyTopic}")
                            .send("#{key}", "#{payload}")
                            .check(jsonPath("$.status").is("ok")));
                        setUp(scn.injectOpen(atOnceUsers(1))).protocols(kafkaProtocol);
                      }
                    }
                    """.formatted(name);
            case KOTLIN -> """
                    import io.gatling.javaapi.core.CoreDsl.*
                    import io.gatling.javaapi.core.Simulation
                    import org.galaxio.gatling.kafka.javaapi.KafkaDsl.*
                    import java.time.Duration

                    class %s : Simulation() {
                      private val kafkaProtocol = kafka()
                        .producerSettings(mapOf<String, Any>("bootstrap.servers" to System.getProperty("kafkaUrl")))
                        .consumeSettings(mapOf<String, Any>("bootstrap.servers" to System.getProperty("kafkaUrl")))
                        .timeout(Duration.ofSeconds(15))

                      private val scn = scenario("Kafka request-reply")
                        .exec(kafka("send request").requestReply()
                          .requestTopic("#{requestTopic}")
                          .replyTopic("#{replyTopic}")
                          .send("#{key}", "#{payload}")
                          .check(jsonPath("$.status").`is`("ok")))

                      init { setUp(scn.injectOpen(atOnceUsers(1))).protocols(kafkaProtocol) }
                    }
                    """.formatted(name);
            case SCALA -> """
                    import io.gatling.core.Predef._
                    import org.galaxio.gatling.kafka.Predef._
                    import scala.concurrent.duration._

                    class %s extends Simulation {
                      val kafkaProtocol = kafka
                        .producerSettings("bootstrap.servers" -> System.getProperty("kafkaUrl"))
                        .consumeSettings("bootstrap.servers" -> System.getProperty("kafkaUrl"))
                        .timeout(15.seconds)

                      val scn = scenario("Kafka request-reply")
                        .exec(kafka("send request").requestReply
                          .requestTopic("#{requestTopic}")
                          .replyTopic("#{replyTopic}")
                          .send[String, String]("#{key}", "#{payload}")
                          .check(jsonPath("$.status").is("ok")))

                      setUp(scn.inject(atOnceUsers(1))).protocols(kafkaProtocol)
                    }
                    """.formatted(name);
            case JAVASCRIPT, TYPESCRIPT -> throw new IllegalArgumentException("Community plugins are JVM-only");
        };
    }

    private static String jdbc(DslLanguage language, String name) {
        return switch (language) {
            case JAVA -> """
                    import static io.gatling.javaapi.core.CoreDsl.*;
                    import static org.galaxio.gatling.javaapi.JdbcDsl.*;
                    import io.gatling.javaapi.core.Simulation;
                    import java.util.Map;

                    public class %s extends Simulation {
                      {
                        var jdbcProtocol = DB()
                          .url(System.getProperty("dbUrl"))
                          .username(System.getProperty("dbUser"))
                          .password(System.getProperty("dbPassword"))
                          .maximumPoolSize(10)
                          .protocolBuilder();
                        var scn = scenario("JDBC query")
                          .exec(jdbc("select account")
                            .queryP("SELECT * FROM accounts WHERE id = {id}")
                            .params(Map.of("id", "#{accountId}"))
                            .check(simpleCheck(simpleCheckType.NonEmpty)));
                        setUp(scn.injectOpen(atOnceUsers(1))).protocols(jdbcProtocol);
                      }
                    }
                    """.formatted(name);
            case KOTLIN -> """
                    import io.gatling.javaapi.core.CoreDsl.*
                    import io.gatling.javaapi.core.Simulation
                    import org.galaxio.gatling.javaapi.JdbcDsl.*

                    class %s : Simulation() {
                      private val jdbcProtocol = DB()
                        .url(System.getProperty("dbUrl"))
                        .username(System.getProperty("dbUser"))
                        .password(System.getProperty("dbPassword"))
                        .maximumPoolSize(10)
                        .protocolBuilder()

                      private val scn = scenario("JDBC query")
                        .exec(jdbc("select account")
                          .queryP("SELECT * FROM accounts WHERE id = {id}")
                          .params(mapOf("id" to "#{accountId}"))
                          .check(simpleCheck(simpleCheckType.NonEmpty)))

                      init { setUp(scn.injectOpen(atOnceUsers(1))).protocols(jdbcProtocol) }
                    }
                    """.formatted(name);
            case SCALA -> """
                    import io.gatling.core.Predef._
                    import org.galaxio.gatling.jdbc.Predef._

                    class %s extends Simulation {
                      val jdbcProtocol = DB
                        .url(System.getProperty("dbUrl"))
                        .username(System.getProperty("dbUser"))
                        .password(System.getProperty("dbPassword"))
                        .maximumPoolSize(10)

                      val scn = scenario("JDBC query")
                        .exec(jdbc("select account")
                          .queryP("SELECT * FROM accounts WHERE id = {id}")
                          .params("id" -> "#{accountId}")
                          .check(simpleCheck(_.nonEmpty)))

                      setUp(scn.inject(atOnceUsers(1))).protocols(jdbcProtocol)
                    }
                    """.formatted(name);
            case JAVASCRIPT, TYPESCRIPT -> throw new IllegalArgumentException("Community plugins are JVM-only");
        };
    }

    private static String amqp(DslLanguage language, String name) {
        return switch (language) {
            case JAVA -> """
                    import static io.gatling.javaapi.core.CoreDsl.*;
                    import static org.galaxio.gatling.amqp.javaapi.AmqpDsl.*;
                    import io.gatling.javaapi.core.Simulation;

                    public class %s extends Simulation {
                      {
                        var amqpProtocol = amqp()
                          .connectionFactory(rabbitmq().host(System.getProperty("amqpHost"))
                            .port(Integer.getInteger("amqpPort", 5672)).build())
                          .replyTimeout(60000L)
                          .consumerThreadsCount(8)
                          .matchByMessageId();
                        var scn = scenario("AMQP request-reply")
                          .exec(amqp("rpc call").requestReply()
                            .queueExchange("#{requestQueue}")
                            .replyExchange("#{replyQueue}")
                            .textMessage("#{payload}")
                            .messageId("#{messageId}")
                            .check(bodyString().exists()));
                        setUp(scn.injectOpen(atOnceUsers(1))).protocols(amqpProtocol);
                      }
                    }
                    """.formatted(name);
            case KOTLIN -> """
                    import io.gatling.javaapi.core.CoreDsl.*
                    import io.gatling.javaapi.core.Simulation
                    import org.galaxio.gatling.amqp.javaapi.AmqpDsl.*

                    class %s : Simulation() {
                      private val amqpProtocol = amqp()
                        .connectionFactory(rabbitmq().host(System.getProperty("amqpHost"))
                          .port(Integer.getInteger("amqpPort", 5672)).build())
                        .replyTimeout(60000L)
                        .consumerThreadsCount(8)
                        .matchByMessageId()

                      private val scn = scenario("AMQP request-reply")
                        .exec(amqp("rpc call").requestReply()
                          .queueExchange("#{requestQueue}")
                          .replyExchange("#{replyQueue}")
                          .textMessage("#{payload}")
                          .messageId("#{messageId}")
                          .check(bodyString().exists()))

                      init { setUp(scn.injectOpen(atOnceUsers(1))).protocols(amqpProtocol) }
                    }
                    """.formatted(name);
            case SCALA -> """
                    import io.gatling.core.Predef._
                    import org.galaxio.gatling.amqp.Predef._

                    class %s extends Simulation {
                      val amqpProtocol = amqp
                        .connectionFactory(rabbitmq.host(System.getProperty("amqpHost"))
                          .port(Integer.getInteger("amqpPort", 5672)))
                        .replyTimeout(60000)
                        .consumerThreadsCount(8)
                        .matchByMessageId

                      val scn = scenario("AMQP request-reply")
                        .exec(amqp("rpc call").requestReply
                          .queueExchange("#{requestQueue}")
                          .replyExchange("#{replyQueue}")
                          .textMessage("#{payload}")
                          .messageId("#{messageId}")
                          .check(bodyString.exists))

                      setUp(scn.inject(atOnceUsers(1))).protocols(amqpProtocol)
                    }
                    """.formatted(name);
            case JAVASCRIPT, TYPESCRIPT -> throw new IllegalArgumentException("Community plugins are JVM-only");
        };
    }

    private static String picatinny(DslLanguage language, String name) {
        return switch (language) {
            case JAVA -> """
                    import static io.gatling.javaapi.core.CoreDsl.*;
                    import static io.gatling.javaapi.http.HttpDsl.*;
                    import static org.galaxio.gatling.javaapi.Feeders.*;
                    import static org.galaxio.gatling.javaapi.Transactions.*;
                    import io.gatling.javaapi.core.Simulation;
                    import java.util.Iterator;
                    import java.util.Map;

                    public class %s extends Simulation {
                      {
                        Iterator<Map<String, Object>> messageIds = RandomUUIDFeeder("messageId");
                        var httpProtocol = http.baseUrl(System.getProperty("baseUrl"));
                        var scn = scenario("Picatinny HTTP")
                          .feed(messageIds)
                          .exec(startTransaction("home"))
                          .exec(http("GET /").get("/").check(status().is(200)))
                          .exec(endTransaction("home"));
                        setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol)
                          .assertions(global().failedRequests().percent().lt(1.0));
                      }
                    }
                    """.formatted(name);
            case KOTLIN -> """
                    import io.gatling.javaapi.core.CoreDsl.*
                    import io.gatling.javaapi.core.Simulation
                    import io.gatling.javaapi.http.HttpDsl.*
                    import org.galaxio.gatling.javaapi.Feeders.*
                    import org.galaxio.gatling.javaapi.Transactions.*

                    class %s : Simulation() {
                      private val messageIds = RandomUUIDFeeder("messageId")
                      private val httpProtocol = http.baseUrl(System.getProperty("baseUrl"))
                      private val scn = scenario("Picatinny HTTP")
                        .feed(messageIds)
                        .exec(startTransaction("home"))
                        .exec(http("GET /").get("/").check(status().`is`(200)))
                        .exec(endTransaction("home"))

                      init { setUp(scn.injectOpen(atOnceUsers(1))).protocols(httpProtocol)
                        .assertions(global().failedRequests().percent().lt(1.0)) }
                    }
                    """.formatted(name);
            case SCALA -> """
                    import io.gatling.core.Predef._
                    import io.gatling.http.Predef._
                    import org.galaxio.gatling.feeders._
                    import org.galaxio.gatling.transactions.Predef._

                    class %s extends Simulation {
                      val messageIds = RandomUUIDFeeder("messageId")
                      val httpProtocol = http.baseUrl(System.getProperty("baseUrl"))
                      val scn = scenario("Picatinny HTTP")
                        .feed(messageIds)
                        .startTransaction("home")
                        .exec(http("GET /").get("/").check(status.is(200)))
                        .endTransaction("home")

                      setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
                        .assertions(global.failedRequests.percent.lt(1.0))
                    }
                    """.formatted(name);
            case JAVASCRIPT, TYPESCRIPT -> throw new IllegalArgumentException("Community plugins are JVM-only");
        };
    }
}
