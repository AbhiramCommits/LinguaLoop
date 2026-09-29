package com.lingualoop.api.learner;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "streak")
public class Streak {

    @Id
    @Column(name = "learner_id")
    private Long learnerId;

    @Column(name = "current_days", nullable = false)
    private int currentDays;

    @Column(name = "longest_days", nullable = false)
    private int longestDays;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    protected Streak() {
    }

    public Streak(Long learnerId) {
        this.learnerId = learnerId;
    }

    public Long getLearnerId() {
        return learnerId;
    }

    public int getCurrentDays() {
        return currentDays;
    }

    public void setCurrentDays(int currentDays) {
        this.currentDays = currentDays;
    }

    public int getLongestDays() {
        return longestDays;
    }

    public void setLongestDays(int longestDays) {
        this.longestDays = longestDays;
    }

    public LocalDate getLastActiveDate() {
        return lastActiveDate;
    }

    public void setLastActiveDate(LocalDate lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }
}
