package com.fleetops.shipment.domain;

public final class IllegalShipmentTransitionException extends RuntimeException {
    public IllegalShipmentTransitionException(ShipmentStatus from, ShipmentStatus to) {
        super("Shipment cannot transition from %s to %s".formatted(from, to));
    }
}
