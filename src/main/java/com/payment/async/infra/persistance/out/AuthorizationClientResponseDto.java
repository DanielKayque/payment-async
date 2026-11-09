package com.payment.async.infra.persistance.out;

public record AuthorizationClientResponseDto(String status, AuthorizationDto data) {
}

