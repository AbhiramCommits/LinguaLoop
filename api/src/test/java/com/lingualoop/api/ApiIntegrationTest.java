package com.lingualoop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import com.lingualoop.api.learner.Learner;
import com.lingualoop.api.learner.LearnerRepository;
import com.lingualoop.api.learner.ReviewState;
import com.lingualoop.api.learner.ReviewStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired
    LearnerRepository learners;

    @Autowired
    ReviewStateRepository reviewStates;

    @Autowired
    StringRedisTemplate redisTemplate;

    private Long languageId;
    private Long unitId;
    private Long lessonId;
    private Long exerciseId;
    private Long learnerId;
    private String email;
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
        languageId = language.getId();
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

        ResponseEntity<List> unitsByLanguage = rest.getForEntity("/api/languages/" + languageId + "/units", List.class);
        assertThat(unitsByLanguage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unitsByLanguage.getBody()).hasSize(1);

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

        var statsBefore = authedGet("/api/learners/me/stats");
        assertThat(statsBefore.getBody().get("attemptsTotal")).isEqualTo(1);
        assertThat(statsBefore.getBody().get("exercisesStudied")).isEqualTo(1);
        Map<String, Object> streakBefore = (Map<String, Object>) statsBefore.getBody().get("streak");
        assertThat(streakBefore.get("currentDays")).isEqualTo(0);

        ResponseEntity<Map> completed = rest.exchange(
                "/api/sessions/" + session.get("id") + "/complete", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), Map.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("attemptCount")).isEqualTo(1);

        var statsAfter = authedGet("/api/learners/me/stats");
        Map<String, Object> streakAfter = (Map<String, Object>) statsAfter.getBody().get("streak");
        assertThat(streakAfter.get("currentDays")).isEqualTo(1);
        assertThat(streakAfter.get("longestDays")).isEqualTo(1);

        var afterComplete = submitAttempt(session.get("id"), exerciseId, 4);
        assertThat(afterComplete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failedAttemptIsRescheduledForTomorrow() {
        var session = startSession();
        var attempt = submitAttempt(session.get("id"), exerciseId, 1);
        assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> review = (Map<String, Object>) attempt.getBody().get("review");
        assertThat(review.get("repetitions")).isEqualTo(0);
        assertThat(review.get("intervalDays")).isEqualTo(1.0);
        assertThat(review.get("lapses")).isEqualTo(1);

        var queue = authedGet("/api/learners/me/queue");
        assertThat(queue.getBody().get("dueCount")).isEqualTo(0);
        assertThat((List<?>) queue.getBody().get("items")).isEmpty();
    }

    @Test
    void queueBackfillsUnseenExercisesFromActiveUnitAndSurvivesColdRedis() {
        Language language = languages.save(new Language("t" + languageCounter.getAndIncrement(), "Spanish"));
        Unit unit = units.save(new Unit(language, "Unidad de repaso", 1));
        Lesson firstLesson = lessons.save(new Lesson(unit, "Lección 1", 1));
        Lesson secondLesson = lessons.save(new Lesson(unit, "Lección 2", 2));
        List<Long> exerciseIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            exerciseIds.add(exercises.save(new Exercise(firstLesson, ExerciseType.TRANSLATE, "p1-" + i, "a1-" + i))
                    .getId());
        }
        for (int i = 0; i < 3; i++) {
            exerciseIds.add(exercises.save(new Exercise(secondLesson, ExerciseType.TRANSLATE, "p2-" + i, "a2-" + i))
                    .getId());
        }

        Map session = startSessionFor(firstLesson.getId());
        long attemptedExercise = exerciseIds.get(0);
        assertThat(submitAttempt(session.get("id"), attemptedExercise, 5).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        flushRedis();

        var queue = authedGet("/api/learners/me/queue");
        assertThat(queue.getBody().get("dueCount")).isEqualTo(0);
        List<?> items = (List<?>) queue.getBody().get("items");
        assertThat(items).hasSize(5);
        for (Object item : items) {
            Map<String, Object> map = (Map<String, Object>) item;
            assertThat(map.get("new")).isEqualTo(true);
            assertThat(((Number) map.get("exerciseId")).longValue()).isNotEqualTo(attemptedExercise);
        }

        Long ttlSeconds = redisTemplate.getExpire("queue:" + learnerId, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isBetween(800L, 900L);

        flushRedis();
        var queueAfterSecondFlush = authedGet("/api/learners/me/queue");
        assertThat((List<?>) queueAfterSecondFlush.getBody().get("items")).hasSize(5);
        assertThat(queueAfterSecondFlush.getBody().get("dueCount")).isEqualTo(0);
    }

    @Test
    void queueIsCappedAtDailyLimit() {
        Language language = languages.save(new Language("t" + languageCounter.getAndIncrement(), "Spanish"));
        Unit unit = units.save(new Unit(language, "Unidad larga", 1));
        Lesson lesson = lessons.save(new Lesson(unit, "Lección larga", 1));
        for (int i = 0; i < 35; i++) {
            exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "prompt-" + i, "answer-" + i));
        }

        startSessionFor(lesson.getId());
        var queue = authedGet("/api/learners/me/queue");
        assertThat(queue.getBody().get("dueCount")).isEqualTo(0);
        assertThat((List<?>) queue.getBody().get("items")).hasSize(30);
    }

    @Test
    void statsReportLessonAndUnitMastery() {
        Language language = languages.save(new Language("t" + languageCounter.getAndIncrement(), "Spanish"));
        Unit unit = units.save(new Unit(language, "Unidad de maestría", 1));
        Lesson firstLesson = lessons.save(new Lesson(unit, "Lección A", 1));
        Lesson secondLesson = lessons.save(new Lesson(unit, "Lección B", 2));
        List<Exercise> lessonAExercises = new ArrayList<>();
        List<Exercise> lessonBExercises = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            lessonAExercises.add(exercises.save(new Exercise(firstLesson, ExerciseType.TRANSLATE, "a-" + i, "A-" + i)));
            lessonBExercises.add(exercises.save(new Exercise(secondLesson, ExerciseType.TRANSLATE, "b-" + i, "B-" + i)));
        }

        startSessionFor(firstLesson.getId());

        Learner learner = learners.findById(learnerId).orElseThrow();
        mastered(learner, lessonAExercises.get(0));
        mastered(learner, lessonAExercises.get(1));
        mastered(learner, lessonBExercises.get(0), 1.5); // high reps but ease below 2.0 -> not mastered

        var stats = authedGet("/api/learners/me/stats");
        Map<String, Object> unitMastery = (Map<String, Object>) stats.getBody().get("unit");
        assertThat(unitMastery.get("title")).isEqualTo("Unidad de maestría");
        assertThat(((Number) unitMastery.get("mastery")).doubleValue()).isEqualTo(0.5);

        List<?> lessonMasteries = (List<?>) unitMastery.get("lessons");
        assertThat(lessonMasteries).hasSize(2);
        Map<String, Object> lessonA = (Map<String, Object>) lessonMasteries.get(0);
        Map<String, Object> lessonB = (Map<String, Object>) lessonMasteries.get(1);
        assertThat(lessonA.get("title")).isEqualTo("Lección A");
        assertThat(((Number) lessonA.get("mastery")).doubleValue()).isEqualTo(1.0);
        assertThat(((Number) lessonA.get("masteredExercises")).longValue()).isEqualTo(2);
        assertThat(lessonB.get("title")).isEqualTo("Lección B");
        assertThat(((Number) lessonB.get("mastery")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) lessonB.get("masteredExercises")).longValue()).isEqualTo(0);
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
        email = "learner-" + System.nanoTime() + "@example.com";
        rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123", "displayName", "Test Learner", "timezone",
                        "Europe/Berlin"),
                Map.class);
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "password123"), Map.class);
        Map<String, Object> learner = (Map<String, Object>) login.getBody().get("learner");
        learnerId = ((Number) learner.get("id")).longValue();
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
        return startSessionFor(lessonId);
    }

    private Map startSessionFor(long sessionLessonId) {
        ResponseEntity<Map> response = rest.postForEntity("/api/sessions",
                new HttpEntity<>(Map.of("lessonId", sessionLessonId), authHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Map> submitAttempt(Object sessionId, long exerciseId, int grade) {
        return rest.postForEntity("/api/sessions/" + sessionId + "/attempts",
                new HttpEntity<>(Map.of("exerciseId", exerciseId, "grade", grade, "latencyMs", 1500, "hintShown",
                        false), authHeaders()),
                Map.class);
    }

    private void mastered(Learner learner, Exercise exercise) {
        mastered(learner, exercise, 2.4);
    }

    private void mastered(Learner learner, Exercise exercise, double easeFactor) {
        ReviewState state = new ReviewState(learner, exercise, Instant.now());
        state.setRepetitions(3);
        state.setEaseFactor(easeFactor);
        state.setIntervalDays(14);
        reviewStates.save(state);
    }

    private void flushRedis() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
