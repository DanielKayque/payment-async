package com.payment.async.features.payment.createPayment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequestDto(
        @NotNull
        @Positive
        Long amountCents) {
}
