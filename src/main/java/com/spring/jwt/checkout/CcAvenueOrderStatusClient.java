package com.spring.jwt.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.Payment.CcAvenueConfig;
import com.spring.jwt.Payment.CcAvenueUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;

/**
 * Best-effort CCAvenue {@code orderStatusTracker} client for reconciliation when webhooks are delayed.
 * Request/response format follows CCAvenue merchant API (AES enc_request + enc_response).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CcAvenueOrderStatusClient {

    private final CcAvenueConfig ccAvenueConfig;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public Optional<CcAvenueOrderStatusResult> fetchStatus(String merchantOrderId) {
        return fetchDecryptedOrderStatusJson(merchantOrderId).map(node -> new CcAvenueOrderStatusResult(
                merchantOrderId,
                text(node, "order_status"),
                firstNonBlank(text(node, "order_amt"), text(node, "amount")),
                firstNonBlank(text(node, "tracking_id"), text(node, "reference_no")),
                text(node, "payment_mode"),
                firstNonBlank(text(node, "status_message"), text(node, "order_status"))
        ));
    }

    /**
     * Decrypted order-status JSON for refund reconciliation (field names vary by CCAvenue version).
     */
    public Optional<JsonNode> fetchDecryptedOrderStatusJson(String merchantOrderId) {
        String baseUrl = ccAvenueConfig.getStatusApiUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        String workingKey = ccAvenueConfig.getWorkingKey();
        String accessCode = ccAvenueConfig.getAccessCode();
        if (workingKey == null || accessCode == null) {
            return Optional.empty();
        }
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("reference_no", "");
            payload.put("order_no", merchantOrderId);
            String json = objectMapper.writeValueAsString(payload);
            String encRequest = CcAvenueUtil.encrypt(json, workingKey);

            String form = "enc_request=" + url(encRequest)
                    + "&access_code=" + url(accessCode)
                    + "&command=orderStatusTracker"
                    + "&request_type=JSON"
                    + "&response_type=JSON"
                    + "&version=1.2";

            String url = baseUrl.contains("DoWebTrans") ? baseUrl : joinUrl(baseUrl, "/apis/servlet/DoWebTrans");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, String> outer = parseKeyValue(response.body());
            String status = outer.get("status");
            if (status != null && !"0".equals(status.trim())) {
                String errorMsg = outer.get("error");
                log.warn("CCAvenue status API returned status={} error={} for order {}", status, errorMsg, merchantOrderId);
                String synth = String.format("{\"order_status\":\"Failure\",\"status_message\":\"%s\"}", 
                        errorMsg != null ? errorMsg.replace("\"", "\\\"") : ("API Error " + status));
                return Optional.of(objectMapper.readTree(synth));
            }
            String encResponse = outer.get("enc_response");
            if (encResponse == null || encResponse.isBlank()) {
                log.warn("CCAvenue status API response invalid! Missing enc_response. Body snippet: {}", 
                        response.body() != null && response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
                return Optional.empty();
            }
            String decrypted = CcAvenueUtil.decrypt(encResponse.trim(), workingKey);
            return Optional.of(objectMapper.readTree(decrypted));
        } catch (Exception e) {
            log.warn("CCAvenue order status lookup failed for {}: {}", merchantOrderId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        return base + path;
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseKeyValue(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null) {
            return map;
        }
        StringTokenizer tokenizer = new StringTokenizer(body, "&");
        while (tokenizer.hasMoreTokens()) {
            String pair = tokenizer.nextToken();
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String value = idx < pair.length() - 1 ? pair.substring(idx + 1).trim() : "";
                map.put(key, value);
            }
        }
        return map;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    public record CcAvenueOrderStatusResult(
            String merchantOrderId,
            String orderStatus,
            String amount,
            String trackingId,
            String paymentMode,
            String statusMessage
    ) {
    }
}
