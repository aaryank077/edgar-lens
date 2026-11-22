package com.edgarlens.financials;

import java.util.List;

/**
 * A financial line item and the XBRL tags companies use to report it, in priority order.
 * Companies switch tags over time (e.g. SalesRevenueNet was replaced by
 * RevenueFromContractWithCustomerExcludingAssessedTax after ASC 606 in 2018), so each
 * period takes the first tag that has a value for it.
 */
public enum Concept {
    REVENUE(Kind.DURATION, "USD",
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet",
            "RevenueFromContractWithCustomerIncludingAssessedTax",
            "SalesRevenueGoodsNet"),
    NET_INCOME(Kind.DURATION, "USD",
            "NetIncomeLoss",
            "ProfitLoss",
            "NetIncomeLossAvailableToCommonStockholdersBasic"),
    OPERATING_CASH_FLOW(Kind.DURATION, "USD",
            "NetCashProvidedByUsedInOperatingActivities",
            "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"),
    CAPITAL_EXPENDITURES(Kind.DURATION, "USD",
            "PaymentsToAcquirePropertyPlantAndEquipment",
            "PaymentsToAcquireProductiveAssets",
            "PaymentsToAcquireOtherPropertyPlantAndEquipment"),
    DILUTED_SHARES(Kind.DURATION, "shares",
            "WeightedAverageNumberOfDilutedSharesOutstanding",
            "WeightedAverageNumberOfSharesOutstandingBasic"),
    CASH(Kind.INSTANT, "USD",
            "CashAndCashEquivalentsAtCarryingValue",
            "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents",
            "Cash"),
    LONG_TERM_DEBT(Kind.INSTANT, "USD",
            "LongTermDebt",
            "LongTermDebtNoncurrent",
            "LongTermDebtAndCapitalLeaseObligations");

    public enum Kind { DURATION, INSTANT }

    private final Kind kind;
    private final String unit;
    private final List<String> tags;

    Concept(Kind kind, String unit, String... tags) {
        this.kind = kind;
        this.unit = unit;
        this.tags = List.of(tags);
    }

    public Kind kind() { return kind; }
    public String unit() { return unit; }
    public List<String> tags() { return tags; }
}
