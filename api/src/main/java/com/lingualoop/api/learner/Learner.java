package com.lingualoop.api.learner;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "learner")
public class Learner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "is_simulated", nullable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LearnerRole role = LearnerRole.LEARNER;

    protected Learner() {
    }

    public Learner(String email, String displayName, String passwordHash, String timezone) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone;
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

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }

    public LearnerRole getRole() {
        return role;
    }

    public void setRole(LearnerRole role) {
        this.role = role;
    }
}
