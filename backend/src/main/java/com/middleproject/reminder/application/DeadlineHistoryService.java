package com.middleproject.reminder.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DeadlineHistoryService {
    private final JdbcTemplate db;
    private final boolean schedulerEnabled;
    private final boolean queueEnabled;
    private final boolean emailEnabled;

    public DeadlineHistoryService(
            JdbcTemplate db,
            @Value("${scheduler.aws.enabled:false}") boolean schedulerEnabled,
            @Value("${delivery.sqs.enabled:false}") boolean queueEnabled,
            @Value("${notification.email.enabled:false}") boolean emailEnabled) {
        this.db = db;
        this.schedulerEnabled = schedulerEnabled;
        this.queueEnabled = queueEnabled;
        this.emailEnabled = emailEnabled;
    }

    public record HistoryView(
            UUID reminderId,
            String deliveryMode,
            String deliveryModeDetail,
            List<HistoryEntry> entries) { }

    public record HistoryEntry(
            String id,
            String kind,
            String action,
            String status,
            OffsetDateTime occurredAt,
            OffsetDateTime completedAt,
            String detail,
            String providerReference,
            int attempts) { }

    public HistoryView find(UUID reminderId, String ownerId) {
        List<HistoryEntry> entries = new ArrayList<>();
        List<HistoryEntry> current = db.query("""
                select r.id,r.status,r.created_at,r.updated_at
                  from reminders r
                 where r.id=? and r.owner_id=?
                """, (row, number) -> new HistoryEntry(
                "deadline-" + row.getObject("id"),
                "DEADLINE",
                "STATE",
                row.getString("status"),
                row.getObject("updated_at", OffsetDateTime.class),
                null,
                deadlineDetail(row.getString("status")),
                null,
                0), reminderId, ownerId);
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deadline not found");
        }
        entries.addAll(current);

        entries.addAll(db.query("""
                select id,operation,status,attempts,created_at,processed_at,last_error
                  from schedule_outbox
                 where reminder_id=?
                 order by created_at desc,id desc
                """, (row, number) -> {
            String status = row.getString("status");
            String operation = row.getString("operation");
            return new HistoryEntry(
                    row.getObject("id").toString(),
                    "SCHEDULE",
                    operation,
                    status,
                    row.getObject("created_at", OffsetDateTime.class),
                    row.getObject("processed_at", OffsetDateTime.class),
                    scheduleDetail(operation, status),
                    null,
                    row.getInt("attempts"));
        }, reminderId));

        entries.addAll(db.query("""
                select id,channel,status,provider_message_id,error_classification,created_at,completed_at
                  from notification_attempt
                 where reminder_id=?
                 order by created_at desc,id desc
                """, (row, number) -> {
            String storedStatus = row.getString("status");
            String publicStatus = "SUCCEEDED".equals(storedStatus) ? "PROVIDER_ACCEPTED" : storedStatus;
            return new HistoryEntry(
                    row.getObject("id").toString(),
                    "NOTIFICATION",
                    row.getString("channel"),
                    publicStatus,
                    row.getObject("created_at", OffsetDateTime.class),
                    row.getObject("completed_at", OffsetDateTime.class),
                    notificationDetail(publicStatus, row.getString("error_classification")),
                    row.getString("provider_message_id"),
                    1);
        }, reminderId));

        entries.sort(Comparator.comparing(HistoryEntry::occurredAt).reversed());
        return new HistoryView(reminderId, deliveryMode(), deliveryModeDetail(), List.copyOf(entries));
    }

    private String deliveryMode() {
        if (schedulerEnabled && queueEnabled && emailEnabled) return "ACTIVE";
        if (!schedulerEnabled && !queueEnabled && !emailEnabled) return "DISABLED";
        return "PARTIAL";
    }

    private String deliveryModeDetail() {
        return switch (deliveryMode()) {
            case "ACTIVE" -> "서버 예약·대기열·이메일 제공자 연동이 활성화되어 있습니다.";
            case "DISABLED" -> "외부 예약과 이메일 발송이 비활성화되어 있습니다. 저장된 일정을 발송 성공으로 표시하지 않습니다.";
            default -> "알림 처리 구성의 일부만 활성화되어 있습니다. 비활성 구성은 운영자가 확인해야 합니다.";
        };
    }

    private static String deadlineDetail(String status) {
        return switch (status) {
            case "CANCELLED" -> "일정이 취소되어 이후 예약 메시지는 현재 상태와 버전으로 차단됩니다.";
            case "DELIVERED", "ACKNOWLEDGED" -> "발송 제공자가 요청을 수락한 상태입니다. 실제 수신이나 열람을 뜻하지 않습니다.";
            case "DELIVERY_UNKNOWN" -> "제공자 응답 제한 시간을 넘겨 발송 결과가 불명확합니다. 자동으로 재발송하지 않습니다.";
            default -> "현재 저장된 일정·알림 상태입니다.";
        };
    }

    private static String scheduleDetail(String operation, String status) {
        if ("DELETE".equals(operation)) {
            return "SUCCEEDED".equals(status) ? "외부 예약 취소 요청을 처리했습니다." : "외부 예약 취소 작업을 처리 중입니다.";
        }
        return switch (status) {
            case "PENDING" -> "일정 저장은 완료됐고 서버 예약 작업을 기다리고 있습니다.";
            case "CLAIMED" -> "서버 worker가 예약 작업을 처리하고 있습니다.";
            case "SUCCEEDED" -> "예약 제공자에 예약 생성 또는 변경 요청을 반영했습니다.";
            case "RETRY" -> "일시적인 예약 오류로 제한된 재시도를 기다리고 있습니다.";
            case "FAILED" -> "예약 재시도 한도를 초과했습니다. 운영 확인이 필요합니다.";
            default -> "서버 예약 작업 상태입니다.";
        };
    }

    private static String notificationDetail(String status, String errorClassification) {
        return switch (status) {
            case "PROVIDER_ACCEPTED" -> "이메일 제공자가 발송 요청을 수락했습니다. 실제 수신이나 열람과는 다릅니다.";
            case "OUTCOME_UNKNOWN" -> "제공자 응답 제한 시간을 넘겨 결과가 불명확합니다. 중복 위험 때문에 자동 재발송하지 않습니다.";
            case "RETRYABLE_PROVIDER" -> "제공자 연결 실패로 제한된 재시도를 기다리고 있습니다.";
            case "PROVIDER_FAILURE", "DELIVERY_FAILED" -> "제공자가 발송 요청을 거부했습니다. 분류: " + safeClassification(errorClassification);
            case "STARTED" -> "서버가 발송 요청을 처리하고 있습니다.";
            default -> "영속화된 발송 처리 상태입니다.";
        };
    }

    private static String safeClassification(String value) {
        if (value == null || value.isBlank()) return "UNCLASSIFIED";
        return value.replaceAll("[^A-Z0-9_]", "_").substring(0, Math.min(50, value.length()));
    }
}
