package com.fleetops.shipment.persistence;

import com.fleetops.shipment.domain.Shipment;
import com.fleetops.shipment.domain.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment")
public class ShipmentEntity {
    @Id
    @Column(name = "shipment_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "pickup_instructions", nullable = false)
    private String pickupInstructions;

    @Column(name = "delivery_instructions", nullable = false)
    private String deliveryInstructions;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ShipmentEntity() { }

    public static ShipmentEntity from(Shipment shipment) {
        ShipmentEntity entity = new ShipmentEntity();
        entity.id = shipment.id();
        entity.customerId = shipment.customerId();
        entity.status = shipment.status();
        entity.createdAt = shipment.createdAt();
        entity.pickupInstructions = shipment.pickupInstructions();
        entity.deliveryInstructions = shipment.deliveryInstructions();
        return entity;
    }

    public Shipment toDomain() {
        return Shipment.reconstitute(id, customerId, status, createdAt, pickupInstructions, deliveryInstructions);
    }

    public void apply(Shipment shipment) {
        if (!id.equals(shipment.id()) || !customerId.equals(shipment.customerId())) {
            throw new IllegalArgumentException("Cannot apply a different shipment identity");
        }
        status = shipment.status();
        pickupInstructions = shipment.pickupInstructions();
        deliveryInstructions = shipment.deliveryInstructions();
    }
}
