package com.westfield.api.billing.edge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEP-001 — the dead messaging dependency is not carried into the target (N-0103, ADR-0040, ADR-0009).
 *
 * <p>The legacy declares the {@code ibm-mq} namespace and the {@code com.ibm.mq.allclient} shared
 * library and uses neither: no listener, no publisher, no broker connection, no transaction. The
 * knowledge map flags N-0103 {@code dead_code: true} and, because it is not a behaviour, no capability
 * claims it — which is exactly how a dependency survives a migration. It gets copied because it was
 * there, and then it is on the classpath of a new system nobody chose to put it in.
 *
 * <p>ADR-0040 asks for this to be verified from the artifact rather than asserted in a document, so
 * that is what this does. Two of the four criteria assert an absence in the built dependency set; the
 * other two assert that there is nothing to reproduce — no delivery guarantee, no transaction — which
 * is the fact that makes the removal safe rather than merely tidy.
 *
 * <p>The one thing that could invalidate this is R-015: an unknown deployment model whose platform
 * convention requires the MQ client on the classpath. Nothing in the source suggests it, and if it
 * turns out to be true it is a packaging decision, not a resurrection of the behaviour.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("DEP-001 dependency surface")
class DependencySurfaceTest {

    /** Every artifact that would put a message broker client on the runtime classpath. */
    private static final List<String> MESSAGING_ARTIFACTS = List.of(
            "com.ibm.mq", "mq-jms-spring", "javax.jms", "jakarta.jms", "activemq", "artemis",
            "rabbitmq", "spring-jms", "spring-rabbit", "spring-kafka", "kafka-clients", "pulsar");

    @Test // DEP-001-a
    @DisplayName("a: the built dependency set contains no IBM MQ client and no JMS client")
    void noMessagingClientOnTheDependencySet() {
        String pom = read(Path.of("pom.xml")).toLowerCase(Locale.ROOT);

        List<String> found = new ArrayList<>();
        for (String artifact : MESSAGING_ARTIFACTS) {
            if (pom.contains(artifact)) {
                found.add(artifact);
            }
        }
        assertThat(found)
                .as("the legacy's dead ibm-mq namespace and com.ibm.mq.allclient shared library are "
                        + "not carried forward (ADR-0040); found %s", found)
                .isEmpty();

        // The same check against the resolved runtime classpath, which is what actually ships and
        // what a transitive dependency would sneak in through. Reading the pom alone would miss that.
        List<String> onClasspath = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString().toLowerCase(Locale.ROOT);
            for (String artifact : MESSAGING_ARTIFACTS) {
                if (name.contains(artifact.replace('.', '-')) || name.contains(artifact)) {
                    onClasspath.add(name);
                }
            }
        }
        assertThat(onClasspath)
                .as("no messaging client may reach the runtime classpath, transitively or otherwise")
                .isEmpty();
    }

    @Test // DEP-001-b
    @DisplayName("b: the service declares no message listener, producer or broker connection")
    void noMessagingConfigurationIsDeclared() {
        // The Java equivalent of the legacy's unused <ibm-mq:config>. If someone later adds a
        // @JmsListener because "we might need it", this fails and the decision has to be taken
        // deliberately rather than arrived at.
        List<String> offenders = new ArrayList<>();
        for (Path source : sources(Path.of("src", "main", "java"))) {
            String text = read(source);
            for (String idiom : List.of("@JmsListener", "@RabbitListener", "@KafkaListener",
                    "JmsTemplate", "ConnectionFactory", "MessageProducer", "MessageConsumer")) {
                if (text.contains(idiom)) {
                    offenders.add(source.getFileName() + " declares " + idiom);
                }
            }
        }
        assertThat(offenders).isEmpty();

        for (Path resource : sources(Path.of("src", "main", "resources"))) {
            String text = read(resource).toLowerCase(Locale.ROOT);
            assertThat(text)
                    .as("%s must configure no broker", resource.getFileName())
                    .doesNotContain("ibm-mq")
                    .doesNotContain("queue-manager")
                    .doesNotContain("broker-url");
        }
    }

    @Test // DEP-001-c
    @DisplayName("c: every backend interaction is a synchronous request and response, so there is no delivery guarantee to reproduce")
    void everyBackendInteractionIsSynchronous() {
        // ADR-0002: all eight operations are synchronous GETs and every backend interaction is a
        // synchronous request/response call. That is not an aspiration — it is the reason removing
        // the MQ dependency is safe. There is no queue, no broker and no at-least-once semantic
        // hiding behind the unused namespace.
        List<String> asynchronous = new ArrayList<>();
        for (Path source : sources(Path.of("src", "main", "java"))) {
            String text = read(source);
            for (String idiom : List.of("@Async", "CompletableFuture", "sendAsync", "Flux<", "Mono<",
                    "@Scheduled", "ExecutorService")) {
                if (text.contains(idiom)) {
                    asynchronous.add(source.getFileName() + " uses " + idiom);
                }
            }
        }
        assertThat(asynchronous)
                .as("the outbound calls are blocking request/response, exactly as today")
                .isEmpty();
    }

    @Test // DEP-001-d
    @DisplayName("d: no distributed or resource-spanning transaction is declared, because the legacy declares none")
    void noTransactionalMachineryIsDeclared() {
        // N-0103 ec and ADR-0009. A read-only API across two back ends has nothing to make atomic;
        // introducing a transaction manager would add a failure mode and a rollback semantic that
        // the legacy has no equivalent of, and there would be nothing to compare it against.
        List<String> offenders = new ArrayList<>();
        for (Path source : sources(Path.of("src", "main", "java"))) {
            String text = read(source);
            for (String idiom : List.of("@Transactional", "PlatformTransactionManager",
                    "JtaTransactionManager", "XADataSource", "@Retryable", "RetryTemplate")) {
                if (text.contains(idiom)) {
                    offenders.add(source.getFileName() + " declares " + idiom);
                }
            }
        }
        assertThat(offenders)
                .as("no transaction and no retry anywhere (ADR-0009): the legacy has neither, and "
                        + "adding either would change delivery semantics with nothing to verify against")
                .isEmpty();
    }

    private static List<Path> sources(Path root) {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
