package com.example.api_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 密码重置令牌编解码。
 *
 * <p>令牌由「高熵随机 nonce + HMAC-SHA256 签名」组成，邮件链接携带完整签名参数；
 * 数据库只保存令牌的 SHA-256 哈希，即使库被读也无法反推可用链接。</p>
 *
 * <p>签名密钥从 JWT secret 派生（而非直接复用），避免两个场景共用同一把密钥。</p>
 */
@Component
public class ResetTokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_CONTEXT = "api-service:password-reset:";

    private final byte[] signatureKey;

    public ResetTokenCodec(@Value("${app.jwt.secret}") String jwtSecret) {
        this.signatureKey = sha256(SIGNATURE_CONTEXT + jwtSecret);
    }

    /**
     * 签发重置令牌：32 字节随机 nonce + 签名。
     */
    public String issue() {
        String nonce = randomToken(32);
        return nonce + "." + sign(nonce);
    }

    /**
     * 校验令牌签名，签名不合法时无需查库即可快速拒绝。
     */
    public boolean verifySignature(String token) {
        if (token == null) {
            return false;
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        String nonce = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        byte[] expected = sign(nonce).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 生成 URL 安全的随机串，用于重置令牌 nonce 与一次性票据。
     */
    public String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算入库用的令牌哈希。
     */
    public String hash(String token) {
        return HexFormat.of().formatHex(sha256(token));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signatureKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("重置令牌签名失败", ex);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}
