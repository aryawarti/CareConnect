package com.careconnect.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentStateMachineTest {

    private Appointment appointment(Instant start) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(),
                start, start.plus(30, ChronoUnit.MINUTES),
                "checkup", new BigDecimal("500.00"), "Pat", "Doc");
    }

    @Test
    void happyPathRequestedConfirmedCompleted() {
        Appointment a = appointment(Instant.now().plus(1, ChronoUnit.DAYS));
        a.confirm();
        a.complete();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void completedIsTerminal() {
        Appointment a = appointment(Instant.now().plus(1, ChronoUnit.DAYS));
        a.confirm();
        a.complete();
        assertThatThrownBy(a::cancel).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void cannotCompleteAnUnconfirmedRequest() {
        Appointment a = appointment(Instant.now().plus(1, ChronoUnit.DAYS));
        assertThatThrownBy(a::complete).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void patientCancelRespectsTwoHourCutoff() {
        Appointment soon = appointment(Instant.now().plus(1, ChronoUnit.HOURS));
        assertThatThrownBy(() -> soon.cancelAsPatient(Instant.now()))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("2 hours");

        Appointment later = appointment(Instant.now().plus(3, ChronoUnit.HOURS));
        later.cancelAsPatient(Instant.now());
        assertThat(later.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelledAppointmentFreesTheSlotSemantically() {
        Appointment a = appointment(Instant.now().plus(1, ChronoUnit.DAYS));
        assertThat(a.getStatus().blocksSlot()).isTrue();
        a.cancel();
        assertThat(a.getStatus().blocksSlot()).isFalse();
    }
}
