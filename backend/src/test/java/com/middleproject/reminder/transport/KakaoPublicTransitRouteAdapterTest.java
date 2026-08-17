package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.domain.LandmarkCandidate;
import com.middleproject.reminder.transport.infrastructure.adapter.KakaoPublicTransitRouteAdapter;
import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.credential.AwsPublicDataCredentialProvider;
import com.middleproject.reminder.transport.infrastructure.credential.PublicDataCredentials;
import com.middleproject.reminder.transport.infrastructure.http.TransportHttpClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KakaoPublicTransitRouteAdapterTest {

    @Test
    void returnsAnEstimatedRouteAndOnlyAnOfficialKakaoHttpsHandoff() throws Exception {
        TransportHttpClient http = mock(TransportHttpClient.class);
        AwsPublicDataCredentialProvider credentials = mock(AwsPublicDataCredentialProvider.class);
        when(credentials.load(anyString())).thenReturn(new PublicDataCredentials("seoul", "tago", "kakao-secret"));
        when(http.sendGet(any(), any(), anyMap())).thenReturn(response(200, """
                {"status":"OK","properties":{"landingURL":"https://map.kakao.com/?route=demo"},
                 "routes":[{"properties":{"type":"PUBLIC_TRANSIT","totalTime":3661,"transfers":1,"fare":{"value":1450}}}]}
                """));
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setEnabled(true);
        KakaoPublicTransitRouteAdapter adapter = new KakaoPublicTransitRouteAdapter(http, properties, credentials);

        var outcome = adapter.preview(
                new LandmarkCandidate("강남역", "서울 강남구", 37.4979, 127.0276),
                new LandmarkCandidate("서울역", "서울 용산구", 37.5547, 126.9706));

        assertTrue(outcome.isSuccess());
        assertEquals(62, outcome.value().estimatedDurationMinutes());
        assertEquals(1, outcome.value().transferCount());
        assertEquals(1450, outcome.value().fareKrw());
        assertEquals("https://map.kakao.com/?route=demo", outcome.value().kakaoMapUrl());
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).sendGet(uri.capture(), any(Duration.class), headers.capture());
        assertTrue(uri.getValue().toString().contains("start_x=127.0276"));
        assertTrue(uri.getValue().toString().contains("end_y=37.5547"));
        assertEquals("KakaoAK kakao-secret", headers.getValue().get("Authorization"));
        assertFalse(uri.getValue().toString().contains("kakao-secret"));
    }

    @Test
    void failsClosedBeforeLoadingCredentialsWhenTransportIsDisabled() {
        TransportHttpClient http = mock(TransportHttpClient.class);
        AwsPublicDataCredentialProvider credentials = mock(AwsPublicDataCredentialProvider.class);
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setEnabled(false);
        KakaoPublicTransitRouteAdapter adapter = new KakaoPublicTransitRouteAdapter(http, properties, credentials);

        var outcome = adapter.preview(
                new LandmarkCandidate("강남역", "서울 강남구", 37.4979, 127.0276),
                new LandmarkCandidate("서울역", "서울 용산구", 37.5547, 126.9706));

        assertEquals(com.middleproject.reminder.transport.domain.TransportOutcome.FailureKind.DISABLED_INSECURE,
                outcome.failureKind());
        verifyNoInteractions(http, credentials);
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
