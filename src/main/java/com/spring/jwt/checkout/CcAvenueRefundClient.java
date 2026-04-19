package com.spring.jwt.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.Payment.CcAvenueConfig;
import com.spring.jwt.Payment.CcAvenueUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * CCAvenue {@code refundOrder} API (DoWebTrans). Payload format follows merchant API v1.2 style.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CcAvenueRefundClient {

    private final CcAvenueConfig ccAvenueConfig;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * @param ccaTrackingId original transaction tracking / reference_no from capture
     * @param refundRefNo   idempotent merchant reference (e.g. REF-{refundId})
     */
    public RefundApiResult submitRefund(String ccaTrackingId, BigDecimal amount, String refundRefNo) {
        String baseUrl = ccAvenueConfig.getStatusApiUrl();
        String workingKey = ccAvenueConfig.getWorkingKey();
        String accessCode = ccAvenueConfig.getAccessCode();
        String merchantId = ccAvenueConfig.getMerchantId();
        if (baseUrl == null || baseUrl.isBlank() || workingKey == null || accessCode == null) {
            return failed("Missing CCAvenue configuration for refund API");
        }
        if (ccaTrackingId == null || ccaTrackingId.isBlank()) {
            return failed("Missing CCAvenue tracking id for refund");
        }
        try {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("reference_no", ccaTrackingId);
            inner.put("refund_amount", amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            inner.put("refund_ref_no", refundRefNo);
            if (merchantId != null && !merchantId.isBlank()) {
                inner.put("merchant_id", merchantId);
            }
            String json = objectMapper.writeValueAsString(inner);
            String encRequest = CcAvenueUtil.encrypt(json, workingKey);
            String form = "enc_request=" + url(encRequest)
                    + "&access_code=" + url(accessCode)
                    + "&command=refundOrder"
                    + "&request_type=JSON"
                    + "&response_type=JSON"
                    + "&version=1.2";

            String url = baseUrl.contains("DoWebTrans") ? baseUrl : joinUrl(baseUrl, "/apis/servlet/DoWebTrans");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            Map<String, String> outer = parseKeyValue(body);
            String status = outer.get("status");
            if (status != null && !"0".equals(status.trim())) {
                return new RefundApiResult(false, body, outer.getOrDefault("enc_response", body), json, null);
            }
            String encResponse = outer.get("enc_response");
            if (encResponse == null || encResponse.isBlank()) {
                return new RefundApiResult(false, body, null, json, null);
            }
            String decrypted = CcAvenueUtil.decrypt(encResponse.trim(), workingKey);
            JsonNode node = tryParseJson(decrypted);
            boolean ok = interpretInnerRefundAccepted(node, decrypted);
            return new RefundApiResult(ok, body + " | INNER=" + decrypted, decrypted, json, node);
        } catch (Exception e) {
            log.warn("CCAvenue refund API error: {}", e.getMessage());
            return failed("Exception: " + e.getMessage());
        }
    }

    private JsonNode tryParseJson(String decrypted) {
        try {
            return objectMapper.readTree(decrypted);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Best-effort: outer envelope already {@code status=0}; treat obvious failure text as reject, else accept.
     */
    private boolean interpretInnerRefundAccepted(JsonNode node, String raw) {
        if (raw == null) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("invalid") && lower.contains("request")) {
            return false;
        }
        if (lower.contains("failure") || lower.contains(" rejected") || lower.contains("reject")) {
            return lower.contains("success");
        }
        if (node != null && node.has("refund_status")) {
            String rs = node.get("refund_status").asText("").toLowerCase(Locale.ROOT);
            if (rs.contains("fail") || rs.contains("reject")) {
                return false;
            }
        }
        return true;
    }

    private static RefundApiResult failed(String msg) {
        return new RefundApiResult(false, msg, null, null, null);
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

    public record RefundApiResult(
            boolean acceptedByGateway,
            String rawCombined,
            String rawInnerDecrypted,
            String requestJson,
            JsonNode innerJson
    ) {
    }
}
