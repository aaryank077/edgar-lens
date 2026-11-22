package com.edgarlens.valuation;

import com.edgarlens.financials.AnnualFinancials;
import com.edgarlens.financials.CompanyFinancials;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DcfModelTest {

    private final DcfModel model = new DcfModel();

    private static AnnualFinancials year(int fy, Double revenue, Double ocf, Double capex, Double shares, Double cash, Double debt) {
        return new AnnualFinancials(fy, LocalDate.of(fy, 12, 31), revenue, null, ocf, capex, shares, cash, debt, Map.of());
    }

    private static CompanyFinancials company(AnnualFinancials... years) {
        return new CompanyFinancials("TEST", "0000000001", "Test Co", List.of(years), Instant.EPOCH);
    }

    @Test
    void matchesHandComputedValuation() {
        // FCF = 120 - 20 = 100; growth 10%, discount 10%, terminal 2%, 2 years.
        // Year 1: 110 / 1.1 = 100. Year 2: 121 / 1.21 = 100. PV(FCF) = 200.
        // Terminal = 121 * 1.02 / 0.08 = 1542.75; PV = 1542.75 / 1.21 = 1275.
        // EV = 1475; equity = 1475 + 50 cash - 25 debt = 1500; per share (10 shares) = 150.
        CompanyFinancials c = company(year(2024, 1000.0, 120.0, 20.0, 10.0, 50.0, 25.0));
        DcfResult r = model.value(c, new DcfAssumptions(0.10, 0.10, 0.02, 2), "test");

        assertThat(r.baseFreeCashFlow()).isEqualTo(100);
        assertThat(r.projections()).hasSize(2);
        assertThat(r.projections().get(1).fiscalYear()).isEqualTo(2026);
        assertThat(r.presentValueOfCashFlows()).isCloseTo(200, within(1e-9));
        assertThat(r.terminalValue()).isCloseTo(1542.75, within(1e-9));
        assertThat(r.presentValueOfTerminalValue()).isCloseTo(1275, within(1e-9));
        assertThat(r.enterpriseValue()).isCloseTo(1475, within(1e-9));
        assertThat(r.equityValue()).isCloseTo(1500, within(1e-9));
        assertThat(r.valuePerShare()).isCloseTo(150, within(1e-9));
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void sensitivityCenterCellEqualsHeadlineValue() {
        CompanyFinancials c = company(year(2024, 1000.0, 120.0, 20.0, 10.0, 50.0, 25.0));
        DcfResult r = model.value(c, new DcfAssumptions(0.10, 0.10, 0.02, 2), "test");

        assertThat(r.sensitivity().valuePerShare().get(1).get(1)).isCloseTo(r.valuePerShare(), within(1e-9));
        // Higher discount rate -> lower value; higher terminal growth -> higher value.
        assertThat(r.sensitivity().valuePerShare().get(2).get(1)).isLessThan(r.valuePerShare());
        assertThat(r.sensitivity().valuePerShare().get(1).get(2)).isGreaterThan(r.valuePerShare());
    }

    @Test
    void missingCashAndDebtAreTreatedAsZeroWithWarnings() {
        CompanyFinancials c = company(year(2024, 1000.0, 120.0, 20.0, 10.0, null, null));
        DcfResult r = model.value(c, new DcfAssumptions(0.10, 0.10, 0.02, 2), "test");
        assertThat(r.equityValue()).isCloseTo(r.enterpriseValue(), within(1e-9));
        assertThat(r.warnings()).hasSize(2);
    }

    @Test
    void rejectsNonPositiveFreeCashFlow() {
        CompanyFinancials c = company(year(2024, 1000.0, 10.0, 20.0, 10.0, 0.0, 0.0));
        assertThatThrownBy(() -> model.value(c, new DcfAssumptions(0.05, 0.10, 0.02, 5), "test"))
                .isInstanceOf(InsufficientDataException.class);
    }

    @Test
    void rejectsTerminalGrowthAtOrAboveDiscountRate() {
        assertThatThrownBy(() -> new DcfAssumptions(0.05, 0.03, 0.03, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void historicalGrowthIsFiveYearRevenueCagr() {
        // 100 -> 161.051 over 5 years is exactly 10% per year; the 2018 value is outside the lookback.
        CompanyFinancials c = company(
                year(2018, 10.0, null, null, null, null, null),
                year(2019, 100.0, null, null, null, null, null),
                year(2024, 161.051, null, null, null, null, null));
        DcfModel.GrowthEstimate g = model.historicalRevenueGrowth(c);
        assertThat(g.rate()).isCloseTo(0.10, within(1e-9));
        assertThat(g.fromYear()).isEqualTo(2019);
        assertThat(g.toYear()).isEqualTo(2024);
    }

    @Test
    void historicalGrowthIsClamped() {
        CompanyFinancials c = company(
                year(2023, 100.0, null, null, null, null, null),
                year(2024, 300.0, null, null, null, null, null));
        assertThat(model.historicalRevenueGrowth(c).rate()).isEqualTo(DcfModel.MAX_DEFAULT_GROWTH);
    }
}
