package com.careconnect.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Pins the contract every deployed secret depends on.
 *
 * In production, secrets are not environment variables — they are files mounted
 * at /run/secrets/, one per value, by Docker Compose today and by a Kubernetes
 * Secret volume later. Nothing in the application knows that: the services still
 * write ${JWT_SECRET}, and Spring's `configtree` import is what turns the file
 * named JWT_SECRET into the property that placeholder resolves against.
 *
 * That mapping — filename verbatim to property name, no case-folding, no
 * separator rewriting — is a framework behaviour we rely on but do not own, and
 * the naming convention it dictates (SCREAMING_SNAKE filenames rather than
 * dotted property paths) is not obvious from the compose file alone. Hence a
 * test: if a Boot upgrade ever changed it, the symptom would be every service
 * silently falling back to its committed development default.
 *
 * "Silently" is doing less work than it looks: InsecureDefaultsGuard refuses to
 * start on exactly those published defaults, so the real-world failure is loud
 * and fail-closed. This test just makes it loud *here* instead, in half a
 * second, rather than on a VM.
 */
class FileSecretsConfigTreeTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Test
    @DisplayName("a file named JWT_SECRET resolves as ${JWT_SECRET}")
    void fileNameBecomesThePropertyName(@TempDir Path secrets) throws IOException {
        Files.writeString(secrets.resolve("JWT_SECRET"), "a-real-secret-from-a-file");
        Files.writeString(secrets.resolve("POSTGRES_PASSWORD"), "a-real-password");

        try (ConfigurableApplicationContext context = run(secrets)) {
            Environment env = context.getEnvironment();

            assertThat(env.getProperty("JWT_SECRET")).isEqualTo("a-real-secret-from-a-file");
            assertThat(env.getProperty("POSTGRES_PASSWORD")).isEqualTo("a-real-password");
            // The form the services actually use: a placeholder with a fallback.
            // This resolving to the file, not the fallback, is the whole point.
            assertThat(env.resolvePlaceholders("${JWT_SECRET:committed-dev-default}"))
                .isEqualTo("a-real-secret-from-a-file");
        }
    }

    @Test
    @DisplayName("trailing whitespace in a secret file is trimmed")
    void secretsAreTrimmed(@TempDir Path secrets) throws IOException {
        // `echo secret > file` appends a newline, and that is how these files get
        // written by hand on a VM roughly every time. A shared secret compared
        // byte-for-byte against the gateway's copy would not survive it.
        Files.writeString(secrets.resolve("GATEWAY_SHARED_SECRET"), "shared-secret\n");

        try (ConfigurableApplicationContext context = run(secrets)) {
            assertThat(context.getEnvironment().getProperty("GATEWAY_SHARED_SECRET"))
                .isEqualTo("shared-secret");
        }
    }

    @Test
    @DisplayName("a missing secrets directory is not an error")
    void missingDirectoryIsOptional(@TempDir Path parent) {
        // Dev machines, CI and `mvn spring-boot:run` have no /run/secrets. The
        // `optional:` prefix in every service's application.yml is what keeps
        // them starting, so it is worth asserting rather than assuming.
        Path absent = parent.resolve("does-not-exist");

        try (ConfigurableApplicationContext context = run(absent)) {
            assertThat(context.getEnvironment().getProperty("JWT_SECRET")).isNull();
            assertThat(context.getEnvironment().resolvePlaceholders("${JWT_SECRET:fallback}"))
                .isEqualTo("fallback");
        }
    }

    private ConfigurableApplicationContext run(Path secrets) {
        // Trailing separator is required: configtree points at a directory.
        String location = secrets.toAbsolutePath().toString().replace('\\', '/') + "/";
        return new SpringApplicationBuilder(TestApp.class)
            .web(WebApplicationType.NONE)
            .properties("spring.config.import=optional:configtree:" + location)
            .run();
    }
}
