package com.middleproject.reminder.web;

import com.middleproject.reminder.device.DevicePairingService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authenticates every /api/device/** request except the unauthenticated pairing exchange. */
@Component
public class DeviceBearerAuthInterceptor implements HandlerInterceptor {

    private final DevicePairingService pairing;

    public DeviceBearerAuthInterceptor(DevicePairingService pairing) {
        this.pairing = pairing;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod)) {
            return true;
        }
        DevicePairingService.DeviceSession session = pairing.authenticate(request.getHeader("Authorization"));
        request.setAttribute("deviceSession", session);
        return true;
    }
}
