package com.edgarlens.valuation;

import com.edgarlens.financials.CompanyFinancials;
import com.edgarlens.financials.FinancialsService;
import org.springframework.stereotype.Service;

@Service
public class ValuationService {

    static final double FALLBACK_GROWTH = 0.05;

    private final FinancialsService financials;
    private final DcfModel model;

    public ValuationService(FinancialsService financials, DcfModel model) {
        this.financials = financials;
        this.model = model;
    }

    /** Runs a DCF. When {@code growthRate} is null, uses the company's historical revenue CAGR. */
    public DcfResult value(String ticker, Double growthRate, double discountRate, double terminalGrowth, int years) {
        CompanyFinancials company = financials.forTicker(ticker);
        if (company.years().isEmpty()) {
            throw new InsufficientDataException("No annual 10-K data found for " + ticker);
        }

        double growth;
        String source;
        if (growthRate != null) {
            growth = growthRate;
            source = "user supplied";
        } else {
            DcfModel.GrowthEstimate historical = model.historicalRevenueGrowth(company);
            if (historical != null) {
                growth = historical.rate();
                source = "revenue CAGR FY" + historical.fromYear() + "-FY" + historical.toYear()
                        + ", clamped to [" + DcfModel.MIN_DEFAULT_GROWTH + ", " + DcfModel.MAX_DEFAULT_GROWTH + "]";
            } else {
                growth = FALLBACK_GROWTH;
                source = "fallback default (not enough revenue history)";
            }
        }
        return model.value(company, new DcfAssumptions(growth, discountRate, terminalGrowth, years), source);
    }
}
