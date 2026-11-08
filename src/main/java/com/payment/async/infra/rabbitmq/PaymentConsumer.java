package com.payment.async.infra.rabbitmq;

import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import com.payment.async.features.payment.processPayment.ProcessPaymentUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {

    @Autowired
    public ProcessPaymentUseCase useCase;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE)
    public void consume(PaymentCreatedEvent event){
        useCase.execute(event);
    }
}
