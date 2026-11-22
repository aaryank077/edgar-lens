package com.edgarlens.api;

import com.edgarlens.financials.FinancialsService;
import com.edgarlens.sec.SecUnavailableException;
import com.edgarlens.sec.UnknownTickerException;
import com.edgarlens.valuation.DcfAssumptions;
import com.edgarlens.valuation.DcfResult;
import com.edgarlens.valuation.InsufficientDataException;
import com.edgarlens.valuation.ValuationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FinancialsService financials;

    @MockitoBean
    private ValuationService valuation;

    private static DcfResult sampleResult() {
        return new DcfResult("AMZN", 2025, 100, new DcfAssumptions(0.1, 0.09, 0.025, 5), "user supplied",
                List.of(), 0, 0, 0, 1000, 0, 0, 1000, 10, 100,
                new DcfResult.SensitivityTable(List.of(), List.of(), List.of()), List.of());
    }

    @Test
    void passesQueryParametersThrough() throws Exception {
        when(valuation.value(eq("AMZN"), eq(0.1), eq(0.08), eq(0.03), eq(7))).thenReturn(sampleResult());
        mvc.perform(get("/api/companies/AMZN/valuation?growth=0.1&discountRate=0.08&terminalGrowth=0.03&years=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valuePerShare").value(100.0));
    }

    @Test
    void usesDefaultsAndHistoricalGrowthWhenParametersOmitted() throws Exception {
        when(valuation.value(eq("AMZN"), isNull(), eq(0.09), eq(0.025), eq(5))).thenReturn(sampleResult());
        mvc.perform(get("/api/companies/AMZN/valuation")).andExpect(status().isOk());
    }

    @Test
    void unknownTickerIs404() throws Exception {
        when(financials.forTicker("ZZZZ")).thenThrow(new UnknownTickerException("ZZZZ"));
        mvc.perform(get("/api/companies/ZZZZ/financials"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Unknown ticker"));
    }

    @Test
    void invalidAssumptionsAre400() throws Exception {
        when(valuation.value(any(), any(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new IllegalArgumentException("terminalGrowth must be below discountRate"));
        mvc.perform(get("/api/companies/AMZN/valuation?discountRate=0.02")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedTickerIs400() throws Exception {
        mvc.perform(get("/api/companies/BAD$TICKER!!/financials")).andExpect(status().isBadRequest());
    }

    @Test
    void negativeCashFlowIs422() throws Exception {
        when(valuation.value(any(), any(), anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new InsufficientDataException("Free cash flow is not positive"));
        mvc.perform(get("/api/companies/RIVN/valuation")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void secOutageIs503() throws Exception {
        when(financials.forTicker("AMZN")).thenThrow(new SecUnavailableException("down"));
        mvc.perform(get("/api/companies/AMZN/financials")).andExpect(status().isServiceUnavailable());
    }
}
