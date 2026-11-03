package com.payment.async.features.payment;

import java.util.UUID;

public record PaymentRequestDto(UUID idempotencyKey, Long amountCents) {
}
