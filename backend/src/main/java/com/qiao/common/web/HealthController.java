package com.qiao.common.web;

import com.qiao.common.api.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /api/health} → {@code {status, version, commit}}: database reachability plus the build that is running,
 * {@code version} from the Maven project ({@code META-INF/build-info.properties}) and {@code commit} from
 * {@code APP_COMMIT} (a Docker build argument set by CI and {@code deploy/update.sh}); both {@code unknown} when absent.
 */
@RestController
public class HealthController {

    static final String UNKNOWN = "unknown";

    private final JdbcTemplate jdbcTemplate;
    private final String version;
    private final String commit;

    public HealthController(JdbcTemplate jdbcTemplate, ObjectProvider<BuildProperties> buildProperties,
                            @Value("${app.commit:unknown}") String commit) {
        this.jdbcTemplate = jdbcTemplate;
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build == null || build.getVersion() == null ? UNKNOWN : build.getVersion();
        this.commit = commit == null || commit.isBlank() ? UNKNOWN : commit.trim();
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(body("UP")));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("UNAVAILABLE", "Database unavailable", body("DOWN")));
        }
    }

    private Map<String, String> body(String status) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("version", version);
        body.put("commit", commit);
        return body;
    }
}
