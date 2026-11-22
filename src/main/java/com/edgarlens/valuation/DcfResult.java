package com.edgarlens.valuation;

import java.util.List;

public record DcfResult(
        String ticker,
        int baseFiscalYear,
        double baseFreeCashFlow,
        DcfAssumptions assumptions,
        String growthRateSource,
        List<ProjectedYear> projections,
        double presentValueOfCashFlows,
        double terminalValue,
        double presentValueOfTerminalValue,
        double enterpriseValue,
        double cash,
        double longTermDebt,
        double equityValue,
        double dilutedShares,
        double valuePerShare,
        SensitivityTable sensitivity,
        List<String> warnings) {

    public record ProjectedYear(int fiscalYear, double freeCashFlow, double discountFactor, double presentValue) { }

    /** Value per share across discount rates (rows) and terminal growth rates (columns). Null cells are undefined. */
    public record SensitivityTable(List<Double> discountRates, List<Double> terminalGrowthRates, List<List<Double>> valuePerShare) { }
}
