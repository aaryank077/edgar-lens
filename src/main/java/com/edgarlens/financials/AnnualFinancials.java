package com.edgarlens.financials;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.Map;

/**
 * One fiscal year of normalized figures. Missing values are null.
 * {@code sources} records which XBRL tag supplied each figure, so every number can be
 * traced back to the filing.
 */
public record AnnualFinancials(
        int fiscalYear,
        LocalDate periodEnd,
        Double revenue,
        Double netIncome,
        Double operatingCashFlow,
        Double capitalExpenditures,
        Double dilutedShares,
        Double cash,
        Double longTermDebt,
        Map<Concept, String> sources) {

    /** Operating cash flow minus capex, or null if either is missing. */
    @JsonProperty(value = "freeCashFlow", access = JsonProperty.Access.READ_ONLY)
    public Double freeCashFlow() {
        if (operatingCashFlow == null || capitalExpenditures == null) {
            return null;
        }
        return operatingCashFlow - capitalExpenditures;
    }
}
