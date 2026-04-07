package com.spring.jwt.ProductBuyPending;

//import com.spring.jwt.Payment.CcAvenueConfig;
//import com.spring.jwt.Payment.CcAvenueUtil;
//import com.spring.jwt.entity.ProductBuyPending;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//
//@Service
//@RequiredArgsConstructor
public class CcAvenuePaymentRequestService {

//    private final CcAvenueConfig config;
//
//    @Value("${ccavenue.redirectUrl}")
//    private String redirectUrl;
//
//    @Value("${ccavenue.cancelUrl}")
//    private String cancelUrl;
//
//    @Value("${ccavenue.paymentUrl}")
//    private String paymentUrl;
//
//    public String generatePaymentForm(ProductBuyPending order) {
//
//        try {
//
//            String data =
//                    "merchant_id=" + config.getMerchantId() +
//                            "&order_id=" + order.getProductBuyPendingId() +
//                            "&currency=INR" +
//                            // ✅ FIX 1: amount format
//                            "&amount=" + String.format("%.2f", order.getTotalAmount()) +
//                            "&redirect_url=" + URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8) +
//                            "&cancel_url=" + URLEncoder.encode(cancelUrl, StandardCharsets.UTF_8) +
//                            "&language=EN" +
//                            // ✅ FIX 2: encode all user fields
//                            "&billing_name=" + URLEncoder.encode(order.getCustomerName(), StandardCharsets.UTF_8) +
//                            "&billing_address=" + URLEncoder.encode(order.getDeliveryAddress(), StandardCharsets.UTF_8) +
//                            "&billing_tel=" + URLEncoder.encode(order.getContactNumber(), StandardCharsets.UTF_8);
//
//            // ✅ FIX 3: use correct working key
//            String encrypted = CcAvenueUtil.encrypt(data, config.getWorkingKey());
//
//            return "<html><body onload='document.forms[0].submit()'>" +
//                    "<form method='post' action='" + paymentUrl + "'>" +
//                    "<input type='hidden' name='encRequest' value='" + encrypted + "'/>" +
//                    "<input type='hidden' name='access_code' value='" + config.getAccessCode() + "'/>" +
//                    "</form></body></html>";
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error generating CCAvenue payment form", e);
//        }
//    }
}