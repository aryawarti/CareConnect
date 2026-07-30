package com.careconnect.medicalrecord.application;

import com.careconnect.medicalrecord.domain.RecordAccessAction;
import com.careconnect.medicalrecord.domain.RecordAccessEntry;
import com.careconnect.medicalrecord.infrastructure.repository.RecordAccessRepository;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every read of clinical data.
 *
 * **Fail-closed, on purpose.** {@code record} joins the caller's transaction
 * ({@code Propagation.REQUIRED}, the default), so if the audit insert fails the
 * whole read fails and the caller gets an error instead of the chart. That is
 * the correct trade for medical records: an access you cannot prove happened is
 * an access that should not have been served. The alternative — log on a best
 * effort, serve the data anyway — produces a log that is silently incomplete,
 * which is worse than no log because it looks trustworthy.
 *
 * The cost is honest and worth stating: medical-record reads now depend on a
 * write succeeding, so the service cannot serve charts from a read-only replica
 * or with a full disk. For this domain that is the right way to fail.
 *
 * Not implemented as an AOP aspect. An aspect would silently cover whatever
 * happens to match its pointcut, and silently miss a new read path added later —
 * for an audit trail, "you have to remember to call it" is a feature, because
 * the call site is greppable and a reviewer can see it.
 */
@Service
public class RecordAccessLogger {

    private final RecordAccessRepository log;

    public RecordAccessLogger(RecordAccessRepository log) {
        this.log = log;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID actorUserId, String actorRole, String actorName,
                       UUID patientId, UUID encounterId,
                       RecordAccessAction action, boolean selfAccess) {
        log.save(new RecordAccessEntry(actorUserId, actorRole, actorName,
                patientId, encounterId, action, selfAccess,
                // Set by platform-starter's CorrelationIdFilter, so an entry can
                // be joined to the request across every service it touched.
                MDC.get("correlationId")));
    }

    @Transactional(readOnly = true)
    public Page<RecordAccessEntry> forPatient(UUID patientId, Pageable pageable) {
        return log.findByPatientIdOrderByAccessedAtDesc(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RecordAccessEntry> byActor(UUID actorUserId, Pageable pageable) {
        return log.findByActorUserIdOrderByAccessedAtDesc(actorUserId, pageable);
    }
}
