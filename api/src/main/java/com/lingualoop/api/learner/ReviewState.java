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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "review_state", uniqueConstraints = {
        @UniqueConstraint(name = "uq_review_state_learner_exercise", columnNames = { "learner_id", "exercise_id" })
})
public class ReviewState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private Learner learner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "ease_factor", nullable = false)
    private double easeFactor = 2.5;

    @Column(name = "interval_days", nullable = false)
    private double intervalDays = 0;

    @Column(nullable = false)
    private int repetitions = 0;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "last_grade")
    private Short lastGrade;

    @Column(nullable = false)
    private int lapses = 0;

    protected ReviewState() {
    }

    public ReviewState(Learner learner, Exercise exercise, Instant dueAt) {
        this.learner = learner;
        this.exercise = exercise;
        this.dueAt = dueAt;
    }

    public Long getId() {
        return id;
    }

    public Learner getLearner() {
        return learner;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public double getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(double intervalDays) {
        this.intervalDays = intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Short getLastGrade() {
        return lastGrade;
    }

    public void setLastGrade(Short lastGrade) {
        this.lastGrade = lastGrade;
    }

    public int getLapses() {
        return lapses;
    }

    public void setLapses(int lapses) {
        this.lapses = lapses;
    }
}
