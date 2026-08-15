package com.middleproject.reminder.domain;

public record ProviderFailure(String stage, String category, ProviderOutcome.Kind kind) {

    public ProviderFailure {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("stage must not be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category must not be null or blank");
        }
        if (kind == null || kind == ProviderOutcome.Kind.SUCCESS) {
            throw new IllegalArgumentException("kind must not be null and must not be SUCCESS");
        }
    }
}
