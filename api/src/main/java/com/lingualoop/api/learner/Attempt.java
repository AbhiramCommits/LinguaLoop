package com.lingualoop.api.learner;

import java.time.Instant;

import com.lingualoop.api.content.Exercise;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "attempt")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(nullable = false)
    private short grade;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "hint_shown", nullable = false)
    private boolean hintShown;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attempt() {
    }

    public Attempt(StudySession session, Exercise exercise, short grade, int latencyMs, boolean hintShown) {
        this.session = session;
        this.exercise = exercise;
        this.grade = grade;
        this.latencyMs = latencyMs;
        this.hintShown = hintShown;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public StudySession getSession() {
        return session;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public short getGrade() {
        return grade;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public boolean isHintShown() {
        return hintShown;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
