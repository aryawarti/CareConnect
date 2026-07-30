package com.careconnect.billing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.billing.application.BillingService;
import com.careconnect.billing.domain.Invoice;
import com.careconnect.billing.domain.InvoiceStateException;
import com.careconnect.billing.infrastructure.client.PatientClient;
import com.careconnect.billing.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.billing.infrastructure.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The controller resolves the caller's own patient id through a Feign client,
 * which must be mocked: a @WebMvcTest slice has no FeignClientFactory, so
 * without this the context fails to load and every test errors before running.
 */
@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class BillingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean BillingService service;
    @MockitoBean PatientClient patientClient;

    private static final UUID INVOICE = UUID.randomUUID();
    private static final String PATIENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void identityResolves() {
        when(patientClient.me()).thenReturn(new PatientClient.Envelope<>(
                new PatientClient.MeSummary(UUID.fromString(PATIENT_ID))));
    }

    private static final String PAY_BODY = """
            {"amount":800.00,"method":"SIMULATED","reference":"ref-123"}""";

    private Invoice invoice() {
        return new Invoice("INV-005001", UUID.randomUUID(), UUID.fromString(PATIENT_ID),
                UUID.randomUUID(), "Asha Verma", "Dr. Rao", new BigDecimal("800.00"));
    }

    @Test
    void patientCanPayOwnInvoice() throws Exception {
        when(service.pay(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(invoice());

        mvc.perform(post("/api/invoices/" + INVOICE + "/payments")
                        .header("X-User-Id", PATIENT_ID).header("X-User-Roles", "PATIENT")
                        .contentType("application/json").content(PAY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-005001"));
    }

    @Test
    void doublePaymentSurfacesAsConflict() throws Exception {
        when(service.pay(any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new InvoiceStateException("Invoice INV-005001 is already paid"));

        mvc.perform(post("/api/invoices/" + INVOICE + "/payments")
                        .header("X-User-Id", PATIENT_ID).header("X-User-Roles", "PATIENT")
                        .contentType("application/json").content(PAY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invoice state conflict"));
    }

    @Test
    void patientCannotVoidInvoices() throws Exception {
        mvc.perform(post("/api/invoices/" + INVOICE + "/void")
                        .header("X-User-Id", PATIENT_ID).header("X-User-Roles", "PATIENT")
                        .contentType("application/json")
                        .content("""
                                {"reason":"I don't want to pay"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotListAllInvoices() throws Exception {
        mvc.perform(get("/api/invoices")
                        .header("X-User-Id", PATIENT_ID).header("X-User-Roles", "PATIENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/invoices/" + INVOICE))
                .andExpect(status().isUnauthorized());
    }
}
