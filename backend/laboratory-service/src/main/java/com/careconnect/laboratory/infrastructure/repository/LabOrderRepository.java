package com.careconnect.laboratory.infrastructure.repository;

import com.careconnect.laboratory.domain.LabOrder;
import com.careconnect.laboratory.domain.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LabOrderRepository extends JpaRepository<LabOrder, UUID> {

    // No @EntityGraph on these: Hibernate can't eager-fetch two List-typed
    // collections (items + samples) in one query (MultipleBagFetchException).
    // Callers rely on the controller wrapping the DTO mapping in a
    // transaction (OrderResponse.summary() below) instead.

    /** Worklist: active orders, STAT first, then oldest. */
    @Query("""
            select o from LabOrder o
            where o.status in (com.careconnect.laboratory.domain.OrderStatus.ORDERED,
                               com.careconnect.laboratory.domain.OrderStatus.COLLECTED,
                               com.careconnect.laboratory.domain.OrderStatus.IN_PROCESS,
                               com.careconnect.laboratory.domain.OrderStatus.REPORTED)
            order by o.priority asc, o.orderedAt asc
            """)
    List<LabOrder> worklist();

    Page<LabOrder> findByPatientIdOrderByOrderedAtDesc(UUID patientId, Pageable pageable);

    Page<LabOrder> findByPatientIdAndStatusOrderByOrderedAtDesc(UUID patientId, OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    @Query(value = "select nextval('lab_order_seq')", nativeQuery = true)
    long nextOrderNumber();
}
