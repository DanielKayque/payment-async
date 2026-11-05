package com.payment.async.features.payment.createPayment;

import java.math.BigDecimal;

public record CreatePaymentResponseDto(BigDecimal amount) {
}
