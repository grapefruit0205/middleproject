package com.middleproject.reminder.web;

import com.middleproject.reminder.application.DeviceQueryService;
import com.middleproject.reminder.application.DeviceDayPlanQueryService;
import com.middleproject.reminder.application.DayPlanRevisionService;
import com.middleproject.reminder.application.PublicTransportQueryService;
import com.middleproject.reminder.application.LandmarkBusStopDiscoveryService;
import com.middleproject.reminder.application.ReminderService;
import com.middleproject.reminder.application.TripService;
import com.middleproject.reminder.application.TransitFavoriteService;
import com.middleproject.reminder.device.DevicePairingService;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.domain.Trip;
import com.middleproject.reminder.transport.domain.HandoffLinks;
import com.middleproject.reminder.transport.domain.TransportOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public Android pairing/device REST boundaries. Every /api/device/** request except the
 * unauthenticated exchange is protected by the bearer-token interceptor, which resolves
 * only the stored Demo Owner/device and ignores identity headers. All writes use
 * Idempotency-Key except the deliberately one-time pairing exchange and disconnect.
 */
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final DevicePairingService pairing;
    private final DeviceQueryService queries;
    private final TripService trips;
    private final ReminderService reminders;
    private final PublicTransportQueryService publicTransport;
    private final LandmarkBusStopDiscoveryService landmarkBusStops;
    private final TransitFavoriteService transitFavorites;
    private final DeviceDayPlanQueryService dayPlans;
    private final DayPlanRevisionService dayPlanRevisions;

    public DeviceController(DevicePairingService pairing, DeviceQueryService queries, TripService trips,
                            ReminderService reminders, PublicTransportQueryService publicTransport,
                            LandmarkBusStopDiscoveryService landmarkBusStops,
                            TransitFavoriteService transitFavorites,
                            DeviceDayPlanQueryService dayPlans,
                            DayPlanRevisionService dayPlanRevisions) {
        this.pairing = pairing;
        this.queries = queries;
        this.trips = trips;
        this.reminders = reminders;
        this.publicTransport = publicTransport;
        this.landmarkBusStops = landmarkBusStops;
        this.transitFavorites = transitFavorites;
        this.dayPlans = dayPlans;
        this.dayPlanRevisions = dayPlanRevisions;
    }

    public record ExchangeRequest(
            @NotBlank @Size(max = 20) String pairingCode,
            @NotBlank @Size(max = 200) String installationId,
            @NotBlank @Size(max = 200) String label) {}

    public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    public record FcmTokenRequest(@NotBlank @Size(max = 4096) String registrationToken) {}
    public record SubwayFavoriteRequest(@NotBlank @Size(max = 100) String alias,
                                        @NotBlank @Size(max = 200) String stationName) {}
    public record BusFavoriteRequest(@NotBlank @Size(max = 100) String alias,
                                     @NotNull Integer cityCode,
                                     @NotBlank @Size(max = 200) String nodeId,
                                     @Size(max = 200) String stopName,
                                     @Size(max = 100) String routeNo) {}

    /** Stable device response that does not rely on polymorphic sealed-type serialization. */
    public record TransportResponse<T>(
            boolean success,
            boolean empty,
            boolean retryable,
            String failureKind,
            T value,
            String errorMessage) {}

    /** Unauthenticated: accepts only the pairing code plus installation id and label. */
    @PostMapping("/exchange")
    public ExchangeResponse exchange(@Valid @RequestBody ExchangeRequest request) {
        DevicePairingService.ExchangeResult result = pairing.exchange(
                request.pairingCode().trim().toUpperCase(),
                request.installationId().trim(),
                request.label().trim());
        return new ExchangeResponse(result.token(), result.deviceId(),
                OffsetDateTime.ofInstant(result.expiresAt(), java.time.ZoneOffset.UTC));
    }

    public record ExchangeResponse(String token, UUID deviceId, OffsetDateTime expiresAt) {}

    @GetMapping("/trips")
    public List<Trip> trips() {
        return trips.all();
    }

    @GetMapping("/trips/{id}")
    public Trip trip(@PathVariable UUID id) {
        return trips.find(id);
    }

    @PostMapping("/trips/{id}/cancel")
    public Trip cancelTrip(@PathVariable UUID id,
                           @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                           @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return trips.cancel(id, request.expectedVersion(), key);
    }

    @GetMapping("/reminders")
    public List<DeviceQueryService.ReminderView> reminders() {
        return queries.reminders(pairing.ownerId());
    }

    @GetMapping("/reminders/{id}")
    public DeviceQueryService.ReminderView reminder(@PathVariable UUID id) {
        return queries.findReminder(id, pairing.ownerId());
    }

    @GetMapping("/reminders/{id}/delivery")
    public List<DeviceQueryService.DeliveryView> reminderDelivery(@PathVariable UUID id) {
        return queries.delivery(id, pairing.ownerId());
    }

    /** Read-only paired-device projection for the confirmed daily itinerary. */
    @GetMapping("/day-plans")
    public List<DeviceDayPlanQueryService.DayPlanView> dayPlans(
            @RequestParam(required = false) String date) {
        return dayPlans.findAll(pairing.ownerId(), parsePlanDate(date));
    }

    @GetMapping("/day-plans/{id}")
    public DeviceDayPlanQueryService.DayPlanView dayPlan(@PathVariable UUID id) {
        return dayPlans.find(pairing.ownerId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Day plan not found"));
    }

    /** Removes one item, cancels its notification, and returns the recomputed preview. */
    @PostMapping("/day-plans/{id}/items/{sequence}/cancel")
    public DayPlanRevisionService.RevisionResult cancelDayPlanItem(
            @PathVariable UUID id,
            @PathVariable int sequence,
            @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
            @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        return dayPlanRevisions.cancelItem(id, sequence, request.expectedVersion(), key);
    }

    @PostMapping("/reminders/{id}/cancel")
    public DeviceQueryService.ReminderView cancelReminder(@PathVariable UUID id,
                                                          @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                          @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        Reminder reminder = reminders.transition(id, ReminderStatus.CANCELLED, request.expectedVersion(), key, pairing.ownerId());
        return queries.findReminder(id, pairing.ownerId());
    }

    /** ACK uses the existing Reminder state machine and optimistic version; never bypasses service/domain rules. */
    @PostMapping("/reminders/{id}/ack")
    public DeviceQueryService.ReminderView acknowledge(@PathVariable UUID id,
                                                       @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                       @Valid @RequestBody VersionRequest request) {
        requireKey(key);
        reminders.transition(id, ReminderStatus.ACKNOWLEDGED, request.expectedVersion(), key, pairing.ownerId());
        return queries.findReminder(id, pairing.ownerId());
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<Void> registerFcmToken(@Valid @RequestBody FcmTokenRequest request,
                                                 @RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                 HttpServletRequest http) {
        requireKey(key);
        pairing.registerFcmToken(session(http).deviceId(), request.registrationToken().trim(), key);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fcm-token")
    public ResponseEntity<Void> unregisterFcmToken(@RequestHeader("Idempotency-Key") @Size(max = 200) String key,
                                                   HttpServletRequest http) {
        requireKey(key);
        pairing.unregisterFcmToken(session(http).deviceId(), key);
        return ResponseEntity.noContent().build();
    }

    /** Deliberately one-time: disconnect revokes the server token and removes FCM registration atomically. */
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(HttpServletRequest http) {
        pairing.disconnect(session(http).deviceId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transport/subway/stations")
    public TransportResponse<?> searchSubwayStations(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requireText("name", name);
        requirePage(pageNo, numOfRows, 50);
        return transportResponse(publicTransport.searchSubwayStations(name.trim(), pageNo, numOfRows));
    }

    @GetMapping("/transport/subway/arrivals")
    public TransportResponse<?> realtimeSubwayArrivals(
            @RequestParam String stationName,
            @RequestParam(defaultValue = "20") int limit) {
        requireText("stationName", stationName);
        if (limit < 1 || limit > 100) badRequest("limit must be between 1 and 100");
        return transportResponse(publicTransport.getRealtimeSubwayArrivals(stationName.trim(), limit));
    }

    @GetMapping("/transport/subway/schedule")
    public TransportResponse<?> subwaySchedule(
            @RequestParam String subwayStationId,
            @RequestParam String dailyTypeCode,
            @RequestParam String upDownTypeCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requireText("subwayStationId", subwayStationId);
        if (!List.of("01", "02", "03").contains(dailyTypeCode)) badRequest("dailyTypeCode must be 01, 02, or 03");
        if (!List.of("U", "D").contains(upDownTypeCode)) badRequest("upDownTypeCode must be U or D");
        requirePage(pageNo, numOfRows, 100);
        return transportResponse(publicTransport.getSubwayStationSchedule(
                subwayStationId.trim(), dailyTypeCode, upDownTypeCode, pageNo, numOfRows));
    }

    @GetMapping("/transport/bus/stops/nearby")
    public TransportResponse<?> nearbyBusStops(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        if (!Double.isFinite(latitude) || latitude < 33.0 || latitude > 39.0
                || !Double.isFinite(longitude) || longitude < 124.0 || longitude > 132.0) {
            badRequest("coordinates must be within the supported Korea boundary");
        }
        requirePage(pageNo, numOfRows, 50);
        return transportResponse(publicTransport.getNearbyBusStops(latitude, longitude, pageNo, numOfRows));
    }

    @GetMapping("/transport/bus/stops/by-landmark")
    public TransportResponse<?> busStopsByLandmark(
            @RequestParam String landmark,
            @RequestParam(defaultValue = "3") int maxCandidates) {
        requireText("landmark", landmark);
        if (maxCandidates < 1 || maxCandidates > 3) badRequest("maxCandidates must be between 1 and 3");
        return transportResponse(landmarkBusStops.find(landmark.trim(), maxCandidates));
    }

    @GetMapping("/transport/bus/arrivals")
    public TransportResponse<?> busArrivals(
            @RequestParam int cityCode,
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requirePositiveCityCode(cityCode);
        requireText("nodeId", nodeId);
        requirePage(pageNo, numOfRows, 50);
        return transportResponse(publicTransport.getBusArrivals(cityCode, nodeId.trim(), pageNo, numOfRows));
    }

    @GetMapping("/transport/bus/routes")
    public TransportResponse<?> busRoutes(
            @RequestParam int cityCode,
            @RequestParam String routeNo,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requirePositiveCityCode(cityCode);
        requireText("routeNo", routeNo);
        requirePage(pageNo, numOfRows, 50);
        return transportResponse(publicTransport.searchBusRoutes(cityCode, routeNo.trim(), pageNo, numOfRows));
    }

    @GetMapping("/transport/express-bus/arrivals")
    public TransportResponse<?> expressBusArrivals(
            @RequestParam String depTerminalCode,
            @RequestParam String arrTerminalCode,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requireText("depTerminalCode", depTerminalCode);
        requireText("arrTerminalCode", arrTerminalCode);
        requirePage(pageNo, numOfRows, 100);
        return transportResponse(publicTransport.getExpressBusArrivals(
                depTerminalCode.trim(), arrTerminalCode.trim(), pageNo, numOfRows));
    }

    @GetMapping("/transport/intercity-bus/schedule")
    public TransportResponse<?> intercityBusSchedule(
            @RequestParam String depTerminalId,
            @RequestParam String arrTerminalId,
            @RequestParam String depPlandTime,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows) {
        requireText("depTerminalId", depTerminalId);
        requireText("arrTerminalId", arrTerminalId);
        requireDate(depPlandTime);
        requirePage(pageNo, numOfRows, 100);
        return transportResponse(publicTransport.getIntercityBusSchedule(
                depTerminalId.trim(), arrTerminalId.trim(), depPlandTime, pageNo, numOfRows));
    }

    @GetMapping("/transport/handoffs")
    public Map<String, String> transportHandoffs() {
        return HandoffLinks.allOfficialHandoffs();
    }

    @GetMapping("/transport/favorites")
    public List<TransitFavoriteService.Favorite> transitFavorites() {
        return transitFavorites.list(pairing.ownerId());
    }

    @PostMapping("/transport/favorites/subway")
    public TransitFavoriteService.Favorite saveSubwayFavorite(
            @Valid @RequestBody SubwayFavoriteRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 200) String key) {
        requireKey(key);
        return transitFavorites.saveSubway(pairing.ownerId(), request.alias(), request.stationName(), key);
    }

    @PostMapping("/transport/favorites/bus")
    public TransitFavoriteService.Favorite saveBusFavorite(
            @Valid @RequestBody BusFavoriteRequest request,
            @RequestHeader("Idempotency-Key") @Size(max = 200) String key) {
        requireKey(key);
        return transitFavorites.saveBus(pairing.ownerId(), request.alias(), request.cityCode(), request.nodeId(),
                request.stopName(), request.routeNo(), key);
    }

    private DevicePairingService.DeviceSession session(HttpServletRequest http) {
        return (DevicePairingService.DeviceSession) http.getAttribute("deviceSession");
    }

    private void requireKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private void requireText(String name, String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            badRequest(name + " must be nonblank and at most 200 characters");
        }
    }

    private void requirePage(int pageNo, int numOfRows, int maximumRows) {
        if (pageNo < 1 || numOfRows < 1 || numOfRows > maximumRows) {
            badRequest("invalid pagination");
        }
    }

    private void requirePositiveCityCode(int cityCode) {
        if (cityCode < 1) badRequest("cityCode must be positive");
    }

    private void requireDate(String value) {
        if (value == null || !value.matches("[0-9]{8}")) badRequest("depPlandTime must be yyyyMMdd");
        try {
            LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            badRequest("depPlandTime must be a valid yyyyMMdd date");
        }
    }

    private LocalDate parsePlanDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(ZoneId.of("Asia/Seoul"));
        }
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("invalid date", value, 0);
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            badRequest("date must be a valid yyyy-MM-dd date");
            return null;
        }
    }

    private TransportResponse<?> transportResponse(TransportOutcome<?> outcome) {
        if (outcome.isSuccess()) {
            return new TransportResponse<>(true, false, false, null, outcome.value(), null);
        }
        if (outcome.isEmpty()) {
            return new TransportResponse<>(false, true, false, null, List.of(), null);
        }
        return new TransportResponse<>(false, false, outcome.isRetryable(),
                outcome.failureKind().name(), null, outcome.errorMessage());
    }

    private void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
