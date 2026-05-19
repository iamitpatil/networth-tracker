package com.networth.repository;

import com.networth.model.entity.BrokerConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BrokerConnectionRepository extends JpaRepository<BrokerConnection, UUID> {
    Optional<BrokerConnection> findByUserIdAndBroker(UUID userId, String broker);
    List<BrokerConnection> findByUserId(UUID userId);
    List<BrokerConnection> findByBrokerAndStatus(String broker, String status);
}
