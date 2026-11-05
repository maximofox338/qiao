package com.qiao.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiao.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthIT extends IntegrationTest {

    @Test
    void healthShowsTheRunningBuild() {
        JsonNode health = get("/api/health", null).ok();
        assertThat(health.path("status").asText()).isEqualTo("UP");
        // Maven project version from META-INF/build-info.properties (spring-boot-maven-plugin build-info goal)
        assertThat(health.path("version").asText()).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
        // APP_COMMIT (application-test.yml sets app.commit as the Docker build argument would)
        assertThat(health.path("commit").asText()).isEqualTo("test-commit-0123abc");
    }
}
