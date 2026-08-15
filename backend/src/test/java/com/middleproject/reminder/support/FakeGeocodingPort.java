package com.middleproject.reminder.support;

import com.middleproject.reminder.domain.GeoPoint;
import com.middleproject.reminder.domain.ProviderOutcome;
import com.middleproject.reminder.port.GeocodingPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic in-memory geocoding port for tests. */
public class FakeGeocodingPort implements GeocodingPort {
    public static final GeoPoint SEOUL = new GeoPoint(37.5665, 126.9780);
    public static final GeoPoint BUSAN = new GeoPoint(35.1796, 129.0756);
    public static final GeoPoint DAEJEON = new GeoPoint(36.3504, 127.3845);

    private final Map<String, GeoPoint> points = new LinkedHashMap<>();
    private ProviderOutcome<GeoPoint> fixed;
    private final List<ProviderOutcome<GeoPoint>> queue = new ArrayList<>();
    private int calls;

    public FakeGeocodingPort() {
        points.put("Seoul", SEOUL);
        points.put("Busan", BUSAN);
        points.put("Daejeon", DAEJEON);
    }

    public FakeGeocodingPort with(String location, GeoPoint point) { points.put(location, point); return this; }
    public FakeGeocodingPort failWith(ProviderOutcome<GeoPoint> outcome) { this.fixed = outcome; return this; }

    /** Outcomes are served in order; once the queue is empty the provider succeeds again. */
    public FakeGeocodingPort queue(ProviderOutcome<GeoPoint>... outcomes) {
        for (ProviderOutcome<GeoPoint> outcome : outcomes) queue.add(outcome);
        return this;
    }

    public void reset() { fixed = null; queue.clear(); calls = 0; }
    public int callCount() { return calls; }

    @Override
    public ProviderOutcome<GeoPoint> geocode(String location) {
        calls++;
        if (fixed != null) return fixed;
        if (!queue.isEmpty()) return queue.remove(0);
        GeoPoint point = points.get(location);
        return point == null ? new ProviderOutcome.Empty<>() : new ProviderOutcome.Success<>(point);
    }
}
