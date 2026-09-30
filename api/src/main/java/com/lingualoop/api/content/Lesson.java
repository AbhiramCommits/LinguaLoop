package com.lingualoop.api.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int position;

    protected Lesson() {
    }

    public Lesson(Unit unit, String title, int position) {
        this.unit = unit;
        this.title = title;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Unit getUnit() {
        return unit;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
