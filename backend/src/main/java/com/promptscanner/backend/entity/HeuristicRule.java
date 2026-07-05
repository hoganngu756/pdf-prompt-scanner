package com.promptscanner.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "heuristic_rules")
public class HeuristicRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phrase;

    private boolean isRegex;
    private boolean isActive;

    public HeuristicRule() {}

    public HeuristicRule(String phrase, boolean isRegex, boolean isActive) {
        this.phrase = phrase;
        this.isRegex = isRegex;
        this.isActive = isActive;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhrase() {
        return phrase;
    }

    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }

    public boolean isRegex() {
        return isRegex;
    }

    public void setRegex(boolean regex) {
        isRegex = regex;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
