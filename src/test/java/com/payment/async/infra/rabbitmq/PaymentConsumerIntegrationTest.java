package com.payment.async.infra.rabbitmq;

import com.payment.async.features.payment.createPayment.PaymentCreatedEvent;
import com.payment.async.features.payment.createPayment.PaymentStatus;
import com.payment.async.features.payment.processPayment.ProcessPaymentUseCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
public class PaymentConsumerIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:4-management");

    @MockitoBean
    private ProcessPaymentUseCase useCase;

    PaymentCreatedEvent paymentCreatedEvent;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @BeforeEach
    public void setUp(){
        paymentCreatedEvent = new PaymentCreatedEvent(
                UUID.randomUUID(),
                BigDecimal.valueOf(100),
                Instant.now(),
                PaymentStatus.PENDING);
    }

    @Test
    void shouldConsumePaymentEvent(){
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_ROUTING_KEY,
                paymentCreatedEvent
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> Mockito.verify(useCase, Mockito.times(1)).execute(paymentCreatedEvent));
    }

    @Test
    void ShouldThrowErrorRetryAndSendToDlq(){
        Mockito.doThrow(new RuntimeException("Error")).when(useCase).execute(paymentCreatedEvent);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                RabbitMQConfig.PAYMENT_ROUTING_KEY,
                paymentCreatedEvent
        );

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> Mockito.verify(useCase, Mockito.times(3)).execute(paymentCreatedEvent));

        PaymentCreatedEvent dlqMessage = (PaymentCreatedEvent) rabbitTemplate.receiveAndConvert(RabbitMQConfig.PAYMENT_DLQ, 5000);

        Assertions.assertNotNull(dlqMessage, "Message should be in DLQ");
        Assertions.assertEquals(paymentCreatedEvent.paymentId(), dlqMessage.paymentId());
    }
}
