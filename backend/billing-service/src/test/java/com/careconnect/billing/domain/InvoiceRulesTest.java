package com.careconnect.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceRulesTest {

    private Invoice invoice() {
        return new Invoice("INV-005001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Asha Verma", "Dr. Rao", new BigDecimal("800.00"));
    }

    @Test
    void payingInFullSettlesTheInvoice() {
        Invoice i = invoice();
        i.pay(new BigDecimal("800.00"), "SIMULATED", "ref-1");

        assertThat(i.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(i.getPaidAt()).isNotNull();
        assertThat(i.getPayments()).hasSize(1);
    }

    @Test
    void payingTwiceIsRejected() {
        Invoice i = invoice();
        i.pay(new BigDecimal("800.00"), "SIMULATED", "ref-1");

        assertThatThrownBy(() -> i.pay(new BigDecimal("800.00"), "SIMULATED", "ref-2"))
                .isInstanceOf(InvoiceStateException.class)
                .hasMessageContaining("already paid");
    }

    @Test
    void partialPaymentIsRejectedInV1() {
        Invoice i = invoice();
        assertThatThrownBy(() -> i.pay(new BigDecimal("500.00"), "SIMULATED", "ref-1"))
                .isInstanceOf(InvoiceStateException.class)
                .hasMessageContaining("Partial payments");
    }

    @Test
    void paidInvoiceCannotBeVoided() {
        Invoice i = invoice();
        i.pay(new BigDecimal("800.00"), "SIMULATED", "ref-1");

        assertThatThrownBy(() -> i.voidInvoice("mistake"))
                .isInstanceOf(InvoiceStateException.class)
                .hasMessageContaining("refund");
    }

    @Test
    void voidedInvoiceCannotBePaid() {
        Invoice i = invoice();
        i.voidInvoice("duplicate visit record");

        assertThat(i.getStatus()).isEqualTo(InvoiceStatus.VOID);
        assertThatThrownBy(() -> i.pay(new BigDecimal("800.00"), "SIMULATED", "ref-1"))
                .isInstanceOf(InvoiceStateException.class);
    }

    @Test
    void amountsUseExactDecimalArithmetic() {
        Invoice i = new Invoice("INV-1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "P", "D", new BigDecimal("0.10").add(new BigDecimal("0.20")));

        // 0.1 + 0.2 == 0.3 exactly — the reason money is never a double
        assertThat(i.getAmount()).isEqualByComparingTo("0.30");
    }
}
