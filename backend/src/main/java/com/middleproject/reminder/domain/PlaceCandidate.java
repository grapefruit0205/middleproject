package com.middleproject.reminder.domain;

import java.time.Instant;

/**
 * Immutable candidate place for a travel recommendation. Price and rating are optional
 * but each requires its source when present.
 */
public record PlaceCandidate(String id, String name, PlaceCategory category, int distanceMeters,
                             Double price, Double rating, String priceSource, String ratingSource,
                             String provider, String source, Instant fetchedAt) {

    public PlaceCandidate {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (category == null) throw new IllegalArgumentException("category is required");
        if (distanceMeters < 0) throw new IllegalArgumentException("distanceMeters must be nonnegative");
        if (price != null && (priceSource == null || priceSource.isBlank())) {
            throw new IllegalArgumentException("priceSource is required when price is present");
        }
        if (rating != null && (ratingSource == null || ratingSource.isBlank())) {
            throw new IllegalArgumentException("ratingSource is required when rating is present");
        }
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        if (fetchedAt == null) throw new IllegalArgumentException("fetchedAt is required");
    }
}
