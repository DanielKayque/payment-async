package com.payment.async.features.payment.processPayment;

import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import com.payment.async.features.payment.createPayment.PaymentStatus;
import com.payment.async.infra.persistance.entities.PaymentEntity;
import com.payment.async.infra.persistance.out.AuthorizationClient;
import com.payment.async.infra.persistance.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPaymentUseCase {

    private final AuthorizationClient authorizationClient;

    private final PaymentRepository repository;

    public ProcessPaymentUseCase(AuthorizationClient authorizationClient, PaymentRepository repository) {
        this.authorizationClient = authorizationClient;
        this.repository = repository;
    }

    @Transactional
    public void execute(PaymentCreatedEvent event) {
        PaymentEntity entity = repository.findById(event.paymentId()).orElseThrow(IllegalArgumentException::new);

        if (authorizationClient.get()) {
            entity.changeStatus(PaymentStatus.APPROVED);
        } else {
            entity.changeStatus(PaymentStatus.DECLINED);
        }
    }
}
