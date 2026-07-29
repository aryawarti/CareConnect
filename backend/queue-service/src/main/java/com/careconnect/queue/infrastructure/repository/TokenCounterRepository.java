package com.careconnect.queue.infrastructure.repository;

import com.careconnect.queue.domain.TokenCounter;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenCounterRepository extends JpaRepository<TokenCounter, TokenCounter.Key> {

    /** Pessimistic lock: two receptionists checking in at once must not collide. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TokenCounter c where c.doctorId = :doctorId and c.queueDate = :date")
    Optional<TokenCounter> lockFor(@Param("doctorId") UUID doctorId, @Param("date") LocalDate date);
}
