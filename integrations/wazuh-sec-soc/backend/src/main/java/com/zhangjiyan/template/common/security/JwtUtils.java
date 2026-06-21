package com.zhangjiyan.template.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JwtUtils {

    private static final String DEFAULT_DEMO_SECRET = "demo-only-change-with-env";
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 7200;

    private JwtUtils() {
    }

    public static String createAccessToken(LoginUser loginUser) {
        return createAccessToken(loginUser, DEFAULT_EXPIRES_IN_SECONDS);
    }

    public static String createAccessToken(LoginUser loginUser, long expiresInSeconds) {
        long expiresAt = Instant.now().plusSeconds(expiresInSeconds).getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{"
                + "\"iss\":\"" + AuthConstants.TOKEN_ISSUER + "\","
                + "\"sub\":\"" + escape(loginUser.username()) + "\","
                + "\"uid\":" + loginUser.userId() + ","
                + "\"nickname\":\"" + escape(loginUser.nickname()) + "\","
                + "\"roles\":\"" + escape(String.join(",", loginUser.roles())) + "\","
                + "\"permissions\":\"" + escape(String.join(",", loginUser.permissions())) + "\","
                + "\"exp\":" + expiresAt
                + "}");
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput, secret());
    }

    public static long accessTokenExpiresInSeconds() {
        return DEFAULT_EXPIRES_IN_SECONDS;
    }

    public static LoginUser parseAccessToken(String token) {
        Map<String, String> claims = parseClaims(token);
        long expiresAt = Long.parseLong(claims.getOrDefault("exp", "0"));
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new IllegalArgumentException("token expired");
        }
        return new LoginUser(
                Long.valueOf(claims.get("uid")),
                claims.get("sub"),
                claims.getOrDefault("nickname", claims.get("sub")),
                splitCsv(claims.get("roles")),
                splitCsv(claims.get("permissions"))
        );
    }

    private static Map<String, String> parseClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token");
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = sign(signingInput, secret());
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return parseFlatJson(payload);
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT signing failed", ex);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String secret() {
        return System.getenv().getOrDefault("JWT_SECRET", DEFAULT_DEMO_SECRET);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> claims = new HashMap<>();
        String body = json.substring(1, json.length() - 1);
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (ch == ',' && !inString) {
                pairs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString());
        }
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            claims.put(unquote(pair.substring(0, colonIndex).trim()), unquote(pair.substring(colonIndex + 1).trim()));
        }
        return claims;
    }

    private static String unquote(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return values;
    }
}
