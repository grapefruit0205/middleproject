package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.port.IdempotencyPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private static final int WAIT_ATTEMPTS = 80;
    private static final long WAIT_MILLIS = 25;
    private final IdempotencyPort port;
    private final ObjectMapper mapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyPort port, ObjectMapper mapper, Clock clock) { this.port = port; this.mapper = mapper; this.clock = clock; }

    public <T> T execute(String scope, String key, Object payload, Class<T> type, Supplier<T> action) {
        String hash = hash(payload);
        String token = port.reserve(scope, key, hash);
        if (token != null) return run(scope, key, token, action);
        IdempotencyPort.IdempotencyRecord record = sameRecord(scope, key, hash);
        if (record.failed()) return retryFailed(scope, key, hash, record, action);
        record = awaitOrClaim(scope, key, hash, record);
        if (record.failed()) return retryFailed(scope, key, hash, record, action);
        if (record.completed()) return read(record, type);
        String expiredToken = port.claimExpired(scope, key, hash);
        if (expiredToken != null) return run(scope, key, expiredToken, action);
        return resolveAfterClaimRace(scope, key, hash, type, action);
    }

    public void executeVoid(String scope, String key, Object payload, Runnable action) {
        String hash = hash(payload);
        String token = port.reserve(scope, key, hash);
        if (token != null) { runVoid(scope, key, token, action); return; }
        IdempotencyPort.IdempotencyRecord record = sameRecord(scope, key, hash);
        if (record.failed()) {
            if (record.attempts() >= 10) throw conflict("Idempotency retry limit exceeded; use a new key after resolving the failure");
            String failedToken = port.claimFailed(scope, key, hash);
            if (failedToken != null) { runVoid(scope, key, failedToken, action); return; }
            throw conflict("Idempotency retry is already in progress");
        }
        record = awaitOrClaim(scope, key, hash, record);
        if (record.failed()) {
            if (record.attempts() >= 10) throw conflict("Idempotency retry limit exceeded; use a new key after resolving the failure");
            String failedToken = port.claimFailed(scope, key, hash);
            if (failedToken != null) { runVoid(scope, key, failedToken, action); return; }
            throw conflict("Idempotency retry is already in progress");
        }
        if (record.completed()) { if (record.responseStatus() == 204) return; throw conflict("Stored idempotent response is invalid"); }
        String expiredToken = port.claimExpired(scope, key, hash);
        if (expiredToken != null) { runVoid(scope, key, expiredToken, action); return; }
        resolveVoidAfterClaimRace(scope, key, hash, action);
    }

    private <T> T retryFailed(String scope, String key, String hash, IdempotencyPort.IdempotencyRecord record, Supplier<T> action) {
        if (record.attempts() >= 10) throw conflict("Idempotency retry limit exceeded; use a new key after resolving the failure");
        String token = port.claimFailed(scope, key, hash);
        if (token != null) return run(scope, key, token, action);
        throw conflict("Idempotency retry is already in progress");
    }

    private IdempotencyPort.IdempotencyRecord sameRecord(String scope, String key, String hash) {
        IdempotencyPort.IdempotencyRecord record = port.find(scope, key).orElseThrow(() -> conflict("Idempotency record unavailable"));
        if (!hash.equals(record.requestHash())) throw conflict("Idempotency-Key was used with a different request");
        return record;
    }

    private IdempotencyPort.IdempotencyRecord awaitOrClaim(String scope, String key, String hash, IdempotencyPort.IdempotencyRecord record) {
        for (int i = 0; i < WAIT_ATTEMPTS && !record.completed(); i++) {
            if (record.leaseUntil() != null && !record.leaseExpired(OffsetDateTime.now(clock))) {
                try { Thread.sleep(WAIT_MILLIS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw conflict("Idempotency wait interrupted"); }
                record = sameRecord(scope, key, hash);
            } else break;
        }
        return record;
    }

    private <T> T resolveAfterClaimRace(String scope, String key, String hash, Class<T> type, Supplier<T> action) {
        IdempotencyPort.IdempotencyRecord latest = awaitOrClaim(scope, key, hash, sameRecord(scope, key, hash));
        if (latest.failed()) return retryFailed(scope, key, hash, latest, action);
        if (latest.completed()) return read(latest, type);
        String expiredToken = port.claimExpired(scope, key, hash);
        if (expiredToken != null) return run(scope, key, expiredToken, action);
        throw conflict("Identical request is currently in progress");
    }

    private void resolveVoidAfterClaimRace(String scope, String key, String hash, Runnable action) {
        IdempotencyPort.IdempotencyRecord latest = awaitOrClaim(scope, key, hash, sameRecord(scope, key, hash));
        if (latest.failed()) {
            if (latest.attempts() >= 10) throw conflict("Idempotency retry limit exceeded; use a new key after resolving the failure");
            String failedToken = port.claimFailed(scope, key, hash);
            if (failedToken != null) { runVoid(scope, key, failedToken, action); return; }
            throw conflict("Idempotency retry is already in progress");
        }
        if (latest.completed()) { if (latest.responseStatus() == 204) return; throw conflict("Stored idempotent response is invalid"); }
        String expiredToken = port.claimExpired(scope, key, hash);
        if (expiredToken != null) { runVoid(scope, key, expiredToken, action); return; }
        throw conflict("Identical request is currently in progress");
    }

    private <T> T read(IdempotencyPort.IdempotencyRecord record, Class<T> type) {
        try { return mapper.readValue(record.responseBody(), type); } catch (JsonProcessingException e) { throw conflict("Stored idempotent response is invalid"); }
    }
    private <T> T run(String scope, String key, String token, Supplier<T> action) {
        try { T result = action.get(); if (!port.complete(scope, key, token, 200, mapper.writeValueAsString(result))) throw new IllegalStateException("Idempotency claim was fenced"); return result; }
        catch (RuntimeException | JsonProcessingException failure) { port.fail(scope, key, token, failure.getMessage()); if (failure instanceof RuntimeException r) throw r; throw conflict("Cannot store response"); }
    }
    private void runVoid(String scope, String key, String token, Runnable action) { try { action.run(); if (!port.complete(scope, key, token, 204, null)) throw new IllegalStateException("Idempotency claim was fenced"); } catch (RuntimeException failure) { port.fail(scope, key, token, failure.getMessage()); throw failure; } }
    private String hash(Object payload) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(payload)); StringBuilder s = new StringBuilder(); for (byte b : digest) s.append(String.format("%02x", b)); return s.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
