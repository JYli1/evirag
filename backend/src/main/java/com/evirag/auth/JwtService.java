package com.evirag.auth;

import com.evirag.config.AppProperties;
import com.evirag.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * JWT 生成与校验服务。
 *
 * <p>这里使用 HS256 手工生成紧凑 JWT，避免引入额外依赖；签名密钥在构造阶段校验，拒绝空值和明显默认弱密钥。</p>
 */
@Service
public class JwtService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long expireMinutes;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties appProperties, Clock clock) {
        this(appProperties.getJwt().getSecret(), appProperties.getJwt().getExpireMinutes(), clock);
        if (appProperties.getJwt().hasWeakSecret()) {
            throw new IllegalStateException("JWT 密钥不能使用空值或默认值");
        }
    }

    public JwtService(String secret, long expireMinutes, Clock clock) {
        if (secret == null || secret.isBlank() || secret.length() < 32 || "change-me".equals(secret)) {
            throw new IllegalStateException("JWT 密钥不能使用空值、默认值或过短值");
        }
        this.secret = secret;
        this.expireMinutes = expireMinutes;
        this.clock = clock;
    }

    @PostConstruct
    void validate() {
        // 构造函数已经完成校验；该方法保留给 Spring 启动日志定位，避免弱密钥进入运行期。
    }

    public AuthJwt createToken(User user) {
        long expiresAt = Instant.now(clock).plusSeconds(expireMinutes * 60).getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole());
        payload.put("exp", expiresAt);
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return new AuthJwt(unsigned + "." + sign(unsigned), expiresAt);
    }

    public JwtPrincipal parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new AuthException("无效令牌");
            }
            Map<String, Object> header = OBJECT_MAPPER.readValue(
                    BASE64_URL_DECODER.decode(parts[0]),
                    new TypeReference<>() {
                    }
            );
            if (!"HS256".equals(header.get("alg")) || !isCompatibleJwtType(header.get("typ"))) {
                throw new AuthException("无效令牌");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!signatureMatches(sign(unsigned), parts[2])) {
                throw new AuthException("无效令牌");
            }
            Map<String, Object> payload = OBJECT_MAPPER.readValue(
                    BASE64_URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now(clock).getEpochSecond() >= exp) {
                throw new AuthException("无效令牌");
            }
            Long userId = ((Number) payload.get("sub")).longValue();
            return new JwtPrincipal(userId, (String) payload.get("email"), (String) payload.get("role"));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("无效令牌");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 序列化失败", ex);
        }
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private boolean signatureMatches(String expected, String actual) {
        try {
            return MessageDigest.isEqual(BASE64_URL_DECODER.decode(expected), BASE64_URL_DECODER.decode(actual));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isCompatibleJwtType(Object typ) {
        return typ == null || "JWT".equals(typ);
    }

    public record AuthJwt(String token, long expiresAt) {
    }

    public record JwtPrincipal(Long userId, String email, String role) {
    }
}
