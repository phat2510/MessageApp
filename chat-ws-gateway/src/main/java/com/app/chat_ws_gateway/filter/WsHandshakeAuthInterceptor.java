package com.app.chat_ws_gateway.filter;

import com.app.chat_ws_gateway.config.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class WsHandshakeAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final int MAX_CONNECTIONS_PER_MINUTE = 10;
    private static final String RATE_LIMIT_PREFIX = "ws:rate:";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String clientIp = getClientIp(request);

        // Check rate limit
        if (!checkRateLimit(clientIp)) {
            log.warn("[Filter-Connection] IP {} vượt rate limit", clientIp);
            return false;
        }

        // Verify JWT
        String token = extractToken(request);
        if (token == null) {
            log.warn("[Filter-Auth] Không tìm thấy token từ IP {}", clientIp);
            return false;
        }

        try {
            Claims claims = jwtUtil.validateAndGetClaims(token);
            String userId = claims.getSubject();

            // Lưu vào session để handler dùng sau
            attributes.put("userId", userId);
            log.info("[Filter-Auth] Handshake OK — userId={}", userId);
            return true;

        } catch (Exception e) {
            log.warn("[Filter-Auth] Token không hợp lệ: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private boolean checkRateLimit(String ip) {
        String key = RATE_LIMIT_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        return count <= MAX_CONNECTIONS_PER_MINUTE;
    }

    private String extractToken(ServerHttpRequest request) {
        // Thử query param: ws://host/ws/chat?token=xxx
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        // Fallback: Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
