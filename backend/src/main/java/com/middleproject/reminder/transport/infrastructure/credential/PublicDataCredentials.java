package com.middleproject.reminder.transport.infrastructure.credential;

import java.util.Objects;

public record PublicDataCredentials(
        String seoulOpenDataKey,
        String dataGoKrServiceKey,
        String kakaoLocalRestApiKey
) {
    public PublicDataCredentials(String seoulOpenDataKey, String dataGoKrServiceKey) {
        this(seoulOpenDataKey, dataGoKrServiceKey, null);
    }

    public PublicDataCredentials {
        if (seoulOpenDataKey == null || seoulOpenDataKey.isBlank()) {
            throw new IllegalArgumentException("seoulOpenDataKey must not be null or blank");
        }
        if (dataGoKrServiceKey == null || dataGoKrServiceKey.isBlank()) {
            throw new IllegalArgumentException("dataGoKrServiceKey must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return "PublicDataCredentials[seoulOpenDataKey=[PROTECTED], dataGoKrServiceKey=[PROTECTED], kakaoLocalRestApiKey=[PROTECTED]]";
    }
}
