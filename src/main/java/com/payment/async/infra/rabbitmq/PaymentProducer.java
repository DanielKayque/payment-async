package com.payment.async.infra.rabbitmq;

import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publish(PaymentCreatedEvent event){
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_ROUTING_KEY,
                event
        );
    }

}
