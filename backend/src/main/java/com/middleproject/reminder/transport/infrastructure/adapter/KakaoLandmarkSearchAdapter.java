package com.middleproject.reminder.transport.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.NearbySubwayStation;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import com.middleproject.reminder.transport.port.LandmarkSearchPort;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.transport.enabled", havingValue = "true")
public class KakaoLandmarkSearchAdapter implements LandmarkSearchPort {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String CATEGORY_ENDPOINT = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String SUBWAY_CATEGORY = "SW8";
    private final TransportHttpClient http;
    private final PublicTransportProperties properties;
    private final AwsPublicDataCredentialProvider credentialProvider;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile PublicDataCredentials cachedCredentials;

    public KakaoLandmarkSearchAdapter(TransportHttpClient http, PublicTransportProperties properties,
                                      AwsPublicDataCredentialProvider credentialProvider) {
        this.http = http;
        this.properties = properties;
        this.credentialProvider = credentialProvider;
    }

    @Override
    public TransportOutcome<List<LandmarkCandidate>> search(String query, int limit) {
        if (query == null || query.isBlank() || query.length() > 200) {
            return TransportOutcome.malformed("landmark must be nonblank and at most 200 characters");
        }
        if (properties == null || !properties.isEnabled() || credentialProvider == null || http == null) {
            return TransportOutcome.disabledInsecure("Kakao landmark search is disabled");
        }
        try {
            PublicDataCredentials credentials = credentials();
            if (credentials.kakaoLocalRestApiKey() == null || credentials.kakaoLocalRestApiKey().isBlank()) {
                return TransportOutcome.disabledInsecure("Kakao Local credential is not configured");
            }
            int size = Math.max(1, Math.min(limit, 15));
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create(ENDPOINT + "?query=" + encoded + "&size=" + size + "&sort=accuracy");
            HttpResponse<String> response = http.sendGet(uri, properties.getRequestTimeout(),
                    Map.of("Authorization", "KakaoAK " + credentials.kakaoLocalRestApiKey()));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return TransportOutcome.authRejected("Kakao Local rejected the configured credential");
            }
            if (response.statusCode() == 429) {
                return TransportOutcome.rateLimited("Kakao Local rate limit exceeded");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TransportOutcome.malformed("Kakao Local returned an unexpected response");
            }
            JsonNode documents = mapper.readTree(response.body()).path("documents");
            if (!documents.isArray()) return TransportOutcome.malformed("Kakao Local response is malformed");
            List<LandmarkCandidate> candidates = new ArrayList<>();
            for (JsonNode document : documents) {
                String name = document.path("place_name").asText("").trim();
                String address = document.path("road_address_name").asText("").trim();
                if (address.isBlank()) address = document.path("address_name").asText("").trim();
                if (name.isBlank()) continue;
                double longitude = Double.parseDouble(document.path("x").asText());
                double latitude = Double.parseDouble(document.path("y").asText());
                candidates.add(new LandmarkCandidate(name, address, latitude, longitude));
            }
            return candidates.isEmpty() ? TransportOutcome.empty() : TransportOutcome.success(candidates);
        } catch (HttpTimeoutException e) {
            return TransportOutcome.timeout("Kakao Local request timed out");
        } catch (Exception e) {
            return TransportOutcome.malformed("Kakao Local response could not be processed");
        }
    }

    @Override
    public TransportOutcome<List<NearbySubwayStation>> findNearbySubwayStations(
            double latitude, double longitude, int radiusMeters, int limit) {
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            return TransportOutcome.malformed("coordinates must be within South Korea");
        }
        if (radiusMeters < 1 || radiusMeters > 20_000 || limit < 1 || limit > 15) {
            return TransportOutcome.malformed("radius or limit is outside the supported range");
        }
        if (properties == null || !properties.isEnabled() || credentialProvider == null || http == null) {
            return TransportOutcome.disabledInsecure("Kakao nearby subway search is disabled");
        }
        try {
            PublicDataCredentials credentials = credentials();
            if (credentials.kakaoLocalRestApiKey() == null || credentials.kakaoLocalRestApiKey().isBlank()) {
                return TransportOutcome.disabledInsecure("Kakao Local credential is not configured");
            }
            URI uri = URI.create(CATEGORY_ENDPOINT + "?category_group_code=" + SUBWAY_CATEGORY
                    + "&x=" + longitude + "&y=" + latitude + "&radius=" + radiusMeters
                    + "&size=" + limit + "&sort=distance");
            HttpResponse<String> response = http.sendGet(uri, properties.getRequestTimeout(),
                    Map.of("Authorization", "KakaoAK " + credentials.kakaoLocalRestApiKey()));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return TransportOutcome.authRejected("Kakao Local rejected the configured credential");
            }
            if (response.statusCode() == 429) return TransportOutcome.rateLimited("Kakao Local rate limit exceeded");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TransportOutcome.malformed("Kakao Local returned an unexpected response");
            }
            JsonNode documents = mapper.readTree(response.body()).path("documents");
            if (!documents.isArray()) return TransportOutcome.malformed("Kakao Local response is malformed");
            List<NearbySubwayStation> stations = new ArrayList<>();
            for (JsonNode document : documents) {
                String name = document.path("place_name").asText("").trim();
                if (name.isBlank()) continue;
                String address = document.path("road_address_name").asText("").trim();
                if (address.isBlank()) address = document.path("address_name").asText("").trim();
                String distance = document.path("distance").asText("").trim();
                Integer distanceMeters = distance.isBlank() ? null : Integer.valueOf(distance);
                stations.add(new NearbySubwayStation(name, address,
                        Double.parseDouble(document.path("y").asText()),
                        Double.parseDouble(document.path("x").asText()), distanceMeters));
            }
            return stations.isEmpty() ? TransportOutcome.empty() : TransportOutcome.success(stations);
        } catch (HttpTimeoutException e) {
            return TransportOutcome.timeout("Kakao Local request timed out");
        } catch (Exception e) {
            return TransportOutcome.malformed("Kakao Local response could not be processed");
        }
    }

    private PublicDataCredentials credentials() {
        PublicDataCredentials current = cachedCredentials;
        if (current == null) {
            current = credentialProvider.load(properties.getSecretsSecretId());
            cachedCredentials = current;
        }
        return current;
    }
}
