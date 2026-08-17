package com.middleproject.reminder.transport.infrastructure.http;

import com.middleproject.reminder.transport.infrastructure.config.PublicTransportProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class DefaultTransportHttpClient implements TransportHttpClient {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;

    @Autowired
    public DefaultTransportHttpClient(PublicTransportProperties properties) {
        Duration connectTimeout = properties != null && properties.getConnectTimeout() != null
                ? properties.getConnectTimeout()
                : DEFAULT_CONNECT_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DefaultTransportHttpClient(Duration connectTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DefaultTransportHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public DefaultTransportHttpClient() {
        this(DEFAULT_CONNECT_TIMEOUT);
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public HttpResponse<String> sendGet(URI uri, Duration timeout) throws Exception {
        return sendGet(uri, timeout, Map.of());
    }

    @Override
    public HttpResponse<String> sendGet(URI uri, Duration timeout, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json, application/xml;q=0.9, text/xml;q=0.8")
                .timeout(timeout != null ? timeout : DEFAULT_REQUEST_TIMEOUT)
                .GET();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name == null || value == null || name.contains("\r") || name.contains("\n")
                        || value.contains("\r") || value.contains("\n")) {
                    throw new IllegalArgumentException("Invalid HTTP header");
                }
                builder.header(name, value);
            });
        }
        HttpRequest request = builder.build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
