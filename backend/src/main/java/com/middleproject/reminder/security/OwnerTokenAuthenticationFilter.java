package com.middleproject.reminder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class OwnerTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final int MIN_TOKEN_LENGTH = 24;

    private final boolean enabled;
    private final String ownerId;
    private final byte[] expectedToken;

    public OwnerTokenAuthenticationFilter(
            @Value("${app.security.enabled:false}") boolean enabled,
            @Value("${app.security.owner-id:}") String ownerId,
            @Value("${app.security.owner-token:}") String ownerToken) {
        this.enabled = enabled;
        this.ownerId = ownerId == null ? "" : ownerId.trim();
        this.expectedToken = ownerToken == null ? new byte[0] : ownerToken.getBytes(StandardCharsets.UTF_8);
        if (enabled && (this.ownerId.isBlank() || this.ownerId.length() > 200)) {
            throw new IllegalStateException("app.security.owner-id is required when API security is enabled");
        }
        if (enabled && this.expectedToken.length < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("app.security.owner-token must be at least 24 bytes when API security is enabled");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            byte[] candidate = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expectedToken, candidate)) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        ownerId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
