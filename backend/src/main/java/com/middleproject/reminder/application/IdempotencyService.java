package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.port.IdempotencyPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final IdempotencyPort port;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyPort port, ObjectMapper mapper) {
        this.port = port;
        this.mapper = mapper;
    }

    public <T> T execute(String scope, String key, Object payload, Class<T> type, Supplier<T> action) {
        String hash = hash(payload);
        if (port.reserve(scope, key, hash)) {
            T result = action.get();
            port.complete(scope, key, 200, write(result));
            return result;
        }
        IdempotencyPort.IdempotencyRecord record = port.find(scope, key).orElseThrow(() -> conflict("Idempotency record unavailable"));
        if (!hash.equals(record.requestHash())) throw conflict("Idempotency-Key was used with a different request");
        if (!record.completed()) throw conflict("Identical request is currently in progress");
        try { return mapper.readValue(record.responseBody(), type); }
        catch (JsonProcessingException e) { throw conflict("Stored idempotent response is invalid"); }
    }

    public void executeVoid(String scope, String key, Object payload, Runnable action) {
        String hash = hash(payload);
        if (port.reserve(scope, key, hash)) { action.run(); port.complete(scope, key, 204, null); return; }
        IdempotencyPort.IdempotencyRecord record = port.find(scope, key).orElseThrow(() -> conflict("Idempotency record unavailable"));
        if (!hash.equals(record.requestHash())) throw conflict("Idempotency-Key was used with a different request");
        if (!record.completed() || record.responseStatus() != 204) throw conflict("Stored idempotent response is invalid");
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot store response", e); }
    }

    private String hash(Object payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(payload));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
