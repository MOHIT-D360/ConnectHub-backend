package com.connecthub.payment.service;

import com.connecthub.payment.dto.SubscriptionResponse;
import com.connecthub.payment.dto.PaymentVerificationResponse;
import com.connecthub.payment.dto.PaymentResponse;
import com.connecthub.payment.dto.VerifyPaymentRequest;
import com.connecthub.payment.entity.Payment;
import com.connecthub.payment.entity.Subscription;
import com.connecthub.payment.repository.PaymentRepository;
import com.connecthub.payment.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.PaymentClient;
import com.razorpay.SubscriptionClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private RazorpayClient razorpayClient;
    @Mock private SubscriptionRepository subscriptionRepo;
    @Mock private PaymentRepository paymentRepo;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private StringRedisTemplate redis;

    @InjectMocks
    private SubscriptionService svc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(svc, "razorpayKeyId", "test_key");
        ReflectionTestUtils.setField(svc, "razorpayKeySecret", "test_secret");
    }

    @Test
    void createSubscription_existingActive_returnsExisting() {
        Subscription existing = Subscription.builder().userId(1).status("ACTIVE").plan("PRO").razorpaySubId("sub_1").build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(existing));

        SubscriptionResponse res = svc.createSubscription(1, "plan_1", 12, "test@test.com");

        assertEquals("sub_1", res.getRazorpaySubId());
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_activated_upgradesExisting() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_1\"}}}");
        Subscription existing = new Subscription();
        existing.setUserId(1);
        existing.setRazorpaySubId("sub_1");
        
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("subscription.activated", payload);

        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("PRO", existing.getPlan());
        verify(subscriptionRepo).save(existing);
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), eventCaptor.capture());
        JSONObject sentEvent = new JSONObject(eventCaptor.getValue());
        assertEquals(1, sentEvent.getInt("userId"));
        assertEquals("PRO", sentEvent.getString("status"));
    }

    @Test
    void handleWebhookEvent_activated_createsNewFromNotes() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_new\", \"notes\":{\"userId\":2}}}}");
        when(subscriptionRepo.findByRazorpaySubId("sub_new")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByUserId(2)).thenReturn(Optional.empty());

        svc.handleWebhookEvent("subscription.activated", payload);

        verify(subscriptionRepo).save(argThat(sub -> sub.getUserId() == 2 && "PRO".equals(sub.getPlan())));
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("2"), anyString());
    }

    @Test
    void handleWebhookEvent_cancelled_cancelsExisting() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_1\"}}}");
        Subscription existing = new Subscription();
        existing.setUserId(1);
        existing.setRazorpaySubId("sub_1");
        
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("subscription.cancelled", payload);

        assertEquals("CANCELLED", existing.getStatus());
        assertEquals("FREE", existing.getPlan());
        assertNotNull(existing.getEndDate());
        verify(subscriptionRepo).save(existing);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
    }

    @Test
    void handleWebhookEvent_paymentCaptured_recordsPayment() {
        JSONObject payload = new JSONObject("{\"payment\":{\"entity\":{\"id\":\"pay_1\", \"amount\":10000, \"currency\":\"INR\", \"subscription_id\":\"sub_1\"}}}");
        Subscription existing = Subscription.builder().id(10L).razorpaySubId("sub_1").build();
        
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("payment.captured", payload);

        verify(paymentRepo).save(argThat(p -> 
            "pay_1".equals(p.getRazorpayPaymentId()) && 
            "CAPTURED".equals(p.getStatus()) &&
            10L == p.getSubscriptionId()
        ));
    }

    @Test
    void createSubscription_reusesExistingFreeRowAndStoresEmailInNotes() throws Exception {
        Subscription existing = Subscription.builder()
                .id(3L)
                .userId(1)
                .status("ACTIVE")
                .plan("FREE")
                .build();
        SubscriptionClient subscriptionClient = mock(SubscriptionClient.class);
        razorpayClient.subscriptions = subscriptionClient;
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(existing));
        when(subscriptionClient.create(any(JSONObject.class))).thenReturn(
                new com.razorpay.Subscription(new JSONObject("{\"id\":\"sub_new\"}")));
        when(subscriptionRepo.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        SubscriptionResponse response = svc.createSubscription(1, "plan_pro", 12, "user@test.com");

        assertEquals("sub_new", response.getRazorpaySubId());
        assertEquals("PRO", existing.getPlan());
        assertEquals("PENDING", existing.getStatus());
        assertEquals("user@test.com", existing.getUserEmail());
        ArgumentCaptor<JSONObject> optionsCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(subscriptionClient).create(optionsCaptor.capture());
        JSONObject options = optionsCaptor.getValue();
        assertEquals("plan_pro", options.getString("plan_id"));
        assertEquals(1, options.getJSONObject("notes").getInt("userId"));
        assertEquals("user@test.com", options.getJSONObject("notes").getString("userEmail"));
    }

    @Test
    void createSubscription_razorpayFailureWrapsException() throws Exception {
        SubscriptionClient subscriptionClient = mock(SubscriptionClient.class);
        razorpayClient.subscriptions = subscriptionClient;
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.empty());
        when(subscriptionClient.create(any(JSONObject.class))).thenThrow(new RuntimeException("gateway down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> svc.createSubscription(1, "plan_pro", 12, null));

        assertTrue(ex.getMessage().contains("Could not initiate subscription"));
    }

    @Test
    void verifyPayment_nullRequestReturnsFailureWithoutThrowing() {
        PaymentVerificationResponse response = svc.verifyPayment(1, null);

        assertFalse(response.isSuccess());
        assertEquals("Payment verification request is required.", response.getMessage());
        verifyNoInteractions(paymentRepo, kafkaTemplate);
    }

    @Test
    void verifyPayment_invalidSignatureReturnsFailure() {
        VerifyPaymentRequest request = verifyRequest();
        try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifySubscription(any(JSONObject.class), eq("test_secret"))).thenReturn(false);

            PaymentVerificationResponse response = svc.verifyPayment(1, request);

            assertFalse(response.isSuccess());
            assertEquals("Payment signature verification failed.", response.getMessage());
        }
    }

    @Test
    void verifyPayment_subscriptionBelongsToDifferentUserReturnsFailure() {
        VerifyPaymentRequest request = verifyRequest();
        Subscription subscription = Subscription.builder().userId(99).razorpaySubId("sub_1").build();
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(subscription));

        try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifySubscription(any(JSONObject.class), eq("test_secret"))).thenReturn(true);

            PaymentVerificationResponse response = svc.verifyPayment(1, request);

            assertFalse(response.isSuccess());
            assertEquals("Payment does not belong to the authenticated user.", response.getMessage());
        }
    }

    @Test
    void verifyPayment_validSignatureActivatesSubscriptionRecordsPaymentAndQueuesReceipt() throws Exception {
        VerifyPaymentRequest request = verifyRequest();
        Subscription subscription = Subscription.builder()
                .id(42L)
                .userId(1)
                .userEmail("user@test.com")
                .plan("PRO")
                .status("PENDING")
                .razorpaySubId("sub_1")
                .build();
        PaymentClient paymentClient = mock(PaymentClient.class);
        razorpayClient.payments = paymentClient;
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(subscription));
        when(paymentClient.fetch("pay_1")).thenReturn(new com.razorpay.Payment(new JSONObject("""
                {"id":"pay_1","amount":19900,"currency":"INR","order_id":"order_1","status":"captured"}
                """)));
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepo.save(subscription)).thenReturn(subscription);
        when(subscriptionRepo.findById(42L)).thenReturn(Optional.of(subscription));

        try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.verifySubscription(any(JSONObject.class), eq("test_secret"))).thenReturn(true);

            PaymentVerificationResponse response = svc.verifyPayment(1, request);

            assertTrue(response.isSuccess());
            assertEquals("ACTIVE", subscription.getStatus());
            assertEquals("PRO", subscription.getPlan());
            assertEquals("pay_1", response.getPayment().getRazorpayPaymentId());
            assertEquals(BigDecimal.valueOf(19900, 2), response.getPayment().getAmount());
            verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
            verify(redis).convertAndSend(eq("email:send"), contains("user@test.com"));
        }
    }

    @Test
    void cancelUserSubscription_alreadyFreeReturnsCurrentSubscription() {
        Subscription subscription = Subscription.builder()
                .id(10L)
                .userId(1)
                .plan("FREE")
                .status("CANCELLED")
                .startDate(LocalDateTime.now())
                .build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(subscription));

        SubscriptionResponse response = svc.cancelUserSubscription(1, "ignored");

        assertEquals("FREE", response.getPlan());
        verifyNoMoreInteractions(kafkaTemplate);
        verify(subscriptionRepo, never()).save(any());
    }

    @Test
    void cancelUserSubscription_rejectsMismatchedRazorpayId() {
        Subscription subscription = Subscription.builder()
                .userId(1)
                .plan("PRO")
                .status("ACTIVE")
                .razorpaySubId("sub_owned")
                .build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(subscription));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> svc.cancelUserSubscription(1, "sub_other"));

        assertEquals("Subscription id does not belong to the authenticated user", ex.getMessage());
    }

    @Test
    void handleWebhookEvent_ignoresNullEventAndPayload() {
        svc.handleWebhookEvent(null, new JSONObject());
        svc.handleWebhookEvent("payment.captured", null);

        verifyNoInteractions(paymentRepo, kafkaTemplate);
    }

    @Test
    void handleWebhookEvent_completedExpiresExistingSubscription() {
        JSONObject payload = new JSONObject("{\"subscription\":{\"entity\":{\"id\":\"sub_1\"}}}");
        Subscription existing = Subscription.builder()
                .userId(1)
                .razorpaySubId("sub_1")
                .plan("PRO")
                .status("ACTIVE")
                .build();
        when(subscriptionRepo.findByRazorpaySubId("sub_1")).thenReturn(Optional.of(existing));

        svc.handleWebhookEvent("subscription.completed", payload);

        assertEquals("EXPIRED", existing.getStatus());
        assertEquals("FREE", existing.getPlan());
        assertNotNull(existing.getEndDate());
        verify(subscriptionRepo).save(existing);
        verify(kafkaTemplate).send(eq("user.subscription.status"), eq("1"), anyString());
    }

    @Test
    void handleWebhookEvent_paymentAlreadyRecordedSkipsSave() {
        JSONObject payload = new JSONObject("{\"payment\":{\"entity\":{\"id\":\"pay_1\", \"amount\":10000, \"subscription_id\":\"sub_1\"}}}");
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.of(new Payment()));

        svc.handleWebhookEvent("payment.failed", payload);

        verify(paymentRepo, never()).save(any());
    }

    @Test
    void handleWebhookEvent_paymentWithoutSubscriptionIdSkipsSave() {
        JSONObject payload = new JSONObject("{\"payment\":{\"entity\":{\"id\":\"pay_1\", \"amount\":10000}}}");
        when(paymentRepo.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());

        svc.handleWebhookEvent("payment.failed", payload);

        verify(paymentRepo, never()).save(any());
    }

    @Test
    void getSubscriptionAndPaymentHistoryMapEntities() {
        Subscription subscription = Subscription.builder().id(7L).userId(1).plan("PRO").status("ACTIVE").build();
        Payment payment = Payment.builder()
                .id(8L)
                .subscriptionId(7L)
                .razorpayPaymentId("pay_1")
                .razorpayOrderId("order_1")
                .amount(BigDecimal.valueOf(19900, 2))
                .currency("INR")
                .status("CAPTURED")
                .build();
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.of(subscription));
        when(paymentRepo.findBySubscriptionIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(payment));

        Optional<SubscriptionResponse> subscriptionResponse = svc.getSubscription(1);
        List<PaymentResponse> history = svc.getPaymentHistory(1);

        assertTrue(subscriptionResponse.isPresent());
        assertEquals("PRO", subscriptionResponse.get().getPlan());
        assertEquals(1, history.size());
        assertEquals("pay_1", history.get(0).getRazorpayPaymentId());
        assertEquals(BigDecimal.valueOf(19900, 2), history.get(0).getAmount());
    }

    @Test
    void getPaymentHistory_noSubscriptionReturnsEmptyList() {
        when(subscriptionRepo.findByUserId(1)).thenReturn(Optional.empty());

        assertTrue(svc.getPaymentHistory(1).isEmpty());
        verifyNoInteractions(paymentRepo);
    }

    private VerifyPaymentRequest verifyRequest() {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpay_payment_id("pay_1");
        request.setRazorpay_subscription_id("sub_1");
        request.setRazorpay_signature("sig");
        return request;
    }
}
