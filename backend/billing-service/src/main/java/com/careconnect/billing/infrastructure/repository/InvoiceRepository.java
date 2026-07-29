package com.careconnect.billing.infrastructure.repository;

import com.careconnect.billing.domain.Invoice;
import com.careconnect.billing.domain.InvoiceStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByAppointmentId(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    Page<Invoice> findByPatientIdOrderByIssuedAtDesc(UUID patientId, Pageable pageable);

    Page<Invoice> findByStatusOrderByIssuedAtDesc(InvoiceStatus status, Pageable pageable);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();
}
