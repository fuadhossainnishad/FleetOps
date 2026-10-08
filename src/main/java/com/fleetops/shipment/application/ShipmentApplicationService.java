package com.fleetops.shipment.application;

import com.fleetops.shipment.domain.Shipment;
import com.fleetops.shipment.persistence.ShipmentEntity;
import com.fleetops.shipment.persistence.ShipmentJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Owns the local transaction for Shipment creation and lifecycle changes. */
@Service
public class ShipmentApplicationService {
    private final ShipmentJpaRepository shipments;
    private final JdbcTemplate jdbc;

    public ShipmentApplicationService(ShipmentJpaRepository shipments, JdbcTemplate jdbc) {
        this.shipments = shipments;
        this.jdbc = jdbc;
    }

    @Transactional
    public Shipment create(UUID customerId, String pickupInstructions, String deliveryInstructions) {
        var activeStates = jdbc.query(
                "SELECT active FROM customer WHERE customer_id = ? FOR SHARE",
                (rs, rowNum) -> rs.getBoolean("active"), customerId);
        if (activeStates.size() != 1 || !activeStates.getFirst()) {
            throw new IllegalArgumentException("Customer does not exist or is inactive");
        }
        Shipment shipment = Shipment.request(UUID.randomUUID(), customerId,
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                pickupInstructions, deliveryInstructions);
        shipments.save(ShipmentEntity.from(shipment));
        return shipment;
    }

    @Transactional
    public Shipment cancelBeforeDispatch(UUID shipmentId) {
        ShipmentEntity entity = shipments.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException("Shipment does not exist"));
        Shipment cancelled = entity.toDomain().cancelBeforeDispatch();
        entity.apply(cancelled);
        return cancelled;
    }
}
