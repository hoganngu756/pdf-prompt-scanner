package com.promptscanner.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The detection rule set, bound from heuristic-rules.yml.
 *
 * Rules used to live in a SQLite table edited through an authenticated admin
 * panel. That put the scanner's detection content in an unversioned, unbacked-up
 * database, and required an auth surface that turned out to be the source of
 * most of this project's security issues. As configuration, a rule change is a
 * reviewable diff instead.
 */
@Component
@ConfigurationProperties(prefix = "app.heuristics")
public class HeuristicRuleProperties {

    private List<Rule> rules = new ArrayList<>();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private String phrase;
        private boolean regex;

        public String getPhrase() {
            return phrase;
        }

        public void setPhrase(String phrase) {
            this.phrase = phrase;
        }

        public boolean isRegex() {
            return regex;
        }

        public void setRegex(boolean regex) {
            this.regex = regex;
        }
    }
}
