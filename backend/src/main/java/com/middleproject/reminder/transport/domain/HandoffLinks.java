package com.middleproject.reminder.transport.domain;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public final class HandoffLinks {

    public static final String KORAIL = "https://www.letskorail.com/";
    public static final String SRT = "https://etk.srail.kr/";
    public static final String EXPRESS_BUS = "https://www.kobus.co.kr/";
    public static final String TMONEY_INTERCITY_BUS = "https://txbus.t-money.co.kr/";
    public static final String KOREA_AIRPORTS = "https://www.airport.co.kr/";

    private static final Set<String> ALLOWLISTED_HOSTS = Set.of(
            "www.letskorail.com",
            "letskorail.com",
            "etk.srail.kr",
            "www.srail.kr",
            "www.kobus.co.kr",
            "kobus.co.kr",
            "txbus.t-money.co.kr",
            "www.airport.co.kr",
            "airport.co.kr",
            "www.airport.kr"
    );

    private static final Map<TransportMode, String> DEFAULT_HANDOFFS = Map.of(
            TransportMode.TRAIN, KORAIL,
            TransportMode.EXPRESS_BUS, EXPRESS_BUS,
            TransportMode.INTERCITY_BUS, TMONEY_INTERCITY_BUS,
            TransportMode.AIR, KOREA_AIRPORTS
    );

    private HandoffLinks() {}

    public static boolean isAllowlisted(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return ALLOWLISTED_HOSTS.contains(host.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    public static String defaultHandoffFor(TransportMode mode) {
        return DEFAULT_HANDOFFS.get(mode);
    }

    public static Map<String, String> allOfficialHandoffs() {
        return Map.of(
                "korail", KORAIL,
                "srt", SRT,
                "expressBus", EXPRESS_BUS,
                "tmoneyIntercityBus", TMONEY_INTERCITY_BUS,
                "koreaAirports", KOREA_AIRPORTS
        );
    }
}
