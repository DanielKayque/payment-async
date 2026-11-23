package com.payment.async.features.outbox;

import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import com.payment.async.infra.persistance.repository.OutboxEventRepository;
import com.payment.async.infra.rabbitmq.RabbitPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
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
                if (event.getAttempts() >= 5){
                    log.error("RabbitMQ error in publishing event: {}", event.getId());

                  continue;
                }

                rabbitPublisher.publish(event);

                event.markAsPublished();

                outboxEventRepository.save(event);
            } catch (Exception ex){
                outboxEventRepository.save(event);
                event.incrementAttempts();
            }
        }
    }
}
