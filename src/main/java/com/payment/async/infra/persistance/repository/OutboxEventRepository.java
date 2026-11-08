package com.payment.async.infra.persistance.repository;

import com.payment.async.infra.persistance.entities.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.publishedAt IS NULL")
    List<OutboxEventEntity> findByPublishedAtIsNull();
}
