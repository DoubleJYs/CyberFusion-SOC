package com.zhangjiyan.template.soc.wazuh;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(WazuhClientProperties.class)
public class WazuhClient {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final WazuhClientProperties properties;
    private final ObjectMapper objectMapper;

    public boolean configured() {
        return notBlank(properties.managerUrl()) || notBlank(properties.indexerUrl());
    }

    public Map<String, Object> managerHealth() {
        if (!notBlank(properties.managerUrl())) {
            return Map.of("configured", false, "message", "Wazuh Manager URL 未配置，P0 使用模拟告警数据");
        }
        if (!notBlank(managerUsername()) || !notBlank(managerPassword())) {
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Manager 凭据未配置");
        }
        try {
            String token = authenticateManager();
            HttpResult response = request("GET", properties.managerUrl(), bearerAuth(token));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Map.of("configured", true, "status", "CONNECTED", "response", parseJson(response.body()));
            }
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Manager 返回 HTTP " + response.statusCode());
        } catch (RuntimeException ex) {
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Manager 连接失败：" + ex.getClass().getSimpleName());
        }
    }

    public Map<String, Object> indexerHealth() {
        if (!notBlank(properties.indexerUrl())) {
            return Map.of("configured", false, "message", "Wazuh Indexer URL 未配置，P0 使用 MySQL 业务态数据");
        }
        if (!notBlank(indexerUsername()) || !notBlank(indexerPassword())) {
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Indexer 凭据未配置");
        }
        try {
            HttpResult response = request("GET", properties.indexerUrl(), basicAuth(indexerUsername(), indexerPassword()));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Map.of("configured", true, "status", "CONNECTED", "response", parseJson(response.body()));
            }
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Indexer 返回 HTTP " + response.statusCode());
        } catch (RuntimeException ex) {
            return Map.of("configured", true, "status", "FAILED", "message", "Wazuh Indexer 连接失败：" + ex.getClass().getSimpleName());
        }
    }

    private String authenticateManager() {
        try {
            HttpResult response = request(
                    "POST",
                    properties.managerUrl() + "/security/user/authenticate",
                    basicAuth(managerUsername(), managerPassword())
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Wazuh Manager auth returned HTTP " + response.statusCode());
            }
            Object token = parseJson(response.body()).get("data");
            if (token instanceof Map<?, ?> data && data.get("token") instanceof String value && notBlank(value)) {
                return value;
            }
            throw new IllegalStateException("Wazuh Manager auth token missing");
        } catch (Exception ex) {
            throw new IllegalStateException("Wazuh Manager auth failed", ex);
        }
    }

    private HttpResult request(String method, String url, String authorization) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            if (connection instanceof HttpsURLConnection https && Boolean.FALSE.equals(properties.tlsVerify())) {
                https.setSSLSocketFactory(trustAllSslContext().getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            }
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod(method);
            connection.setRequestProperty(HttpHeaders.ACCEPT, "application/json");
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, authorization);
            connection.connect();
            int statusCode = connection.getResponseCode();
            return new HttpResult(statusCode, readBody(connection, statusCode));
        } catch (Exception ex) {
            throw new IllegalStateException("Wazuh request failed", ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private SSLContext trustAllSslContext() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize Wazuh TLS context", ex);
        }
    }

    private Map<String, Object> parseJson(String body) {
        try {
            return objectMapper.readValue(body, JSON_MAP);
        } catch (Exception ex) {
            return Map.of("raw", body == null ? "" : body);
        }
    }

    private String bearerAuth(String token) {
        return "Bearer " + token;
    }

    private String basicAuth(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String managerUsername() {
        return firstNotBlank(properties.managerUsername(), properties.username());
    }

    private String managerPassword() {
        return firstNotBlank(properties.managerPassword(), properties.password());
    }

    private String indexerUsername() {
        return firstNotBlank(properties.indexerUsername(), properties.username());
    }

    private String indexerPassword() {
        return firstNotBlank(properties.indexerPassword(), properties.password());
    }

    private String firstNotBlank(String first, String second) {
        return notBlank(first) ? first : second;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record HttpResult(int statusCode, String body) {
    }
}
