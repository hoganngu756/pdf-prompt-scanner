package com.promptscanner.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One reported observation.
 *
 * The parts are kept apart because only {@code description} is ours. {@code
 * location} and {@code quote} are recovered from the uploaded document and are
 * therefore attacker-controlled. Concatenating them into one sentence — as this
 * used to do — has two costs: a crafted PDF can supply metadata that reads as the
 * scanner's own framing and so forge a plausible-looking finding, and the client
 * is left unable to set quoted material apart from our prose, which is the one
 * typographic rule this product actually depends on.
 *
 * @param description what the scanner concluded. Always ours, never from the file.
 * @param location    where it was found (a metadata key, a page, a field name).
 *                    Frequently recovered from the document, so treat as untrusted.
 * @param quote       the recovered text itself. Untrusted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Finding(String description, String location, String quote) {

    public static Finding of(String description) {
        return new Finding(description, null, null);
    }

    public static Finding at(String description, String location) {
        return new Finding(description, location, null);
    }

    public static Finding quoting(String description, String quote) {
        return new Finding(description, null, quote);
    }

    public static Finding quoting(String description, String location, String quote) {
        return new Finding(description, location, quote);
    }
}
