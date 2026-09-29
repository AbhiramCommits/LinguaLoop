package com.lingualoop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.ExerciseType;
import com.lingualoop.api.content.Language;
import com.lingualoop.api.content.LanguageRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.content.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    LanguageRepository languages;

    @Autowired
    UnitRepository units;

    @Autowired
    LessonRepository lessons;

    @Autowired
    ExerciseRepository exercises;

    private Long unitId;
    private Long lessonId;
    private Long exerciseId;
    private String token;

    private static final AtomicInteger languageCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        Language language = languages.save(new Language("t" + languageCounter.getAndIncrement(), "Spanish"));
        Unit unit = units.save(new Unit(language, "Saludos", 1));
        Lesson lesson = lessons.save(new Lesson(unit, "Saludos básicos", 1));
        Exercise exercise = exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "Good morning", "Buenos días"));
        unitId = unit.getId();
        lessonId = lesson.getId();
        exerciseId = exercise.getId();
        token = registerAndLogin();
    }

    @Test
    void publicCatalogueIsBrowsable() {
        ResponseEntity<List> languagesResponse = rest.getForEntity("/api/languages", List.class);
        assertThat(languagesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(languagesResponse.getBody()).isNotEmpty();

        ResponseEntity<Map> unitResponse = rest.getForEntity("/api/units/" + unitId, Map.class);
        assertThat(unitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unitResponse.getBody().get("title")).isEqualTo("Saludos");

        ResponseEntity<Map> lessonResponse = rest.getForEntity("/api/lessons/" + lessonId, Map.class);
        assertThat(lessonResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) lessonResponse.getBody().get("exercises")).hasSize(1);
    }

    @Test
    void protectedEndpointsRequireAuth() {
        assertThat(rest.getForEntity("/api/learners/me/queue", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fullStudyLoopWorks() {
        var session = startSession();

        var attempt = submitAttempt(session.get("id"), exerciseId, 5);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attempt.getBody().get("grade")).isEqualTo(5);
        Map<String, Object> review = (Map<String, Object>) attempt.getBody().get("review");
        assertThat(review.get("repetitions")).isEqualTo(1);
        assertThat(review.get("intervalDays")).isEqualTo(1.0);

        var queue = authedGet("/api/learners/me/queue");
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) queue.getBody().get("items")).isEmpty();

        var stats = authedGet("/api/learners/me/stats");
        assertThat(stats.getBody().get("attemptsTotal")).isEqualTo(1);
        assertThat(stats.getBody().get("exercisesStudied")).isEqualTo(1);

        ResponseEntity<Map> completed = rest.exchange(
                "/api/sessions/" + session.get("id") + "/complete", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), Map.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("attemptCount")).isEqualTo(1);

        var afterComplete = submitAttempt(session.get("id"), exerciseId, 4);
        assertThat(afterComplete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedAttemptBecomesDueImmediately() {
        var session = startSession();
        var attempt = submitAttempt(session.get("id"), exerciseId, 1);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.OK);

        var queue = authedGet("/api/learners/me/queue");
        assertThat((List<?>) queue.getBody().get("items")).hasSize(1);
    }

    @Test
    void validationErrorsReturnProblemDetails() {
        var session = startSession();
        var response = rest.exchange("/api/sessions/" + session.get("id") + "/attempts", HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("exerciseId", exerciseId, "grade", 6, "latencyMs", 1200), authHeaders()),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("type")).asString().contains("validation");
    }

    @Test
    void missingResourceReturnsProblemDetails() {
        var response = authedGet("/api/lessons/999999");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }

    @Test
    void swaggerAndApiDocsAreExposed() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicateRegistrationIsRejected() {
        var payload = Map.of("email", "dup@example.com", "password", "password123", "displayName", "Dup");
        assertThat(rest.postForEntity("/api/auth/register", payload, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var second = rest.postForEntity("/api/auth/register", payload, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }

    @Test
    void loginRejectsWrongPassword() {
        var response = rest.postForEntity("/api/auth/login",
                Map.of("email", "nobody@example.com", "password", "nope-nope-nope"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String registerAndLogin() {
        String email = "learner-" + System.nanoTime() + "@example.com";
        rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123", "displayName", "Test Learner", "timezone",
                        "Europe/Berlin"),
                Map.class);
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "password123"), Map.class);
        return (String) login.getBody().get("token");
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<Map> authedGet(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
    }

    private Map startSession() {
        ResponseEntity<Map> response = rest.postForEntity("/api/sessions",
                new HttpEntity<>(Map.of("lessonId", lessonId), authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Map> submitAttempt(Object sessionId, long exerciseId, int grade) {
        return rest.postForEntity("/api/sessions/" + sessionId + "/attempts",
                new HttpEntity<>(Map.of("exerciseId", exerciseId, "grade", grade, "latencyMs", 1500, "hintShown",
                        false), authHeaders()),
                Map.class);
    }
}
