package com.lingualoop.api.learner;

import java.util.Optional;

import com.lingualoop.api.content.Unit;
import com.lingualoop.api.content.UnitRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the learner's "active unit": the unit of their most recent study
 * session, falling back to the first unit in the catalogue when the learner
 * has never started a session.
 */
@Service
public class ActiveUnitResolver {

    private final StudySessionRepository sessions;
    private final UnitRepository units;

    public ActiveUnitResolver(StudySessionRepository sessions, UnitRepository units) {
        this.sessions = sessions;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public Optional<Unit> resolve(Long learnerId) {
        return sessions.findTopByLearnerIdOrderByIdDesc(learnerId)
                .map(session -> session.getLesson().getUnit())
                .or(() -> units.findAllByOrderByLanguageIdAscPositionAsc(PageRequest.of(0, 1)).stream()
                        .findFirst());
    }
}
