package com.middleproject.reminder.transport.infrastructure.http;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public interface TransportHttpClient {
    HttpResponse<String> sendGet(URI uri, Duration timeout) throws Exception;

    default HttpResponse<String> sendGet(URI uri, Duration timeout, Map<String, String> headers) throws Exception {
        if (headers != null && !headers.isEmpty()) {
            throw new UnsupportedOperationException("HTTP headers are not supported by this client");
        }
        return sendGet(uri, timeout);
    }
}
