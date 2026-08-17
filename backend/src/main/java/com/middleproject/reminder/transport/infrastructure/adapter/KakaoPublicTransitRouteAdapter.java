package com.middleproject.reminder.transport.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.domain.PublicTransitRoutePreview;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import com.middleproject.reminder.transport.port.PublicTransitRoutePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Kakao Map public-transit estimate. The provider URL is returned only when it is a Kakao HTTPS URL. */
@Component
@ConditionalOnProperty(name = "app.transport.enabled", havingValue = "true")
public class KakaoPublicTransitRouteAdapter implements PublicTransitRoutePort {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/routing/publictraffic";
    private final TransportHttpClient http;
    private final PublicTransportProperties properties;
    private final AwsPublicDataCredentialProvider credentialProvider;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile PublicDataCredentials cachedCredentials;

    public KakaoPublicTransitRouteAdapter(TransportHttpClient http, PublicTransportProperties properties,
                                          AwsPublicDataCredentialProvider credentialProvider) {
        this.http = http;
        this.properties = properties;
        this.credentialProvider = credentialProvider;
    }

    @Override
    public TransportOutcome<PublicTransitRoutePreview> preview(LandmarkCandidate origin, LandmarkCandidate destination) {
        if (origin == null || destination == null) return TransportOutcome.malformed("origin and destination are required");
        if (properties == null || !properties.isEnabled() || credentialProvider == null || http == null) {
            return TransportOutcome.disabledInsecure("Kakao Map public-transit routing is disabled");
        }
        try {
            PublicDataCredentials credentials = credentials();
            if (credentials.kakaoLocalRestApiKey() == null || credentials.kakaoLocalRestApiKey().isBlank()) {
                return TransportOutcome.disabledInsecure("Kakao Map credential is not configured");
            }
            URI uri = URI.create(ENDPOINT + "?start_x=" + origin.longitude() + "&start_y=" + origin.latitude()
                    + "&s_name=" + encode(origin.name()) + "&end_x=" + destination.longitude()
                    + "&end_y=" + destination.latitude() + "&e_name=" + encode(destination.name())
                    + "&input_coord=WGS84&output_coord=WGS84");
            HttpResponse<String> response = http.sendGet(uri, properties.getRequestTimeout(),
                    Map.of("Authorization", "KakaoAK " + credentials.kakaoLocalRestApiKey()));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return TransportOutcome.authRejected("Kakao Map rejected the configured credential");
            }
            if (response.statusCode() == 429) return TransportOutcome.rateLimited("Kakao Map rate limit exceeded");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TransportOutcome.malformed("Kakao Map returned an unexpected response");
            }
            JsonNode root = mapper.readTree(response.body());
            String status = root.path("status").asText("");
            if ("NO_RESULTS".equals(status) || "STARTNODES_NULL".equals(status) || "ENDNODES_NULL".equals(status)) {
                return TransportOutcome.empty();
            }
            if (!"OK".equals(status)) return TransportOutcome.malformed("Kakao Map response is malformed");
            JsonNode propertiesNode = root.path("properties");
            JsonNode routeProperties = root.path("routes").path(0).path("properties");
            int seconds = routeProperties.path("totalTime").asInt(-1);
            Integer minutes = seconds < 0 ? null : (seconds + 59) / 60;
            int transfers = routeProperties.path("transfers").asInt(-1);
            int fare = routeProperties.path("fare").path("value").asInt(-1);
            String landingUrl = propertiesNode.path("landingURL").asText("");
            return TransportOutcome.success(new PublicTransitRoutePreview(
                    origin, destination, routeProperties.path("type").asText(""), minutes,
                    transfers < 0 ? null : transfers, fare < 0 ? null : fare,
                    isKakaoHttpsUrl(landingUrl) ? landingUrl : null));
        } catch (HttpTimeoutException e) {
            return TransportOutcome.timeout("Kakao Map request timed out");
        } catch (Exception e) {
            return TransportOutcome.malformed("Kakao Map response could not be processed");
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean isKakaoHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("map.kakao.com") || host.endsWith(".map.kakao.com"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
