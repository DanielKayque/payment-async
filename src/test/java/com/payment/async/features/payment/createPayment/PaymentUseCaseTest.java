package com.payment.async.features.payment.createPayment;

import com.payment.async.exceptions.IdempotencyKeyConflictException;
import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import com.payment.async.infra.persistance.entities.PaymentEntity;
import com.payment.async.infra.persistance.repository.OutboxEventRepository;
import com.payment.async.infra.persistance.repository.PaymentRepository;
import com.payment.async.security.GenerateHash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentUseCaseTest {

    @InjectMocks
    private PaymentUseCase useCase;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private UUID idempotencyKey;
    private CreatePaymentRequestDto request;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID();
        request = new CreatePaymentRequestDto(1_500L);
    }

    @Test
    void shouldCreatePendingPaymentAndOutboxEventWhenKeyIsNew() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.convertValue(any(PaymentCreatedEvent.class), eq(Map.class))).thenReturn(Map.of("paymentId", "id"));

        CreatePaymentResponseDto response = useCase.execute(idempotencyKey, request);

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        ArgumentCaptor<OutboxEventEntity> eventCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        verify(outboxEventRepository).save(eventCaptor.capture());

        PaymentEntity savedPayment = paymentCaptor.getValue();
        OutboxEventEntity savedEvent = eventCaptor.getValue();
        Assertions.assertEquals(new BigDecimal("15.00"), response.amount());
        Assertions.assertEquals(idempotencyKey, savedPayment.getIdempotencyKey());
        Assertions.assertEquals(1_500L, savedPayment.getAmountCents());
        Assertions.assertEquals(PaymentStatus.PENDING, savedPayment.getStatus());
        Assertions.assertEquals(GenerateHash.generateHash(request), savedPayment.getRequestHash());
        Assertions.assertEquals(savedPayment.getId(), savedEvent.getAggregateId());
        Assertions.assertEquals("payment.created", savedEvent.getEventType());
        Assertions.assertEquals(0, savedEvent.getAttempts());
        Assertions.assertNull(savedEvent.getPublishedAt());
    }

    @Test
    void shouldReturnExistingPaymentWhenRequestIsIdempotent() {
        PaymentEntity existingPayment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .amountCents(1_500L)
                .requestHash(GenerateHash.generateHash(request))
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        CreatePaymentResponseDto response = useCase.execute(idempotencyKey, request);

        Assertions.assertEquals(new BigDecimal("15.00"), response.amount());
        verify(paymentRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldThrowConflictWhenExistingKeyHasDifferentRequest() {
        PaymentEntity existingPayment = PaymentEntity.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash("different-request-hash")
                .amountCents(2_000L)
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        Assertions.assertThrows(IdempotencyKeyConflictException.class,
                () -> useCase.execute(idempotencyKey, request));

        verify(paymentRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
