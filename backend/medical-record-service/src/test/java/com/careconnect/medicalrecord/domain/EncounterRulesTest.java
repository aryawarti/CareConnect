package com.careconnect.medicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterRulesTest {

    private Encounter encounter() {
        return new Encounter(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Asha Verma", "Dr. Rao", Instant.now());
    }

    @Test
    void openEncounterAcceptsClinicalContent() {
        Encounter e = encounter();
        e.updateClinicalContent("Fever", "Temp 101F, no rash");
        e.addDiagnosis("J06.9", "Acute upper respiratory infection");
        e.addPrescription("Paracetamol", "500mg", "TID", 3, "After food");

        assertThat(e.getStatus()).isEqualTo(EncounterStatus.OPEN);
        assertThat(e.getDiagnoses()).hasSize(1);
        assertThat(e.getPrescriptions()).hasSize(1);
    }

    @Test
    void signingRequiresNotes() {
        Encounter e = encounter();
        assertThatThrownBy(e::sign)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without notes");
    }

    @Test
    void signedEncounterRejectsDirectEdits() {
        Encounter e = encounter();
        e.updateClinicalContent("Fever", "Initial note");
        e.sign();

        assertThatThrownBy(() -> e.updateClinicalContent("Fever", "sneaky rewrite"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> e.addDiagnosis("X", "Y"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void amendmentPreservesThePreviousNote() {
        Encounter e = encounter();
        e.updateClinicalContent("Fever", "Original finding");
        e.sign();

        e.amend("Corrected finding", "Lab result contradicted initial reading");

        assertThat(e.getStatus()).isEqualTo(EncounterStatus.AMENDED);
        assertThat(e.getNotes()).isEqualTo("Corrected finding");
        assertThat(e.getAmendments()).singleElement()
                .satisfies(a -> {
                    assertThat(a.getPreviousNote()).isEqualTo("Original finding");
                    assertThat(a.getReason()).contains("Lab result");
                });
    }

    @Test
    void openEncountersAreEditedNotAmended() {
        Encounter e = encounter();
        assertThatThrownBy(() -> e.amend("x", "y"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("edited directly");
    }

    @Test
    void ownershipChecksAreExplicit() {
        UUID doctor = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        Encounter e = new Encounter(UUID.randomUUID(), patient, doctor, "P", "D", Instant.now());

        assertThat(e.isTreatingDoctor(doctor)).isTrue();
        assertThat(e.isTreatingDoctor(UUID.randomUUID())).isFalse();
        assertThat(e.belongsToPatient(patient)).isTrue();
        assertThat(e.belongsToPatient(UUID.randomUUID())).isFalse();
    }
}
