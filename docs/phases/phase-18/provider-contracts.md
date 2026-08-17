# Phase 18 Approved Provider Contracts

This file is the external-call allowlist for Phase 18. It was derived from the user-provided
TAGO v1.0/v1.1 guides, the Seoul real-time arrival dataset, and the public-data gateway guide.
Credentials are never written here.

## Credential contract

- AWS secret name: `reminder-platform/phase18/public-data-api-keys`
- JSON fields: `seoulOpenDataKey`, `dataGoKrServiceKey`
- TAGO calls use the public-data portal Decoding general key as `serviceKey`.
- Secret values remain in AWS Secrets Manager and application memory only.
- Provider request URLs must be sanitized before logging; query strings and path credentials
  must never be emitted.

## Eight primary operations

1. Seoul real-time subway arrivals
   - Operation: `realtimeStationArrival`
   - Official URL shape:
     `http://swopenapi.seoul.go.kr/api/subway/{seoulOpenDataKey}/json/realtimeStationArrival/0/{limit}/{stationName}`
   - Input: canonical station name and bounded result count.
   - Output used: line, direction/destination, arrival seconds/message, received timestamp.
   - Cache TTL: at most 20 seconds.
   - Security gate: the official host did not answer HTTPS on 2026-08-17. The adapter must be
     disabled by default and must not make a live call until a secure endpoint is confirmed.

2. TAGO subway station lookup
   - Operation: `GetKwrdFndSubwaySttnList`
   - URL: `https://apis.data.go.kr/1613000/SubwayInfo/GetKwrdFndSubwaySttnList`
   - Required business input: `subwayStationName`; common parameters include `serviceKey`,
     `_type=json`, `pageNo`, and bounded `numOfRows`.
   - Cache TTL: at most 24 hours.

3. TAGO subway station schedule
   - Operation: `GetSubwaySttnAcctoSchdulList`
   - URL: `https://apis.data.go.kr/1613000/SubwayInfo/GetSubwaySttnAcctoSchdulList`
   - Required business input: `subwayStationId`, `dailyTypeCode`, `upDownTypeCode`.
   - Used for first/next/last scheduled train calculations in `Asia/Seoul`; it must not be
     presented as real-time position data.
   - Cache TTL: at most 5 minutes.

4. TAGO coordinate-proximity bus stops
   - Operation: `getCrdntPrxmtSttnList`
   - URL: `https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList`
   - Required business input: WGS84 `gpsLati`, `gpsLong`.
   - Output used: city code, stop ID/name, WGS84 coordinates and computed distance.
   - Cache TTL: at most 60 seconds; coordinates are not persisted or logged.

5. TAGO bus arrivals by stop
   - Operation: `getSttnAcctoArvlPrearngeInfoList`
   - URL: `https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList`
   - Required business input: `cityCode`, `nodeId`.
   - Output used: route identity, remaining stops/time when supplied by the provider.
   - Cache TTL: at most 20 seconds.

6. TAGO route-number lookup
   - Operation: `getRouteNoList`
   - URL: `https://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteNoList`
   - Required business input: `cityCode`, `routeNo`.
   - Cache TTL: at most 10 minutes.

7. TAGO express-bus arrival prediction
   - Operation: `GetExpBusArrPrdtInfo`
   - URL: `https://apis.data.go.kr/1613000/ExpBusArrInfo/GetExpBusArrPrdtInfo`
   - Required business input: `depTmnCd`, `arrTmnCd`.
   - This is an arrival-prediction source, not a booking or complete timetable source.
   - Cache TTL: at most 60 seconds.

8. TAGO intercity-bus scheduled services
   - Operation: `GetStrtpntAlocFndSuberbsBusInfo`
   - URL: `https://apis.data.go.kr/1613000/SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo`
   - Required business input: `depTerminalId`, `arrTerminalId`, `depPlandTime` (`yyyyMMdd`).
   - Output used when present: planned departure/arrival, grade and charge.
   - Cache TTL: at most 5 minutes.

## Approved supporting discovery operations

The following calls may only support the eight primary operations and must share their security,
timeout, pagination, retry and logging rules:

- `BusSttnInfoInqireService/getSttnNoList`
- `BusSttnInfoInqireService/getSttnThrghRouteList`
- `BusRouteInfoInqireService/getRouteAcctoThrghSttnList`
- `ExpBusArrInfo/GetExpBusTmnList`
- `ExpBusArrInfo/GetArrTmnFromDepTmn`
- `SuburbsBusInfo/GetSuberbsBusTrminlList`
- `SuburbsBusInfo/GetCtyCodeList`

## Normalized output

Every successful option exposes a stable identifier, mode, origin, destination, optional route
label, optional departure/arrival timestamps, optional duration, optional transfer count,
optional price, provenance, `fetchedAt`, `expiresAt`, and an optional allowlisted official HTTPS
booking URL. Missing Provider fields stay absent; the application must not fabricate values.

All timestamps exposed by the application use an offset and are interpreted against
`Asia/Seoul`. Provider partial failures use typed categories: timeout, rate limited,
authentication rejected, malformed, empty and disabled/insecure transport.

## Official hand-off links

When no approved query API exists, return a normal HTTPS link and let Android/browser intent
resolution open the installed app when supported:

- KTX/Korail: `https://www.letskorail.com/`
- SRT: `https://etk.srail.kr/`
- Express bus: `https://www.kobus.co.kr/`
- Tmoney intercity bus: `https://txbus.t-money.co.kr/`

No undocumented custom URI scheme, automatic reservation, payment or seat-availability claim is
allowed.
