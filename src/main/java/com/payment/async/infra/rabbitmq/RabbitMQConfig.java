package com.payment.async.infra.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String PAYMENT_QUEUE = "payment.processing";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_ROUTING_KEY = "payment.created";
    public static final String PAYMENT_DLX = "payment.dlx";
    public static final String PAYMENT_DLQ = "payment.processing.dlq";
    public static final String PAYMENT_DLQ_ROUTING_KEY = "payment.created.dlq";

    @Bean
    public Queue paymentQueue(){
        return QueueBuilder
                .durable(PAYMENT_QUEUE)
                .deadLetterExchange(PAYMENT_DLX)
                .deadLetterRoutingKey(PAYMENT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange paymentExchange(){
        return new DirectExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public DirectExchange paymentDlx(){
        return new DirectExchange(PAYMENT_DLX);
    }

    @Bean
    public Queue paymentDlq(){
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    public Binding binding(){
        return BindingBuilder
                .bind(paymentQueue())
                .to(paymentExchange())
                .with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding paymentDlqBinding(){
        return BindingBuilder
                .bind(paymentDlq())
                .to(paymentDlx())
                .with(PAYMENT_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter(){
        return new JacksonJsonMessageConverter();
    }
}
