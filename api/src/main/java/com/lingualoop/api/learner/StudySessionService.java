package com.lingualoop.api.learner;

import java.time.Duration;
import java.time.Instant;

import com.lingualoop.api.common.error.BadRequestException;
import com.lingualoop.api.common.error.ConflictException;
import com.lingualoop.api.common.error.NotFoundException;
import com.lingualoop.api.content.Exercise;
import com.lingualoop.api.content.ExerciseRepository;
import com.lingualoop.api.content.Lesson;
import com.lingualoop.api.content.LessonRepository;
import com.lingualoop.api.experiment.VariantService;
import com.lingualoop.api.learner.dto.AttemptRequest;
import com.lingualoop.api.learner.dto.AttemptResultDto;
import com.lingualoop.api.learner.dto.CompleteSessionResponse;
import com.lingualoop.api.learner.dto.ReviewInfoDto;
import com.lingualoop.api.learner.dto.SessionDto;
import com.lingualoop.api.scheduler.SchedulerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudySessionService {

    private static final Logger log = LoggerFactory.getLogger(StudySessionService.class);
    private static final Duration SESSION_STATE_TTL = Duration.ofHours(24);

    private final StudySessionRepository sessions;
    private final LearnerRepository learners;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final AttemptRepository attempts;
    private final VariantService variantService;
    private final SchedulerService schedulerService;
    private final StreakService streakService;
    private final QueueService queueService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StudySessionService(StudySessionRepository sessions, LearnerRepository learners,
            LessonRepository lessons, ExerciseRepository exercises, AttemptRepository attempts,
            VariantService variantService, SchedulerService schedulerService, StreakService streakService,
            QueueService queueService, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.learners = learners;
        this.lessons = lessons;
        this.exercises = exercises;
        this.attempts = attempts;
        this.variantService = variantService;
        this.schedulerService = schedulerService;
        this.streakService = streakService;
        this.queueService = queueService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionDto start(Long learnerId, Long lessonId) {
        Learner learner = learners.findById(learnerId)
                .orElseThrow(() -> new NotFoundException("Learner " + learnerId + " not found"));
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson " + lessonId + " not found"));
        StudySession session = sessions.save(new StudySession(learner, lesson, variantService.assignVariant()));
        long exerciseCount = exercises.countByLessonId(lessonId);
        cacheSessionState(session, 0, null);
        return new SessionDto(session.getId(), lessonId, session.getVariantKey(), session.getStartedAt(), exerciseCount);
    }

    @Transactional
    public AttemptResultDto submitAttempt(Long learnerId, Long sessionId, AttemptRequest request) {
        StudySession session = sessions.findByIdAndLearnerId(sessionId, learnerId)
                .orElseThrow(() -> new NotFoundException("Session " + sessionId + " not found"));
        if (session.getEndedAt() != null) {
            throw new ConflictException("Session " + sessionId + " is already completed");
        }
        Exercise exercise = exercises.findById(request.exerciseId())
                .orElseThrow(() -> new NotFoundException("Exercise " + request.exerciseId() + " not found"));
        if (!exercise.getLesson().getId().equals(session.getLesson().getId())) {
            throw new BadRequestException("Exercise " + exercise.getId() + " does not belong to this session's lesson");
        }

        Instant now = Instant.now();
        boolean hintShown = Boolean.TRUE.equals(request.hintShown());
        attempts.save(new Attempt(session, exercise, request.grade().shortValue(), request.latencyMs(), hintShown));
        ReviewState reviewState = schedulerService.recordGrade(session.getLearner(), exercise, request.grade(), now);
        streakService.recordActivity(learnerId, now);

        long attemptCount = attempts.countBySessionId(sessionId);
        cacheSessionState(session, attemptCount, exercise.getId());
        queueService.evict(learnerId);

        return new AttemptResultDto(null, exercise.getId(), request.grade(), new ReviewInfoDto(
                reviewState.getEaseFactor(), reviewState.getIntervalDays(), reviewState.getRepetitions(),
                reviewState.getDueAt(), reviewState.getLastGrade(), reviewState.getLapses()));
    }

    @Transactional
    public CompleteSessionResponse complete(Long learnerId, Long sessionId) {
        StudySession session = sessions.findByIdAndLearnerId(sessionId, learnerId)
                .orElseThrow(() -> new NotFoundException("Session " + sessionId + " not found"));
        if (session.getEndedAt() == null) {
            session.setEndedAt(Instant.now());
            sessions.save(session);
        }
        evictSessionState(sessionId);
        return new CompleteSessionResponse(
                session.getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getVariantKey(),
                attempts.countBySessionId(sessionId),
                round2(attempts.averageGradeBySession(sessionId)));
    }

    private record SessionStateCache(Long id, Long learnerId, Long lessonId, String variantKey, Instant startedAt,
            Instant endedAt, long attemptCount, Long lastExerciseId) {
    }

    private void cacheSessionState(StudySession session, long attemptCount, Long lastExerciseId) {
        try {
            SessionStateCache state = new SessionStateCache(session.getId(), session.getLearner().getId(),
                    session.getLesson().getId(), session.getVariantKey(), session.getStartedAt(),
                    session.getEndedAt(), attemptCount, lastExerciseId);
            redis.opsForValue().set("session-state:" + session.getId(),
                    objectMapper.writeValueAsString(state), SESSION_STATE_TTL);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.debug("Redis unavailable while caching session state", ex);
        }
    }

    private void evictSessionState(Long sessionId) {
        try {
            redis.delete("session-state:" + sessionId);
        } catch (RuntimeException ex) {
            log.debug("Redis unavailable while evicting session state", ex);
        }
    }

    private static Double round2(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }
}
