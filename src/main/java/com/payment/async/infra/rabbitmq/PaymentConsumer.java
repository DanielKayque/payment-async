package com.payment.async.infra.rabbitmq;

import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import com.payment.async.features.payment.processPayment.ProcessPaymentUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentConsumer {

    @Autowired
    public ProcessPaymentUseCase useCase;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE)
    @Transactional
    public void consume(PaymentCreatedEvent event){
        //TODO: Lógica do que o consumidor irá fazer

        useCase.execute(event);
    }
}
