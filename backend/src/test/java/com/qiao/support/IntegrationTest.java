package com.qiao.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for MockMvc integration tests against MySQL (Testcontainers). Provides HTTP helpers and the common
 * onboarding steps so each test reads as a business scenario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@test.qiao";
    protected static final String ADMIN_PASSWORD = "TestAdmin-password-1";
    protected static final String PARTNER_KEY = "test-partner-key";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // ------------------------------------------------------------------ HTTP

    public record Response(int status, JsonNode body, MockHttpServletResponse raw) {

        public JsonNode data() {
            return body.path("data");
        }

        public String code() {
            return body.path("code").asText();
        }

        public Response expect(int expectedStatus, String expectedCode) {
            assertThat(code()).as("code of %s", body).isEqualTo(expectedCode);
            assertThat(status).as("status of %s", body).isEqualTo(expectedStatus);
            return this;
        }

        public JsonNode ok() {
            return expect(200, "OK").data();
        }
    }

    protected Response get(String path, String token) {
        return send(MockMvcRequestBuilders.get(path), token, null);
    }

    protected Response post(String path, String token, Object body) {
        return send(MockMvcRequestBuilders.post(path), token, body);
    }

    protected Response put(String path, String token, Object body) {
        return send(MockMvcRequestBuilders.put(path), token, body);
    }

    protected Response send(MockHttpServletRequestBuilder request, String token, Object body) {
        try {
            if (token != null) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
            }
            MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
            String content = response.getContentAsString(StandardCharsets.UTF_8);
            JsonNode json = content.isBlank() || !content.trim().startsWith("{")
                    ? objectMapper.createObjectNode() : objectMapper.readTree(content);
            return new Response(response.getStatus(), json, response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Runs {@code call} {@code count} times at once (released together) and returns the results.
     */
    protected static <T> List<T> parallel(int count, Supplier<T> call) {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.get();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(2, TimeUnit.MINUTES));
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ onboarding

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.qiao";
    }

    protected String lastOtp(String email) {
        return get("/api/dev/last-otp?email=" + email, null).ok().path("code").asText();
    }

    protected String requestCode(String email) {
        post("/api/auth/otp/request", null, Map.of("email", email, "locale", "en")).ok();
        return lastOtp(email);
    }

    protected String register(String email, String role) {
        String code = requestCode(email);
        return post("/api/auth/otp/verify", null, Map.of("email", email, "code", code, "role", role,
                "acceptTerms", true, "termsVersion", "2026-11-01", "privacyVersion", "2026-11-01"))
                .ok().path("token").asText();
    }

    protected String adminToken() {
        return post("/api/admin/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .ok().path("token").asText();
    }

    protected void grantConsent(String token, String type) {
        post("/api/me/consents", token, Map.of("type", type, "granted", true)).ok();
    }

    /**
     * Registers a client, fills the company, accepts the declarations and has an admin approve it.
     */
    protected String approvedClient() {
        String token = register(uniqueEmail("client"), "CLIENT");
        put("/api/client/company", token, Map.of("name", "Acme Robotics", "country", "US",
                "website", "https://acme.example", "size", "11-50")).ok();
        String version = get("/api/public/legal/client-declarations", null).ok().path("version").asText();
        JsonNode company = post("/api/client/company/declarations", token,
                Map.of("version", version, "accepted", true)).ok();
        post("/api/admin/companies/" + company.path("id").asLong() + "/review", adminToken(),
                Map.of("decision", "APPROVE", "sanctionsStatus", "CLEAR", "note", "ok")).ok();
        return token;
    }

    /**
     * Registers an engineer with a complete profile, all consents and an approved review.
     */
    protected String approvedEngineer() {
        return approvedEngineer(profileBody());
    }

    /**
     * Same as {@link #approvedEngineer()} with a custom profile body.
     */
    protected String approvedEngineer(Map<String, Object> profile) {
        String token = register(uniqueEmail("engineer"), "ENGINEER");
        put("/api/engineer/profile", token, profile).ok();
        grantConsent(token, "CROSS_BORDER_PROCESSING");
        grantConsent(token, "SHARE_PROFILE_PSEUDONYMIZED");
        long profileId = post("/api/engineer/profile/submit", token, null).ok().path("id").asLong();
        post("/api/admin/engineers/" + profileId + "/review", adminToken(),
                Map.of("decision", "APPROVE", "note", "ok", "partnerRef", "PR-" + profileId)).ok();
        return token;
    }

    protected static Map<String, Object> profileBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headline", "Full-stack engineer");
        body.put("bio", "Ten years building web products.");
        body.put("skills", List.of("Vue", "Java"));
        body.put("yearsExperience", 10);
        body.put("region", "Zhejiang");
        body.put("englishLevel", "GOOD");
        body.put("spanishLevel", "NONE");
        body.put("hourlyRateUsd", 40);
        body.put("availabilityHoursWeek", 30);
        body.put("cvText", "Senior engineer at a SaaS company in Hangzhou.");
        body.put("education", "BSc Computer Science");
        body.put("portfolioLinks", List.of("https://portfolio.example/me"));
        body.put("declarationNoRestrictions", true);
        return body;
    }

    protected static Map<String, Object> projectBody(String title, String category, BigDecimal budget,
                                                     LocalDate start, LocalDate end,
                                                     List<Map<String, Object>> milestones, boolean submit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "Build the " + title + " with tests and documentation.");
        body.put("categoryCode", category);
        body.put("skills", List.of("Vue", "Spring Boot"));
        body.put("budgetUsd", budget);
        body.put("startDate", start.toString());
        body.put("endDate", end == null ? null : end.toString());
        body.put("milestones", milestones);
        body.put("declarations", Map.of("noControlledTech", true, "noBulkUsSensitiveData", true,
                "noEmployment", true, "noProhibitedCategory", true));
        body.put("submit", submit);
        return body;
    }

    protected static Map<String, Object> milestone(String title, LocalDate due, String amount) {
        return Map.of("title", title, "deliverable", title + " delivered and documented",
                "dueDate", due.toString(), "amountUsd", new BigDecimal(amount));
    }

    /**
     * A submitted project with two milestones (1000 + 1500 USD), approved by an admin (OPEN).
     */
    protected long openProject(String clientToken) {
        LocalDate start = LocalDate.now().plusDays(5);
        Map<String, Object> body = projectBody("Inventory dashboard", "web-dev", new BigDecimal("2500.00"), start,
                start.plusMonths(2), List.of(milestone("Backend API", start.plusMonths(1), "1000.00"),
                        milestone("Front end", start.plusMonths(2), "1500.00")), true);
        long projectId = post("/api/client/projects", clientToken, body).ok().path("id").asLong();
        post("/api/admin/projects/" + projectId + "/review", adminToken(), Map.of("decision", "APPROVE")).ok();
        return projectId;
    }
}
