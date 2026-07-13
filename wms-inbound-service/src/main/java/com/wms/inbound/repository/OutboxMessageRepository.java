package com.wms.inbound.repository;

import com.wms.inbound.entity.OutboxMessage;
import com.wms.inbound.entity.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByStatus(OutboxStatus status);
}
