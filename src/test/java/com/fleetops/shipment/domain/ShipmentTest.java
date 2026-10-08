package com.fleetops.shipment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipmentTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsRequestedShipmentWithRequiredInstructions() {
        Shipment shipment = Shipment.request(ID, CUSTOMER_ID, CREATED_AT, "Use north gate", "Leave at dock 2");

        assertEquals(ID, shipment.id());
        assertEquals(CUSTOMER_ID, shipment.customerId());
        assertEquals(ShipmentStatus.REQUESTED, shipment.status());
        assertThrows(IllegalArgumentException.class,
                () -> Shipment.request(ID, CUSTOMER_ID, CREATED_AT, " ", "delivery"));
    }

    @Test
    void requestedShipmentCanBeCancelled() {
        assertEquals(ShipmentStatus.CANCELLED, requested().cancelBeforeDispatch().status());
    }

    @Test
    void dispatchedShipmentCannotBeCancelledThroughPreDispatchOperation() {
        Shipment dispatched = Shipment.reconstitute(ID, CUSTOMER_ID, ShipmentStatus.DISPATCHED, CREATED_AT,
                "pickup", "delivery");
        assertThrows(IllegalShipmentTransitionException.class, dispatched::cancelBeforeDispatch);
    }

    @Test
    void terminalStatesCannotReopenOrBeCancelledAgain() {
        Shipment cancelled = requested().cancelBeforeDispatch();
        assertThrows(IllegalShipmentTransitionException.class, cancelled::cancelBeforeDispatch);
        Shipment delivered = Shipment.reconstitute(ID, CUSTOMER_ID, ShipmentStatus.DELIVERED, CREATED_AT,
                "pickup", "delivery");
        assertThrows(IllegalShipmentTransitionException.class, delivered::cancelBeforeDispatch);
    }

    private Shipment requested() {
        return Shipment.request(ID, CUSTOMER_ID, CREATED_AT, "pickup", "delivery");
    }
}
