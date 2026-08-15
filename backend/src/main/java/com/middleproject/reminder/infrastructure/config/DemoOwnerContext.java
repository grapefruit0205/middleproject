package com.middleproject.reminder.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 12-18 단일 소유자 시연 컨텍스트. 데모 소유자 ID는 배포 환경 변수로 고정하고
 * 클라이언트가 보낸 임의 사용자 ID를 절대 신뢰하지 않는다. 운영 배포에는 OAuth 2.1 IdP가 필요하다.
 */
@Component
public class DemoOwnerContext {

    private final String ownerId;

    public DemoOwnerContext(@Value("${trip.demo-owner-id:}") String ownerId) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 200) {
            throw new IllegalStateException("trip.demo-owner-id must be configured in the deployment environment");
        }
        this.ownerId = ownerId;
    }

    public String ownerId() {
        return ownerId;
    }
}
