package com.payment.async.features.payment.processPayment;

import com.payment.async.features.payment.createPayment.CreatePaymentRequestDto;
import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import com.payment.async.features.payment.createPayment.PaymentStatus;
import com.payment.async.infra.persistance.entities.PaymentEntity;
import com.payment.async.infra.persistance.out.AuthorizationClient;
import com.payment.async.infra.persistance.repository.PaymentRepository;
import com.payment.async.security.GenerateHash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class ProcessPaymentUseCaseTest {

    @InjectMocks
    private ProcessPaymentUseCase useCase;

    @Mock
    private AuthorizationClient authorizationClient;

    @Mock
    private PaymentRepository repository;

    PaymentEntity paymentEntity;

    CreatePaymentRequestDto dto;

    PaymentCreatedEvent event;

    @BeforeEach
    void setUp(){
        dto = new CreatePaymentRequestDto(100L);

        paymentEntity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .amountCents(100L)
                .idempotencyKey(UUID.randomUUID())
                .createdAt(Instant.now())
                .status(PaymentStatus.PENDING)
                .requestHash(GenerateHash.generateHash(dto))
                .updatedAt(Instant.now())
                .build();

         event = new PaymentCreatedEvent(paymentEntity.getId(),
                BigDecimal.valueOf(100L, 2),
                Instant.now(),
                PaymentStatus.PENDING);
    }

    @Test
    void shouldChangeStatusToApproved(){
        Mockito.when(authorizationClient.get()).thenReturn(true);
        Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(paymentEntity));

        useCase.execute(event);

        Assertions.assertEquals(PaymentStatus.APPROVED, paymentEntity.getStatus());
    }

    @Test
    void shouldDontChangeStatusToApproved(){
        Mockito.when(authorizationClient.get()).thenReturn(false);
        Mockito.when(repository.findById(Mockito.any(UUID.class))).thenReturn(Optional.of(paymentEntity));

        useCase.execute(event);

        Assertions.assertEquals(PaymentStatus.DECLINED, paymentEntity.getStatus());
    }
}
