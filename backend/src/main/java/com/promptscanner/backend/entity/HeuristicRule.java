package com.promptscanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "heuristic_rules")
public class HeuristicRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phrase;

    @JsonProperty("isRegex")
    private Boolean isRegex;

    @JsonProperty("active")
    private Boolean isActive;

    public HeuristicRule() {}

    public HeuristicRule(String phrase, Boolean isRegex, Boolean isActive) {
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
        return isRegex != null && isRegex;
    }

    public void setRegex(Boolean regex) {
        isRegex = regex;
    }

    public boolean isActive() {
        return isActive != null && isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public Boolean getIsRegex() {
        return isRegex;
    }
}
