package com.edgarlens.financials;

import java.time.Instant;
import java.util.List;

/** Normalized annual history for one company, oldest year first. */
public record CompanyFinancials(
        String ticker,
        String cik,
        String name,
        List<AnnualFinancials> years,
        Instant fetchedAt) {

    public AnnualFinancials latest() {
        if (years.isEmpty()) {
            throw new IllegalStateException("No annual data for " + ticker);
        }
        return years.get(years.size() - 1);
    }
}
