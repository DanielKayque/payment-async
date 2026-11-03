package com.payment.async.infra.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitMQConfig {
    public static final String PAYMENT_QUEUE = "payment.processing";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_ROUTING_KEY = "paymenct.created";

    @Bean
    public Queue paymentQueue(){
        return new Queue(PAYMENT_QUEUE, true, false, false);
    }

    @Bean
    public DirectExchange paymentExchange(){
        return new DirectExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Binding relationship(Queue queue, DirectExchange swap){
        return BindingBuilder
                .bind(paymentQueue())
                .to(paymentExchange())
                .with(PAYMENT_ROUTING_KEY);
    }
}
