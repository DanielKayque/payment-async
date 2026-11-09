package com.payment.async.infra.rabbitmq;

import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OutboxEventEntity event){
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_ROUTING_KEY,
                event.getPayload()
        );
    }
}
