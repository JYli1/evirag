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
        // Spring 运行时走这个构造器，从 AppProperties 读取 .env/application.yml 绑定后的配置。
        this(appProperties.getJwt().getSecret(), appProperties.getJwt().getExpireMinutes(), clock);
        if (appProperties.getJwt().hasWeakSecret()) {
            throw new IllegalStateException("JWT 密钥不能使用空值或默认值");
        }
    }

    public JwtService(String secret, long expireMinutes, Clock clock) {
        // HS256 是对称签名，密钥过短很容易被猜出，所以启动阶段直接拒绝弱密钥。
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
        // JWT 分三段：header.payload.signature。前两段是 Base64URL 编码的 JSON。
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        // sub 放用户 ID，是后端识别登录用户的核心字段。
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
                // JWT 必须是三段式，格式不对直接拒绝。
                throw new AuthException("无效令牌");
            }
            Map<String, Object> header = OBJECT_MAPPER.readValue(
                    BASE64_URL_DECODER.decode(parts[0]),
                    new TypeReference<>() {
                    }
            );
            if (!"HS256".equals(header.get("alg")) || !isCompatibleJwtType(header.get("typ"))) {
                // 只接受本服务生成的 HS256 token。
                throw new AuthException("无效令牌");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!signatureMatches(sign(unsigned), parts[2])) {
                // 重新计算签名并比较，防止 token 被篡改。
                throw new AuthException("无效令牌");
            }
            Map<String, Object> payload = OBJECT_MAPPER.readValue(
                    BASE64_URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now(clock).getEpochSecond() >= exp) {
                // 过期 token 不再允许访问接口。
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
            // 签名覆盖 header 和 payload，任何一段被改都会导致签名校验失败。
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private boolean signatureMatches(String expected, String actual) {
        try {
            // MessageDigest.isEqual 尽量避免普通字符串比较带来的时序差异。
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

    /**
     * 放入 Spring SecurityContext 的当前用户信息。
     *
     * <p>Controller 通过 {@code @AuthenticationPrincipal JwtPrincipal principal} 直接拿到该对象。</p>
     */
    public record JwtPrincipal(Long userId, String email, String role) {
    }
}
