package com.promptscanner.backend.dto;

/**
 * Inbound shape for creating or updating a rule.
 *
 * Deliberately has no {@code id}: binding request JSON straight onto the JPA
 * entity let a client supply one and steer which row Hibernate wrote to. The id
 * now comes only from the path, and the persistence object is never constructed
 * from untrusted input.
 *
 * The booleans are nullable so "not supplied" stays distinguishable from
 * "supplied as false" — the controller applies defaults for the former.
 */
public record HeuristicRuleRequest(String phrase, Boolean isRegex, Boolean active) {

    public boolean regexOrDefault() {
        return isRegex != null && isRegex;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }

    public boolean hasPhrase() {
        return phrase != null && !phrase.isBlank();
    }
}
