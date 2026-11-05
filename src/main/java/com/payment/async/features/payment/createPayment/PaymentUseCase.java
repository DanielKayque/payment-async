package com.payment.async.features.payment.createPayment;

import com.payment.async.exceptions.IdempotencyKeyConflictException;
import com.payment.async.infra.rabbitmq.PaymentProducer;
import com.payment.async.infra.persistance.entities.PaymentEntity;
import com.payment.async.infra.persistance.repository.PaymentRepository;
import com.payment.async.security.GenerateHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentUseCase {
    @Autowired
    private PaymentRepository repository;
    @Autowired
    private PaymentProducer producer;

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

        producer.publish(paymentCreatedEvent);

        return new CreatePaymentResponseDto(BigDecimal.valueOf(entity.getAmountCents(), 2));
    }
}
