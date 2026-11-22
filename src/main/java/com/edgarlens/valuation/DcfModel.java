package com.edgarlens.valuation;

import com.edgarlens.financials.AnnualFinancials;
import com.edgarlens.financials.CompanyFinancials;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Unlevered-style DCF on reported free cash flow (operating cash flow minus capex):
 * project FCF at a constant growth rate, add a Gordon-growth terminal value, discount
 * both to today, then bridge enterprise value to equity with cash and long-term debt.
 */
@Component
public class DcfModel {

    static final double MIN_DEFAULT_GROWTH = -0.05;
    static final double MAX_DEFAULT_GROWTH = 0.15;
    static final int CAGR_LOOKBACK_YEARS = 5;

    private static final double[] DISCOUNT_OFFSETS = {-0.01, 0.0, 0.01};
    private static final double[] TERMINAL_OFFSETS = {-0.005, 0.0, 0.005};

    public DcfResult value(CompanyFinancials company, DcfAssumptions assumptions, String growthRateSource) {
        AnnualFinancials base = company.latest();
        Double baseFcf = base.freeCashFlow();
        if (baseFcf == null) {
            throw new InsufficientDataException("Latest 10-K (FY" + base.fiscalYear()
                    + ") is missing operating cash flow or capex, so free cash flow is unknown");
        }
        if (baseFcf <= 0) {
            throw new InsufficientDataException("Free cash flow in FY" + base.fiscalYear()
                    + " is not positive, so a cash-flow DCF is not meaningful");
        }
        if (base.dilutedShares() == null || base.dilutedShares() <= 0) {
            throw new InsufficientDataException("Latest 10-K is missing a diluted share count");
        }

        List<String> warnings = new ArrayList<>();
        double cash = orZero(base.cash(), "cash", warnings);
        double debt = orZero(base.longTermDebt(), "long-term debt", warnings);

        Core core = compute(baseFcf, assumptions);
        List<DcfResult.ProjectedYear> projections = new ArrayList<>();
        for (int t = 1; t <= assumptions.years(); t++) {
            double fcf = core.fcf[t - 1];
            double df = 1 / Math.pow(1 + assumptions.discountRate(), t);
            projections.add(new DcfResult.ProjectedYear(base.fiscalYear() + t, fcf, df, fcf * df));
        }

        double equity = core.enterpriseValue + cash - debt;
        double perShare = equity / base.dilutedShares();

        return new DcfResult(
                company.ticker(),
                base.fiscalYear(),
                baseFcf,
                assumptions,
                growthRateSource,
                projections,
                core.pvCashFlows,
                core.terminalValue,
                core.pvTerminal,
                core.enterpriseValue,
                cash,
                debt,
                equity,
                base.dilutedShares(),
                perShare,
                sensitivity(baseFcf, assumptions, cash - debt, base.dilutedShares()),
                List.copyOf(warnings));
    }

    /** Revenue CAGR between two fiscal years, already clamped. */
    public record GrowthEstimate(double rate, int fromYear, int toYear) { }

    /**
     * Revenue CAGR from the latest fiscal year back to the earliest year within the last
     * five, clamped to a sane range. Returns null with fewer than two years of positive revenue.
     */
    public GrowthEstimate historicalRevenueGrowth(CompanyFinancials company) {
        List<AnnualFinancials> withRevenue = company.years().stream()
                .filter(y -> y.revenue() != null && y.revenue() > 0)
                .toList();
        if (withRevenue.size() < 2) {
            return null;
        }
        AnnualFinancials last = withRevenue.get(withRevenue.size() - 1);
        AnnualFinancials first = withRevenue.stream()
                .filter(y -> y.fiscalYear() >= last.fiscalYear() - CAGR_LOOKBACK_YEARS)
                .findFirst()
                .orElseThrow();
        int span = last.fiscalYear() - first.fiscalYear();
        if (span <= 0) {
            return null;
        }
        double cagr = Math.pow(last.revenue() / first.revenue(), 1.0 / span) - 1;
        double clamped = Math.max(MIN_DEFAULT_GROWTH, Math.min(MAX_DEFAULT_GROWTH, cagr));
        return new GrowthEstimate(clamped, first.fiscalYear(), last.fiscalYear());
    }

    private record Core(double[] fcf, double pvCashFlows, double terminalValue, double pvTerminal, double enterpriseValue) { }

    private static Core compute(double baseFcf, DcfAssumptions a) {
        double[] fcf = new double[a.years()];
        double pv = 0;
        for (int t = 1; t <= a.years(); t++) {
            fcf[t - 1] = baseFcf * Math.pow(1 + a.growthRate(), t);
            pv += fcf[t - 1] / Math.pow(1 + a.discountRate(), t);
        }
        double terminal = fcf[a.years() - 1] * (1 + a.terminalGrowth()) / (a.discountRate() - a.terminalGrowth());
        double pvTerminal = terminal / Math.pow(1 + a.discountRate(), a.years());
        return new Core(fcf, pv, terminal, pvTerminal, pv + pvTerminal);
    }

    private static DcfResult.SensitivityTable sensitivity(double baseFcf, DcfAssumptions a, double netCash, double shares) {
        List<Double> rates = new ArrayList<>();
        List<Double> terminals = new ArrayList<>();
        for (double d : DISCOUNT_OFFSETS) rates.add(a.discountRate() + d);
        for (double d : TERMINAL_OFFSETS) terminals.add(a.terminalGrowth() + d);

        List<List<Double>> grid = new ArrayList<>();
        for (double r : rates) {
            List<Double> row = new ArrayList<>();
            for (double g : terminals) {
                try {
                    Core core = compute(baseFcf, new DcfAssumptions(a.growthRate(), r, g, a.years()));
                    row.add((core.enterpriseValue + netCash) / shares);
                } catch (IllegalArgumentException outOfRange) {
                    row.add(null);
                }
            }
            grid.add(row);
        }
        return new DcfResult.SensitivityTable(rates, terminals, grid);
    }

    private static double orZero(Double value, String label, List<String> warnings) {
        if (value == null) {
            warnings.add("No " + label + " reported at fiscal year end; treated as 0");
            return 0;
        }
        return value;
    }
}
