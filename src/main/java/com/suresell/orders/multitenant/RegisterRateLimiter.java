package com.suresell.orders.multitenant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limit anti-abuso para el alta self-service (/auth/register): como máximo
 * {@value #MAX_PER_WINDOW} registros por IP en {@link #WINDOW}. En memoria — basta
 * para el backend single-instance de F1; si se escala horizontalmente hay que mover
 * esto a un store compartido (Redis). Ver docs/110 §8.
 */
@Component
@Profile("cloud")
public class RegisterRateLimiter {

    static final int MAX_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Registra un intento para la IP; lanza 429 si excede el cupo de la ventana. */
    public void check(String ip) {
        String key = (ip == null || ip.isBlank()) ? "unknown" : ip;
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
                q.pollFirst();
            }
            if (q.size() >= MAX_PER_WINDOW) {
                throw new AuthException(429,
                        "Demasiados registros desde esta red; intenta más tarde.");
            }
            q.addLast(now);
        }
    }
}
