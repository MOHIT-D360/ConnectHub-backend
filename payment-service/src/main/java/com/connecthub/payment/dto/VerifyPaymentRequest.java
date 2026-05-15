package com.connecthub.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyPaymentRequest {
    @NotBlank
    private String razorpay_payment_id;

    @NotBlank
    private String razorpay_subscription_id;

    @NotBlank
    private String razorpay_signature;
}
