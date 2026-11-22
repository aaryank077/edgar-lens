package com.edgarlens.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlNormalizerTest {

    private List<AnnualFinancials> years;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/companyfacts-sample.json")) {
            years = new XbrlNormalizer().normalize(new ObjectMapper().readTree(in));
        }
    }

    @Test
    void producesOneRowPerFiscalYearOldestFirst() {
        assertThat(years).extracting(AnnualFinancials::fiscalYear).containsExactly(2016, 2017, 2018);
    }

    @Test
    void fallsBackToOlderTagForYearsBeforeTheSwitch() {
        AnnualFinancials fy2016 = years.get(0);
        assertThat(fy2016.revenue()).isEqualTo(800);
        assertThat(fy2016.sources()).containsEntry(Concept.REVENUE, "SalesRevenueNet");
    }

    @Test
    void prefersHigherPriorityTagWhenBothReportTheSamePeriod() {
        AnnualFinancials fy2017 = years.get(1);
        assertThat(fy2017.revenue()).isEqualTo(905);
        assertThat(fy2017.sources()).containsEntry(Concept.REVENUE, "RevenueFromContractWithCustomerExcludingAssessedTax");
    }

    @Test
    void ignoresQuarterlyFiguresInsideAnnualFilings() {
        assertThat(years.get(2).revenue()).isEqualTo(1000);
    }

    @Test
    void latestFilingWinsSoRestatementsReplaceOriginals() {
        assertThat(years.get(2).netIncome()).isEqualTo(95);
    }

    @Test
    void readsInstantFactsAtFiscalYearEnd() {
        AnnualFinancials fy2018 = years.get(2);
        assertThat(fy2018.periodEnd()).isEqualTo(LocalDate.of(2018, 12, 31));
        assertThat(fy2018.cash()).isEqualTo(50);
        assertThat(fy2018.longTermDebt()).isEqualTo(20);
        assertThat(fy2018.dilutedShares()).isEqualTo(10);
    }

    @Test
    void computesFreeCashFlowOnlyWhenBothInputsExist() {
        assertThat(years.get(2).freeCashFlow()).isEqualTo(120);
        assertThat(years.get(0).freeCashFlow()).isNull();
    }

    @Test
    void earlyJanuaryYearEndsBelongToThePriorFiscalYear() {
        assertThat(XbrlNormalizer.fiscalYearOf(LocalDate.of(2024, 1, 3))).isEqualTo(2023);
        assertThat(XbrlNormalizer.fiscalYearOf(LocalDate.of(2024, 9, 28))).isEqualTo(2024);
    }
}
