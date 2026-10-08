package com.fleetops.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Shipment aggregate. Lifecycle state can only change through its domain operations. */
public final class Shipment {
    private final UUID id;
    private final UUID customerId;
    private final ShipmentStatus status;
    private final Instant createdAt;
    private final String pickupInstructions;
    private final String deliveryInstructions;

    private Shipment(UUID id, UUID customerId, ShipmentStatus status, Instant createdAt,
                     String pickupInstructions, String deliveryInstructions) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.pickupInstructions = requiredInstruction(pickupInstructions, "pickupInstructions");
        this.deliveryInstructions = requiredInstruction(deliveryInstructions, "deliveryInstructions");
    }

    public static Shipment request(UUID id, UUID customerId, Instant createdAt,
                                   String pickupInstructions, String deliveryInstructions) {
        return new Shipment(id, customerId, ShipmentStatus.REQUESTED, createdAt,
                pickupInstructions, deliveryInstructions);
    }

    public static Shipment reconstitute(UUID id, UUID customerId, ShipmentStatus status, Instant createdAt,
                                        String pickupInstructions, String deliveryInstructions) {
        return new Shipment(id, customerId, status, createdAt, pickupInstructions, deliveryInstructions);
    }

    public Shipment cancelBeforeDispatch() {
        if (status != ShipmentStatus.REQUESTED) {
            throw new IllegalShipmentTransitionException(status, ShipmentStatus.CANCELLED);
        }
        return new Shipment(id, customerId, ShipmentStatus.CANCELLED, createdAt,
                pickupInstructions, deliveryInstructions);
    }

    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public ShipmentStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public String pickupInstructions() { return pickupInstructions; }
    public String deliveryInstructions() { return deliveryInstructions; }

    private static String requiredInstruction(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
