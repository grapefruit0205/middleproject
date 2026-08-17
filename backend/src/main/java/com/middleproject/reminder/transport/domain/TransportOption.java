package com.middleproject.reminder.transport.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public record TransportOption(
        String id,
        TransportMode mode,
        String originName,
        String destinationName,
        String routeLabel,
        OffsetDateTime departureTime,
        OffsetDateTime arrivalTime,
        Duration estimatedDuration,
        Integer transferCount,
        Integer priceKrw,
        TransportProvenance provenance,
        OffsetDateTime fetchedAt,
        OffsetDateTime expiresAt,
        String officialBookingUrl
) {
    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public TransportOption {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(originName, "originName must not be null");
        Objects.requireNonNull(destinationName, "destinationName must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        departureTime = departureTime == null ? null : departureTime.atZoneSameInstant(SEOUL_ZONE).toOffsetDateTime();
        arrivalTime = arrivalTime == null ? null : arrivalTime.atZoneSameInstant(SEOUL_ZONE).toOffsetDateTime();
        fetchedAt = fetchedAt.atZoneSameInstant(SEOUL_ZONE).toOffsetDateTime();
        expiresAt = expiresAt.atZoneSameInstant(SEOUL_ZONE).toOffsetDateTime();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private TransportMode mode;
        private String originName;
        private String destinationName;
        private String routeLabel;
        private OffsetDateTime departureTime;
        private OffsetDateTime arrivalTime;
        private Duration estimatedDuration;
        private Integer transferCount;
        private Integer priceKrw;
        private TransportProvenance provenance;
        private OffsetDateTime fetchedAt;
        private OffsetDateTime expiresAt;
        private String officialBookingUrl;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder mode(TransportMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder originName(String originName) {
            this.originName = originName;
            return this;
        }

        public Builder destinationName(String destinationName) {
            this.destinationName = destinationName;
            return this;
        }

        public Builder routeLabel(String routeLabel) {
            this.routeLabel = routeLabel;
            return this;
        }

        public Builder departureTime(OffsetDateTime departureTime) {
            this.departureTime = departureTime;
            return this;
        }

        public Builder arrivalTime(OffsetDateTime arrivalTime) {
            this.arrivalTime = arrivalTime;
            return this;
        }

        public Builder estimatedDuration(Duration estimatedDuration) {
            this.estimatedDuration = estimatedDuration;
            return this;
        }

        public Builder transferCount(Integer transferCount) {
            this.transferCount = transferCount;
            return this;
        }

        public Builder priceKrw(Integer priceKrw) {
            this.priceKrw = priceKrw;
            return this;
        }

        public Builder provenance(TransportProvenance provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder fetchedAt(OffsetDateTime fetchedAt) {
            this.fetchedAt = fetchedAt;
            return this;
        }

        public Builder expiresAt(OffsetDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder officialBookingUrl(String officialBookingUrl) {
            this.officialBookingUrl = officialBookingUrl;
            return this;
        }

        public TransportOption build() {
            return new TransportOption(
                    id,
                    mode,
                    originName,
                    destinationName,
                    routeLabel,
                    departureTime,
                    arrivalTime,
                    estimatedDuration,
                    transferCount,
                    priceKrw,
                    provenance,
                    fetchedAt,
                    expiresAt,
                    officialBookingUrl
            );
        }
    }
}
