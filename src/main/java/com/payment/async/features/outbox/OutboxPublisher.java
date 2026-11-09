package com.payment.async.features.outbox;

import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import com.payment.async.infra.persistance.repository.OutboxEventRepository;
import com.payment.async.infra.rabbitmq.RabbitPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitPublisher rabbitPublisher;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitPublisher rabbitPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitPublisher = rabbitPublisher;
    }

    @Scheduled(fixedDelay = 30000)
    void publishEventPending(){
        List<OutboxEventEntity> events = outboxEventRepository.findByPublishedAtIsNull();

        for (OutboxEventEntity event : events){
            try{
                rabbitPublisher.publish(event);

                event.markAsPublished();

                outboxEventRepository.save(event);
            } catch (Exception ex){
                event.incrementAttempts();
                outboxEventRepository.save(event);
            }
        }
    }
}
