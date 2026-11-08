package com.payment.async.features.payment.createPayment;

import com.payment.async.exceptions.IdempotencyKeyConflictException;
import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import com.payment.async.infra.persistance.repository.OutboxEventRepository;
import com.payment.async.infra.persistance.entities.PaymentEntity;
import com.payment.async.infra.persistance.repository.PaymentRepository;
import com.payment.async.security.GenerateHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentUseCase {
    private final PaymentRepository repository;

    private final OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper;

    public PaymentUseCase(ObjectMapper objectMapper, OutboxEventRepository outboxEventRepository, PaymentRepository repository) {
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
        this.repository = repository;
    }

    @Transactional
    public CreatePaymentResponseDto execute(UUID idempotencyKey, CreatePaymentRequestDto dto){

        Optional<PaymentEntity> payment = repository.findByIdempotencyKey(idempotencyKey);

        if (payment.isPresent()){
            String requestHash = GenerateHash.generateHash(dto);

            if (payment.get().getRequestHash().equals(requestHash)){
                PaymentEntity entity = payment.get();

                return new CreatePaymentResponseDto(BigDecimal.valueOf(entity.getAmountCents(), 2));
            } else {
                throw new IdempotencyKeyConflictException("Idempotency-Key already exists with different request");
            }
        }

        PaymentEntity paymentEntity = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .amountCents(dto.amountCents())
                .idempotencyKey(idempotencyKey)
                .status(PaymentStatus.PENDING)
                .requestHash(GenerateHash.generateHash(dto))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PaymentEntity entity = repository.save(paymentEntity);

        PaymentCreatedEvent paymentCreatedEvent = new PaymentCreatedEvent(paymentEntity.getId(),
                BigDecimal.valueOf(paymentEntity.getAmountCents(), 2),
                paymentEntity.getCreatedAt(),
                paymentEntity.getStatus());

        Map<String, Object> payload = objectMapper.convertValue(paymentCreatedEvent, Map.class);

        OutboxEventEntity eventEntity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateId(paymentEntity.getId())
                .createdAt(Instant.now())
                .payload(payload)
                .attempts(0)
                .publishedAt(null)
                .eventType("payment.created")
                .build();

        outboxEventRepository.save(eventEntity);

        return new CreatePaymentResponseDto(BigDecimal.valueOf(entity.getAmountCents(), 2));
    }
}
