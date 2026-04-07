package com.spring.jwt.Payment;

import com.spring.jwt.entity.ProductBuyPending;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;

@Service
@RequiredArgsConstructor
public class CcAvenuePaymentService {

    private final CcAvenueConfig config;

    public String generatePaymentForm(ProductBuyPending order) {

        // 🔥 Format amount (CCAvenue requires 2 decimal)
        String amount = new DecimalFormat("0.00")
                .format(order.getTotalAmount());

        // 🔥 Build request safely
        StringBuilder data = new StringBuilder();

        data.append("merchant_id=").append(config.getMerchantId())
                .append("&order_id=").append(order.getProductBuyPendingId())
                .append("&currency=INR")
                .append("&amount=").append(amount)
                .append("&redirect_url=").append(config.getRedirectUrl())
                .append("&cancel_url=").append(config.getCancelUrl())
                .append("&language=EN")

                // Billing (null-safe)
                .append("&billing_name=").append(nullSafe(order.getCustomerName()))
                .append("&billing_address=").append(nullSafe(order.getDeliveryAddress()))
                .append("&billing_tel=").append(nullSafe(order.getContactNumber()));

        // 🔐 Encrypt
        String encrypted = CcAvenueUtil.encrypt(data.toString(), config.getWorkingKey());

        // 🔥 Return auto-submit HTML form
        return "<html><body onload='document.forms[0].submit()'>" +
                "<form method='post' action='https://test.ccavenue.com/transaction/transaction.do?command=initiateTransaction'>" +
                "<input type='hidden' name='encRequest' value='" + encrypted + "'/>" +
                "<input type='hidden' name='access_code' value='" + config.getAccessCode() + "'/>" +
                "</form></body></html>";
    }

    // ✅ Null-safe helper
    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}