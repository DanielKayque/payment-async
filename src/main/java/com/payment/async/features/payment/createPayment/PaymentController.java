package com.payment.async.features.payment.createPayment;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentUseCase useCase;

    @PostMapping
    public ResponseEntity<CreatePaymentResponseDto> post(
            @RequestHeader(value = "Idempotency-Key", required = true)UUID idempotencyKey,
            @Valid @RequestBody CreatePaymentRequestDto dto
            ){

        CreatePaymentResponseDto response = useCase.execute(idempotencyKey,dto);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    };
}
