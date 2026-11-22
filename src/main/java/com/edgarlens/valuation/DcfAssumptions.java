package com.edgarlens.valuation;

/**
 * Inputs to a DCF. Rates are decimals (0.08 = 8%).
 *
 * @param growthRate     annual free-cash-flow growth during the projection period
 * @param discountRate   required return (a WACC stand-in)
 * @param terminalGrowth perpetual growth after the projection period
 * @param years          length of the explicit projection period
 */
public record DcfAssumptions(double growthRate, double discountRate, double terminalGrowth, int years) {

    public DcfAssumptions {
        if (years < 1 || years > 20) {
            throw new IllegalArgumentException("years must be between 1 and 20");
        }
        if (discountRate <= 0 || discountRate > 0.5) {
            throw new IllegalArgumentException("discountRate must be in (0, 0.5]");
        }
        if (growthRate < -0.5 || growthRate > 0.5) {
            throw new IllegalArgumentException("growthRate must be in [-0.5, 0.5]");
        }
        if (terminalGrowth >= discountRate) {
            throw new IllegalArgumentException("terminalGrowth must be below discountRate, or the terminal value is infinite");
        }
    }
}
