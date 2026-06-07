package com.evirag.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evirag.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JWT 校验安全测试。
 *
 * <p>这里直接构造带有效 HMAC 的异常 header，确保 JwtService 不只是验证签名，还会校验 alg/typ 语义。</p>
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                SECRET,
                60,
                Clock.fixed(Instant.parse("2026-06-08T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    /**
     * 即使攻击者能构造出签名正确的 token，只要 header alg 不是 HS256，也必须拒绝。
     */
    @Test
    void rejectsTokenWithUnsupportedAlgorithmEvenWhenSignatureMatches() throws Exception {
        String token = signedToken(Map.of("alg", "none", "typ", "JWT"));

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("无效令牌");
    }

    /**
     * typ 明确不是 JWT 时拒绝，避免把其它令牌类型误当作本系统 JWT。
     */
    @Test
    void rejectsTokenWithUnsupportedTypeEvenWhenSignatureMatches() throws Exception {
        String token = signedToken(Map.of("alg", "HS256", "typ", "NOT_JWT"));

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("无效令牌");
    }

    /**
     * 签名被篡改时必须拒绝。
     */
    @Test
    void rejectsTamperedSignature() {
        User user = User.create("user@example.com", "hash");
        user.setId(1L);
        String token = jwtService.createToken(user).token();
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThatThrownBy(() -> jwtService.parseToken(tampered))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("无效令牌");
    }

    private String signedToken(Map<String, Object> header) throws Exception {
        String encodedHeader = ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
        String encodedPayload = ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "sub", 1,
                "email", "user@example.com",
                "role", "USER",
                "exp", Instant.parse("2026-06-08T09:00:00Z").getEpochSecond()
        )));
        String unsigned = encodedHeader + "." + encodedPayload;
        return unsigned + "." + sign(unsigned);
    }

    private String sign(String unsigned) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    }
}
