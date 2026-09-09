package com.campus.growth.modules.auth.service;

import com.campus.growth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 *
 * <h3>为什么不用 Spring Security</h3>
 * <p>个人项目只需要"登录态 + 角色判断"两件事，引入 Security 的过滤链反而增加理解成本。
 * 这里用 jjwt + 拦截器实现，逻辑一目了然；真要接 SSO 再换也不影响业务代码。</p>
 *
 * <h3>登录态可控</h3>
 * <p>token 里带 {@code jti}，同时把 jti 写入 Redis 白名单。这样即使 JWT 本身没到期，
 * 也能通过删除白名单实现"立即踢下线"（禁用账号、改密码场景）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     *
     * @return token 与 jti（jti 用于写 Redis 白名单）
     */
    public TokenPair create(Long userId, String username, String role) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtProperties.getExpireMinutes() * 60_000);
        String token = Jwts.builder()
                .id(jti)
                .subject(username)
                .issuer(jwtProperties.getIssuer())
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey())
                .compact();
        return new TokenPair(token, jti, expire.getTime());
    }

    /** 解析 token，失败返回 null（调用方统一按未登录处理） */
    public Claims parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public long getExpireMinutes() {
        return jwtProperties.getExpireMinutes();
    }

    /** 签发结果 */
    public record TokenPair(String token, String jti, long expireAtMillis) {
    }
}
