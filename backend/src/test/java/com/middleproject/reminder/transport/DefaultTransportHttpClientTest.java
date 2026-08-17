package com.middleproject.reminder.transport;

import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import com.middleproject.reminder.transport.infrastructure.http.DefaultTransportHttpClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultTransportHttpClientTest {

    @Test
    void wiresConfiguredConnectTimeoutFromProperties() {
        PublicTransportProperties properties = new PublicTransportProperties();
        properties.setConnectTimeout(Duration.ofSeconds(7));

        DefaultTransportHttpClient client = new DefaultTransportHttpClient(properties);

        assertNotNull(client.getHttpClient());
        assertTrue(client.getHttpClient().connectTimeout().isPresent());
        assertEquals(Duration.ofSeconds(7), client.getHttpClient().connectTimeout().get());
    }

    @Test
    void wiresDefaultConnectTimeoutWhenPropertiesNull() {
        DefaultTransportHttpClient client = new DefaultTransportHttpClient((PublicTransportProperties) null);

        assertNotNull(client.getHttpClient());
        assertTrue(client.getHttpClient().connectTimeout().isPresent());
        assertEquals(Duration.ofSeconds(2), client.getHttpClient().connectTimeout().get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesRequestTimeoutOnSentRequest() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultTransportHttpClient client = new DefaultTransportHttpClient(mockHttpClient);
        Duration customTimeout = Duration.ofSeconds(9);
        URI testUri = URI.create("https://apis.data.go.kr/test");

        client.sendGet(testUri, customTimeout);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest captured = requestCaptor.getValue();
        assertEquals(testUri, captured.uri());
        assertTrue(captured.timeout().isPresent());
        assertEquals(customTimeout, captured.timeout().get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesDefaultRequestTimeoutWhenNullPassed() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DefaultTransportHttpClient client = new DefaultTransportHttpClient(mockHttpClient);
        URI testUri = URI.create("https://apis.data.go.kr/test");

        client.sendGet(testUri, null);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest captured = requestCaptor.getValue();
        assertTrue(captured.timeout().isPresent());
        assertEquals(Duration.ofSeconds(5), captured.timeout().get());
    }
}
