package com.payment.async.features.payment.createPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCreatedEvent(UUID paymentId, BigDecimal amount, Instant createdAt, PaymentStatus status) {
}
