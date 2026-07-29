package com.careconnect.queue.application;

import com.careconnect.queue.infrastructure.repository.QueueEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estimates how long a patient will actually wait.
 *
 * Deliberately statistics, not machine learning: the median of a doctor's last
 * 20 consultations, blended with a clinic-wide default until enough history
 * exists. Median rather than mean because one 45-minute consultation should
 * not make every subsequent estimate wrong — the distribution has a long right
 * tail. Honest, explainable, and it improves on its own as the day proceeds.
 */
@Component
public class WaitTimeEstimator {

    private final QueueEntryRepository entries;
    private final int defaultMinutes;
    private final int minimumSamples;

    public WaitTimeEstimator(QueueEntryRepository entries,
                             @Value("${careconnect.queue.default-consultation-minutes:12}")
                             int defaultMinutes,
                             @Value("${careconnect.queue.min-samples:3}") int minimumSamples) {
        this.entries = entries;
        this.defaultMinutes = defaultMinutes;
        this.minimumSamples = minimumSamples;
    }

    /** Typical consultation length for this doctor, in minutes. */
    public int averageConsultationMinutes(UUID doctorId) {
        List<Integer> samples = entries.recentConsultationSeconds(doctorId).stream()
                .filter(seconds -> seconds != null && seconds > 60 && seconds < 4 * 3600)
                .sorted()
                .toList();
        if (samples.size() < minimumSamples) {
            return defaultMinutes;
        }
        int medianSeconds = samples.get(samples.size() / 2);
        return Math.max(3, Math.round(medianSeconds / 60f));
    }

    /**
     * Minutes until this position is likely to be called. Position 0 (next up)
     * still gets a small estimate: the person currently inside has to finish.
     */
    public int estimateWaitMinutes(UUID doctorId, int positionAhead, boolean someoneInConsultation) {
        int perPatient = averageConsultationMinutes(doctorId);
        int minutes = positionAhead * perPatient;
        if (someoneInConsultation) {
            minutes += perPatient / 2;   // the current consultation, on average half done
        }
        return minutes;
    }
}
