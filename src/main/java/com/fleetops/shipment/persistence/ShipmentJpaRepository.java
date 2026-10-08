package com.fleetops.shipment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentEntity, UUID> {
}
