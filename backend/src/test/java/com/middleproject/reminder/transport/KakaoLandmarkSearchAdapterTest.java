package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.infrastructure.adapter.KakaoLandmarkSearchAdapter;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KakaoLandmarkSearchAdapterTest {
    private TransportHttpClient http;
    private AwsPublicDataCredentialProvider credentials;
    private KakaoLandmarkSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        http = mock(TransportHttpClient.class);
        credentials = mock(AwsPublicDataCredentialProvider.class);
        when(credentials.load(anyString())).thenReturn(
                new PublicDataCredentials("seoul", "tago", "kakao-secret"));
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setEnabled(true);
        adapter = new KakaoLandmarkSearchAdapter(http, properties, credentials);
    }

    @Test
    void searchesKoreanLandmarkWithServerSideAuthorizationHeader() throws Exception {
        when(http.sendGet(any(), any(), anyMap())).thenReturn(response(200, """
                {"documents":[{"place_name":"강남역 11번 출구","address_name":"서울 강남구 역삼동 858","road_address_name":"서울 강남구 강남대로 396","x":"127.0276","y":"37.4981"}]}
                """));

        var outcome = adapter.search("강남역 11번 출구", 3);

        assertTrue(outcome.isSuccess());
        assertEquals("강남역 11번 출구", outcome.value().getFirst().name());
        assertEquals("서울 강남구 강남대로 396", outcome.value().getFirst().address());
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).sendGet(uri.capture(), any(Duration.class), headers.capture());
        assertTrue(uri.getValue().toString().contains("query=%EA%B0%95%EB%82%A8%EC%97%AD"));
        assertEquals("KakaoAK kakao-secret", headers.getValue().get("Authorization"));
        assertFalse(uri.getValue().toString().contains("kakao-secret"));
    }

    @Test
    void missingOptionalKakaoCredentialFailsClosedWithoutHttp() {
        when(credentials.load(anyString())).thenReturn(new PublicDataCredentials("seoul", "tago", null));

        var outcome = adapter.search("강남역", 3);

        assertEquals(com.middleproject.reminder.transport.domain.TransportOutcome.FailureKind.DISABLED_INSECURE,
                outcome.failureKind());
        verifyNoInteractions(http);
    }

    @Test
    void mapsProviderRateLimitWithoutLeakingTheCredential() throws Exception {
        when(http.sendGet(any(), any(), anyMap())).thenReturn(response(429, "quota exceeded"));

        var outcome = adapter.search("강남역", 3);

        assertEquals(com.middleproject.reminder.transport.domain.TransportOutcome.FailureKind.RATE_LIMITED,
                outcome.failureKind());
        assertFalse(String.valueOf(outcome.errorMessage()).contains("kakao-secret"));
    }

    @Test
    void findsNearbySubwayStationsUsingTheKakaoSubwayCategoryWithoutPuttingTheKeyInTheUrl() throws Exception {
        when(http.sendGet(any(), any(), anyMap())).thenReturn(response(200, """
                {"documents":[{"place_name":"강남역","address_name":"서울 강남구 역삼동","x":"127.0276","y":"37.4979","distance":"120"}]}
                """));

        var outcome = adapter.findNearbySubwayStations(37.4979, 127.0276, 1_000, 5);

        assertTrue(outcome.isSuccess());
        assertEquals("강남역", outcome.value().getFirst().name());
        assertEquals(120, outcome.value().getFirst().distanceMeters());
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).sendGet(uri.capture(), any(Duration.class), headers.capture());
        assertTrue(uri.getValue().toString().contains("category_group_code=SW8"));
        assertTrue(uri.getValue().toString().contains("radius=1000"));
        assertEquals("KakaoAK kakao-secret", headers.getValue().get("Authorization"));
        assertFalse(uri.getValue().toString().contains("kakao-secret"));
    }

    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return URI.create("https://dapi.kakao.com"); }
            public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
        };
    }
}
