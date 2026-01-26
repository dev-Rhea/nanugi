package com.payment.paymentservice.controller;

import com.payment.paymentservice.dto.request.BatchPaymentRequest;
import com.payment.paymentservice.dto.request.ProcessPaymentRequest;
import com.payment.paymentservice.dto.request.RefundRequest;
import com.payment.paymentservice.dto.response.BatchPaymentResponse;
import com.payment.paymentservice.dto.response.PaymentResponse;
import com.payment.paymentservice.service.GroupBuyPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final GroupBuyPaymentService paymentService;

    /**
     * 단건 결제 처리
     */
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 일괄 결제 처리 (공동구매 확정 시)
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchPaymentResponse> processBatchPayment(@Valid @RequestBody BatchPaymentRequest request) {
        BatchPaymentResponse response = paymentService.processBatchPayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 환불 처리
     */
    @PostMapping("/refund")
    public ResponseEntity<PaymentResponse> refundPayment(@Valid @RequestBody RefundRequest request) {
        PaymentResponse response = paymentService.refundPayment(request);
        return ResponseEntity.ok(response);
    }
}
