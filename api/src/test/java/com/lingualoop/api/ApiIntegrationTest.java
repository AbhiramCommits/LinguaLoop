package com.lingualoop.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.lingualoop.api.audio.AudioAsset;
import com.lingualoop.api.audio.AudioAssetRepository;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.ExerciseType;
import com.lingualoop.api.content.Language;
import com.lingualoop.api.content.LanguageRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.content.Unit;
import com.lingualoop.api.content.UnitRepository;
import com.lingualoop.api.experiment.Assignment;
import com.lingualoop.api.experiment.AssignmentRepository;
import com.lingualoop.api.experiment.Experiment;
import com.lingualoop.api.experiment.ExperimentRepository;
import com.lingualoop.api.experiment.ExperimentStatus;
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
import org.springframework.http.HttpRange;
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

    @Autowired
    AudioAssetRepository audioAssets;

    @Autowired
    ExperimentRepository experimentRepo;

    @Autowired
    AssignmentRepository assignmentRepo;

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
    void audioStreamingSupportsAcceptNegotiationRangesAndCaching() throws Exception {
        String sha = "ab" + "f".repeat(62);
        Path dir = Path.of("build", "test-audio", "audio", "ab");
        Files.createDirectories(dir);
        byte[] opusBytes = "OPUS-DATA-0123456789".getBytes();
        byte[] mp3Bytes = "MP3-DATA-abcdefghijklmnopqrstuvwxyz-0123456789".getBytes();
        Files.write(dir.resolve(sha + ".opus"), opusBytes);
        Files.write(dir.resolve(sha + ".mp3"), mp3Bytes);
        AudioAsset asset = audioAssets.save(new AudioAsset(sha,
                "audio/ab/" + sha + ".opus", "audio/ab/" + sha + ".mp3", 2100, opusBytes.length));

        // Default (no Accept header): Opus with immutable caching
        ResponseEntity<byte[]> full = rest.getForEntity("/api/audio/" + asset.getId(), byte[].class);
        assertThat(full.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(full.getHeaders().getContentType().toString()).startsWith("audio/ogg");
        assertThat(full.getHeaders().getCacheControl()).contains("public").contains("max-age=31536000")
                .contains("immutable");
        assertThat(full.getHeaders().getETag()).isEqualTo("\"" + sha + "-opus\"");
        assertThat(full.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(full.getBody()).isEqualTo(opusBytes);

        // Accept: audio/mpeg -> MP3 variant
        HttpHeaders mp3Headers = new HttpHeaders();
        mp3Headers.setAccept(List.of(MediaType.parseMediaType("audio/mpeg")));
        ResponseEntity<byte[]> mp3 = rest.exchange("/api/audio/" + asset.getId(), HttpMethod.GET,
                new HttpEntity<>(mp3Headers), byte[].class);
        assertThat(mp3.getHeaders().getContentType().toString()).startsWith("audio/mpeg");
        assertThat(mp3.getHeaders().getETag()).isEqualTo("\"" + sha + "-mp3\"");
        assertThat(mp3.getBody()).isEqualTo(mp3Bytes);

        // Accept: audio/* -> Opus
        HttpHeaders anyAudio = new HttpHeaders();
        anyAudio.setAccept(List.of(MediaType.parseMediaType("audio/*")));
        ResponseEntity<byte[]> viaWildcard = rest.exchange("/api/audio/" + asset.getId(), HttpMethod.GET,
                new HttpEntity<>(anyAudio), byte[].class);
        assertThat(viaWildcard.getHeaders().getContentType().toString()).startsWith("audio/ogg");

        // Byte-range request
        HttpHeaders rangeHeaders = new HttpHeaders();
        rangeHeaders.setAccept(List.of(MediaType.parseMediaType("audio/mpeg")));
        rangeHeaders.setRange(List.of(HttpRange.createByteRange(2, 7)));
        ResponseEntity<byte[]> range = rest.exchange("/api/audio/" + asset.getId(), HttpMethod.GET,
                new HttpEntity<>(rangeHeaders), byte[].class);
        assertThat(range.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(range.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 2-7/" + mp3Bytes.length);
        assertThat(range.getBody()).isEqualTo(Arrays.copyOfRange(mp3Bytes, 2, 8));

        // Unsatisfiable range -> 416 with bytes */length
        HttpHeaders badRangeHeaders = new HttpHeaders();
        badRangeHeaders.setAccept(List.of(MediaType.parseMediaType("audio/mpeg")));
        badRangeHeaders.setRange(List.of(HttpRange.createByteRange(mp3Bytes.length + 10, mp3Bytes.length + 20)));
        ResponseEntity<byte[]> unsatisfiable = rest.exchange("/api/audio/" + asset.getId(), HttpMethod.GET,
                new HttpEntity<>(badRangeHeaders), byte[].class);
        assertThat(unsatisfiable.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(unsatisfiable.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes */" + mp3Bytes.length);

        // Conditional request -> 304
        HttpHeaders conditional = new HttpHeaders();
        conditional.setIfNoneMatch(List.of("\"" + sha + "-opus\""));
        ResponseEntity<byte[]> notModified = rest.exchange("/api/audio/" + asset.getId(), HttpMethod.GET,
                new HttpEntity<>(conditional), byte[].class);
        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);

        // Unknown asset -> 404 problem details
        var missing = rest.getForEntity("/api/audio/99999999", Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getHeaders().getContentType().toString()).startsWith("application/problem+json");
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

    @Test
    void lessonOrderingVariantsProduceDifferentQueueOrders() {
        Language language = languages.save(new Language("t" + languageCounter.getAndIncrement(), "Spanish"));
        Unit unit = units.save(new Unit(language, "Unidad de orden", 1));
        Lesson lesson = lessons.save(new Lesson(unit, "Lección de orden", 1));
        Exercise first = exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "p1", "a1"));
        Exercise second = exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "p2", "a2"));
        Exercise third = exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "p3", "a3"));
        Exercise fourth = exercises.save(new Exercise(lesson, ExerciseType.TRANSLATE, "p4", "a4"));

        Registration dueFirst = registerLearner("DueFirst");
        Registration interleaved = registerLearner("Interleaved");

        Learner dueFirstLearner = learners.findById(dueFirst.learnerId()).orElseThrow();
        Learner interleavedLearner = learners.findById(interleaved.learnerId()).orElseThrow();
        assignmentRepo.save(new Assignment(dueFirstLearner, "lesson_ordering", "due_first"));
        assignmentRepo.save(new Assignment(interleavedLearner, "lesson_ordering", "interleaved"));

        Instant now = Instant.now();
        due(dueFirstLearner, first, now.minusSeconds(7200));
        due(dueFirstLearner, second, now.minusSeconds(3600));
        due(interleavedLearner, first, now.minusSeconds(7200));
        due(interleavedLearner, second, now.minusSeconds(3600));

        startSessionFor(dueFirst.token(), lesson.getId());
        startSessionFor(interleaved.token(), lesson.getId());

        List<?> controlItems = (List<?>) authedGetAs(dueFirst.token(), "/api/learners/me/queue").getBody().get("items");
        List<?> treatmentItems = (List<?>) authedGetAs(interleaved.token(), "/api/learners/me/queue").getBody()
                .get("items");

        List<Long> controlOrder = controlItems.stream()
                .map(item -> ((Number) ((Map<?, ?>) item).get("exerciseId")).longValue()).toList();
        List<Long> treatmentOrder = treatmentItems.stream()
                .map(item -> ((Number) ((Map<?, ?>) item).get("exerciseId")).longValue()).toList();

        // Control: strict due order (first, second) then unseen (third, fourth)
        assertThat(controlOrder).containsExactly(first.getId(), second.getId(), third.getId(), fourth.getId());
        // Interleaved (ratio 0.5): due, new, due, new
        assertThat(treatmentOrder).containsExactly(first.getId(), third.getId(), second.getId(), fourth.getId());
        assertThat(controlOrder).isNotEqualTo(treatmentOrder);
    }

    @Test
    void assignmentIsStableAndPersistedOnFirstExposure() {
        var queueFirst = authedGet("/api/learners/me/queue");
        var queueSecond = authedGet("/api/learners/me/queue");

        List<Long> first = ((List<?>) queueFirst.getBody().get("items")).stream()
                .map(item -> ((Number) ((Map<?, ?>) item).get("exerciseId")).longValue()).toList();
        List<Long> second = ((List<?>) queueSecond.getBody().get("items")).stream()
                .map(item -> ((Number) ((Map<?, ?>) item).get("exerciseId")).longValue()).toList();
        assertThat(first).isEqualTo(second);

        Assignment persisted = assignmentRepo.findByLearnerIdAndExperimentKey(learnerId, "lesson_ordering")
                .orElseThrow();
        assertThat(persisted.getVariantKey()).isIn("due_first", "interleaved");
    }

    @Test
    void draftExperimentResultsAreGuarded() {
        String key = "draft-" + System.nanoTime();
        experimentRepo.save(new Experiment(key, "draft experiment", ExperimentStatus.DRAFT));

        var response = authedGet("/api/experiments/" + key + "/results");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }

    @Test
    void resultsEndpointExcludesSimulatedLearnersByDefault() {
        Registration simulated = registerLearner("Simulated");
        Learner learner = learners.findById(simulated.learnerId()).orElseThrow();
        learner.setSimulated(true);
        learners.save(learner);
        assignmentRepo.save(new Assignment(learner, "lesson_ordering", "due_first"));
        startSessionFor(simulated.token(), lessonId);

        var realResults = authedGet("/api/experiments/lesson_ordering/results");
        assertThat(realResults.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(realResults.getBody().get("includeSimulated")).isEqualTo(false);
        Map<Object, Long> realCounts = variantCounts(realResults);

        var simulatedResults = authedGet("/api/experiments/lesson_ordering/results?includeSimulated=true");
        Map<Object, Long> simulatedCounts = variantCounts(simulatedResults);

        assertThat(simulatedCounts.get("due_first")).isEqualTo(realCounts.get("due_first") + 1);
        assertThat(simulatedCounts.get("interleaved")).isEqualTo(realCounts.get("interleaved"));
    }

    @Test
    void experimentListIsExposed() {
        ResponseEntity<List> response = rest.exchange("/api/experiments", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> keys = ((List<?>) response.getBody()).stream()
                .map(experiment -> (String) ((Map<?, ?>) experiment).get("key")).toList();
        assertThat(keys).contains("lesson_ordering", "hint_timing");
    }

    private static Map<Object, Long> variantCounts(ResponseEntity<Map> results) {
        Map<Object, Long> counts = new java.util.HashMap<>();
        for (Object row : (List<?>) results.getBody().get("variants")) {
            Map<?, ?> variant = (Map<?, ?>) row;
            counts.put(variant.get("key"), ((Number) variant.get("n")).longValue());
        }
        return counts;
    }

    @Test
    void hintTimingVariantIsReturnedInTheSessionPayload() {
        Map session = startSession();
        assertThat(session.get("hintDelaySeconds")).isIn(5, 12);
    }

    private record Registration(String token, Long learnerId, String email) {
    }

    private Registration registerLearner(String displayName) {
        String email = "learner-" + System.nanoTime() + "@example.com";
        rest.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "password123", "displayName", displayName, "timezone", "UTC"),
                Map.class);
        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                Map.of("email", email, "password", "password123"), Map.class);
        Map<String, Object> learner = (Map<String, Object>) login.getBody().get("learner");
        return new Registration((String) login.getBody().get("token"),
                ((Number) learner.get("id")).longValue(), email);
    }

    private void due(Learner learner, Exercise exercise, Instant dueAt) {
        ReviewState state = new ReviewState(learner, exercise, dueAt);
        state.setRepetitions(2);
        state.setIntervalDays(6);
        reviewStates.save(state);
    }

    private ResponseEntity<Map> authedGetAs(String tokenValue, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenValue);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
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
        return startSessionFor(token, lessonId);
    }

    private Map startSessionFor(long sessionLessonId) {
        return startSessionFor(token, sessionLessonId);
    }

    private Map startSessionFor(String tokenValue, long sessionLessonId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenValue);
        ResponseEntity<Map> response = rest.postForEntity("/api/sessions",
                new HttpEntity<>(Map.of("lessonId", sessionLessonId), headers), Map.class);
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
