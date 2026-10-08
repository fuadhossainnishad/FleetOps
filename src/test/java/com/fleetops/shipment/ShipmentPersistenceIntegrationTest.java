package com.fleetops.shipment;

import com.fleetops.PostgresIntegrationTest;
import com.fleetops.shipment.application.ShipmentApplicationService;
import com.fleetops.shipment.domain.Shipment;
import com.fleetops.shipment.domain.ShipmentStatus;
import com.fleetops.shipment.persistence.ShipmentEntity;
import com.fleetops.shipment.persistence.ShipmentJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipmentPersistenceIntegrationTest extends PostgresIntegrationTest {
    @Autowired ShipmentApplicationService service;
    @Autowired ShipmentJpaRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void createsAndReadsShipmentFromPostgres() {
        UUID customerId = activeCustomer();

        Shipment created = service.create(customerId, "North gate", "Dock 4");
        Shipment loaded = repository.findById(created.id()).orElseThrow().toDomain();

        assertEquals(ShipmentStatus.REQUESTED, loaded.status());
        assertEquals(customerId, loaded.customerId());
        assertEquals("North gate", loaded.pickupInstructions());
        assertEquals("Dock 4", loaded.deliveryInstructions());
        assertEquals(created.createdAt(), loaded.createdAt());
    }

    @Test
    void postgresForeignKeyRejectsUnknownCustomer() {
        Shipment shipment = Shipment.request(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                "pickup", "delivery");

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(ShipmentEntity.from(shipment)));
    }

    @Test
    void transactionRollbackDoesNotCommitCancellation() {
        Shipment created = service.create(activeCustomer(), "pickup", "delivery");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.cancelBeforeDispatch(created.id());
            repository.flush();
            throw new IllegalStateException("force rollback");
        }));

        assertEquals(ShipmentStatus.REQUESTED, repository.findById(created.id()).orElseThrow().toDomain().status());
    }

    @Test
    void staleShipmentWriteFromIndependentTransactionIsRejectedByOptimisticVersion() throws Exception {
        Shipment created = service.create(activeCustomer(), "pickup", "delivery");
        CountDownLatch transactionBLoaded = new CountDownLatch(1);
        CountDownLatch transactionACommitted = new CountDownLatch(1);
        AtomicReference<Object> persistenceContextA = new AtomicReference<>();
        AtomicReference<Object> persistenceContextB = new AtomicReference<>();
        AtomicLong versionA = new AtomicLong(-1);
        AtomicLong versionB = new AtomicLong(-1);
        TransactionTemplate transactionA = new TransactionTemplate(transactionManager);
        TransactionTemplate transactionB = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<RuntimeException> staleAttempt = executor.submit(() -> {
                try {
                    transactionB.executeWithoutResult(status -> {
                        ShipmentEntity shipmentB = repository.findById(created.id()).orElseThrow();
                        persistenceContextB.set(
                                EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory));
                        versionB.set(databaseVersion(created.id()));
                        transactionBLoaded.countDown();
                        await(transactionACommitted);

                        shipmentB.apply(shipmentB.toDomain().cancelBeforeDispatch());
                        repository.flush();
                    });
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });

            await(transactionBLoaded);
            try {
                transactionA.executeWithoutResult(status -> {
                    ShipmentEntity shipmentA = repository.findById(created.id()).orElseThrow();
                    persistenceContextA.set(
                            EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory));
                    versionA.set(databaseVersion(created.id()));

                    shipmentA.apply(shipmentA.toDomain().cancelBeforeDispatch());
                    repository.flush();
                });
            } finally {
                transactionACommitted.countDown();
            }

            RuntimeException staleFailure = staleAttempt.get(10, TimeUnit.SECONDS);
            assertNotSame(persistenceContextA.get(), persistenceContextB.get(),
                    "transactions must use independent persistence contexts");
            assertEquals(0, versionA.get(), "transaction A must load the original version");
            assertEquals(0, versionB.get(), "transaction B must load the same original version");
            assertInstanceOf(ObjectOptimisticLockingFailureException.class, staleFailure);

            Shipment committed = repository.findById(created.id()).orElseThrow().toDomain();
            assertEquals(ShipmentStatus.CANCELLED, committed.status(), "transaction A's update must remain committed");
            assertEquals(1, databaseVersion(created.id()), "the successful update must advance the database version once");
        } finally {
            transactionACommitted.countDown();
            executor.shutdownNow();
        }
    }

    private UUID activeCustomer() {
        UUID customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO customer (customer_id, active) VALUES (?, TRUE)", customerId);
        return customerId;
    }

    private long databaseVersion(UUID shipmentId) {
        return jdbc.queryForObject("SELECT version FROM shipment WHERE shipment_id = ?", Long.class, shipmentId);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for the other transaction");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while coordinating concurrent transactions", interrupted);
        }
    }
}
