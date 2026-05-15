package com.connecthub.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentVerificationResponse {
    private boolean success;
    private String message;
    private SubscriptionResponse subscription;
    private PaymentResponse payment;
}
