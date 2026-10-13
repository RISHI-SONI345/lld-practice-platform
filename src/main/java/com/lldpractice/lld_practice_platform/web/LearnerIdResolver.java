package com.lldpractice.lld_practice_platform.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** A learner is a UUID in a cookie, created on first visit. There are no accounts. */
@Component
public class LearnerIdResolver {
    public static final String COOKIE = "lld_learner";

    public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (COOKIE.equals(c.getName())) {
                    try {
                        return UUID.fromString(c.getValue());
                    } catch (IllegalArgumentException ignored) {
                        // fall through and issue a fresh id
                    }
                }
            }
        }
        UUID id = UUID.randomUUID();
        ResponseCookie cookie = ResponseCookie.from(COOKIE, id.toString())
                .httpOnly(true).sameSite("Lax").path("/").maxAge(Duration.ofDays(365)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return id;
    }
}
