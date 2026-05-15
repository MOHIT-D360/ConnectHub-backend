package com.connecthub.payment.controller;

import com.connecthub.payment.dto.CreateSubscriptionRequest;
import com.connecthub.payment.dto.PaymentVerificationResponse;
import com.connecthub.payment.dto.PaymentResponse;
import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.dto.VerifyPaymentRequest;
import com.connecthub.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Subscription management endpoints.
 * All routes require authentication (JWT via X-User-Id header injected by gateway).
 */
@RestController
@RequestMapping("/api/v1/payments/subscription")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscription", description = "Manage ConnectHub PRO subscriptions via Razorpay")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.pro-plan-id:}")
    private String defaultPlanId;

    @GetMapping("/config")
    @Operation(summary = "Get Razorpay checkout config")
    public ResponseEntity<Map<String, String>> checkoutConfig() {
        return ResponseEntity.ok(Map.of(
                "keyId", razorpayKeyId,
                "defaultPlanId", defaultPlanId
        ));
    }

    /**
     * Creates (or returns existing) Razorpay subscription for the calling user.
     * The returned subscriptionId is passed to Razorpay Checkout on the frontend.
     */
    @PostMapping("/create")
    @Operation(summary = "Create subscription", description = "Initiates a new Razorpay subscription for the authenticated user")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CreateSubscriptionRequest req) {

        req.setUserId(userId);
        SubscriptionResponse response = subscriptionService.createSubscription(
                userId, req.getPlanId(), req.getTotalCount(), userEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify Razorpay payment", description = "Verifies checkout signature and activates the subscription")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            @RequestHeader("X-User-Id") Integer userId,
            @Valid @RequestBody VerifyPaymentRequest request) {

        log.info("Payment verification request userId={} paymentId={} subscriptionId={}",
                userId, request.getRazorpay_payment_id(), request.getRazorpay_subscription_id());

        PaymentVerificationResponse response = subscriptionService.verifyPayment(userId, request);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    /**
     * Cancels the current Razorpay subscription for the calling user.
     */
    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancels the authenticated user's active Razorpay subscription")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody(required = false) Map<String, String> request) {

        String razorpaySubId = request != null ? request.get("razorpaySubId") : null;
        SubscriptionResponse response = subscriptionService.cancelUserSubscription(userId, razorpaySubId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the current subscription status for the calling user.
     */
    @GetMapping("/status")
    @Operation(summary = "Get subscription status")
    public ResponseEntity<SubscriptionResponse> getStatus(
            @RequestHeader("X-User-Id") Integer userId) {
        SubscriptionResponse response = subscriptionService.getSubscription(userId)
                .orElseGet(() -> defaultFreeSubscription(userId));
        return ResponseEntity.ok(response);
    }

    /**
     * Returns payment history for the calling user's subscription.
     */
    @GetMapping("/payments")
    @Operation(summary = "Get payment history")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(
            @RequestHeader("X-User-Id") Integer userId) {
        List<PaymentResponse> history = subscriptionService.getPaymentHistory(userId);
        return ResponseEntity.ok(history);
    }

    private SubscriptionResponse defaultFreeSubscription(Integer userId) {
        LocalDateTime now = LocalDateTime.now();
        return SubscriptionResponse.builder()
                .userId(userId)
                .plan("FREE")
                .status("ACTIVE")
                .startDate(now)
                .createdAt(now)
                .build();
    }
}
