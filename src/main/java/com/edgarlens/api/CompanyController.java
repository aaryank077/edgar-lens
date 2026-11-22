package com.edgarlens.api;

import com.edgarlens.financials.CompanyFinancials;
import com.edgarlens.financials.FinancialsService;
import com.edgarlens.valuation.DcfResult;
import com.edgarlens.valuation.ValuationService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/companies/{ticker}")
public class CompanyController {

    private static final String TICKER_PATTERN = "[A-Za-z0-9.\\-]{1,10}";

    private final FinancialsService financials;
    private final ValuationService valuation;

    public CompanyController(FinancialsService financials, ValuationService valuation) {
        this.financials = financials;
        this.valuation = valuation;
    }

    @GetMapping("/financials")
    public CompanyFinancials financials(@PathVariable @Pattern(regexp = TICKER_PATTERN) String ticker) {
        return financials.forTicker(ticker);
    }

    @GetMapping("/valuation")
    public DcfResult valuation(@PathVariable @Pattern(regexp = TICKER_PATTERN) String ticker,
                               @RequestParam(required = false) Double growth,
                               @RequestParam(defaultValue = "0.09") double discountRate,
                               @RequestParam(defaultValue = "0.025") double terminalGrowth,
                               @RequestParam(defaultValue = "5") int years) {
        return valuation.value(ticker, growth, discountRate, terminalGrowth, years);
    }
}
