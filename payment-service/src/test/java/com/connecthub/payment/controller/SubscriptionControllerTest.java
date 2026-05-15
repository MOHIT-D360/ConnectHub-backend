package com.connecthub.payment.controller;

import com.connecthub.payment.dto.CreateSubscriptionRequest;
import com.connecthub.payment.dto.PaymentVerificationResponse;
import com.connecthub.payment.dto.PaymentResponse;
import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.dto.VerifyPaymentRequest;
import com.connecthub.payment.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void createSubscription() {
        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlanId("plan_1");
        req.setTotalCount(12);
        
        SubscriptionResponse sr = SubscriptionResponse.builder().build();
        when(subscriptionService.createSubscription(1, "plan_1", 12, null)).thenReturn(sr);

        ResponseEntity<SubscriptionResponse> res = subscriptionController.createSubscription(1, null, req);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(sr, res.getBody());
    }

    @Test
    void checkoutConfig_returnsConfiguredRazorpayValues() {
        ReflectionTestUtils.setField(subscriptionController, "razorpayKeyId", "rzp_key");
        ReflectionTestUtils.setField(subscriptionController, "defaultPlanId", "plan_default");

        ResponseEntity<Map<String, String>> res = subscriptionController.checkoutConfig();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("rzp_key", res.getBody().get("keyId"));
        assertEquals("plan_default", res.getBody().get("defaultPlanId"));
    }

    @Test
    void getStatus_found() {
        SubscriptionResponse sr = SubscriptionResponse.builder().build();
        when(subscriptionService.getSubscription(1)).thenReturn(Optional.of(sr));

        ResponseEntity<SubscriptionResponse> res = subscriptionController.getStatus(1);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(sr, res.getBody());
    }

    @Test
    void getStatus_notFoundReturnsFreeSubscription() {
        when(subscriptionService.getSubscription(1)).thenReturn(Optional.empty());

        ResponseEntity<SubscriptionResponse> res = subscriptionController.getStatus(1);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        SubscriptionResponse body = res.getBody();
        assertNotNull(body);
        assertEquals(1, body.getUserId());
        assertEquals("FREE", body.getPlan());
        assertEquals("ACTIVE", body.getStatus());
    }

    @Test
    void getPaymentHistory() {
        List<PaymentResponse> history = List.of(PaymentResponse.builder().build());
        when(subscriptionService.getPaymentHistory(1)).thenReturn(history);

        ResponseEntity<List<PaymentResponse>> res = subscriptionController.getPaymentHistory(1);
        
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(history, res.getBody());
    }

    @Test
    void verifyPayment_successReturnsOk() {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        PaymentVerificationResponse response = PaymentVerificationResponse.builder()
                .success(true)
                .message("ok")
                .build();
        when(subscriptionService.verifyPayment(1, request)).thenReturn(response);

        ResponseEntity<PaymentVerificationResponse> res = subscriptionController.verifyPayment(1, request);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(response, res.getBody());
    }

    @Test
    void verifyPayment_failureReturnsBadRequest() {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        PaymentVerificationResponse response = PaymentVerificationResponse.builder()
                .success(false)
                .message("bad signature")
                .build();
        when(subscriptionService.verifyPayment(1, request)).thenReturn(response);

        ResponseEntity<PaymentVerificationResponse> res = subscriptionController.verifyPayment(1, request);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(response, res.getBody());
    }

    @Test
    void cancelSubscription_passesRazorpayIdFromBody() {
        SubscriptionResponse response = SubscriptionResponse.builder().plan("FREE").build();
        when(subscriptionService.cancelUserSubscription(1, "sub_1")).thenReturn(response);

        ResponseEntity<SubscriptionResponse> res = subscriptionController.cancelSubscription(1, Map.of("razorpaySubId", "sub_1"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(response, res.getBody());
        verify(subscriptionService).cancelUserSubscription(1, "sub_1");
    }

    @Test
    void cancelSubscription_nullBodyPassesNullRazorpayId() {
        SubscriptionResponse response = SubscriptionResponse.builder().plan("FREE").build();
        when(subscriptionService.cancelUserSubscription(1, null)).thenReturn(response);

        ResponseEntity<SubscriptionResponse> res = subscriptionController.cancelSubscription(1, null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(response, res.getBody());
        verify(subscriptionService).cancelUserSubscription(1, null);
    }
}
