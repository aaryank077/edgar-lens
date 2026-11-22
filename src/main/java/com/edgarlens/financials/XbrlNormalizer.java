package com.edgarlens.financials;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns SEC "companyfacts" JSON into one {@link AnnualFinancials} per fiscal year.
 *
 * <p>Rules:
 * <ul>
 *   <li>Only facts from 10-K / 10-K/A filings count.</li>
 *   <li>Duration facts must cover a full year (350–380 days, which allows 52/53-week years);
 *       this drops quarterly and year-to-date figures that 10-Ks sometimes include.</li>
 *   <li>Each 10-K repeats prior years as comparatives. When the same period appears in several
 *       filings, the most recently filed value wins, so restatements replace originals.</li>
 *   <li>A fiscal year is keyed by its period end date, not the SEC "fy" field: "fy" is the
 *       fiscal year of the <em>filing</em>, so comparatives carry the wrong year.</li>
 * </ul>
 */
@Component
public class XbrlNormalizer {

    private static final Set<String> ANNUAL_FORMS = Set.of("10-K", "10-K/A");
    private static final int MIN_YEAR_DAYS = 350;
    private static final int MAX_YEAR_DAYS = 380;

    private record Fact(double value, LocalDate filed, String tag) { }

    public List<AnnualFinancials> normalize(JsonNode companyFacts) {
        JsonNode gaap = companyFacts.path("facts").path("us-gaap");

        Map<Concept, Map<LocalDate, Fact>> byConcept = new EnumMap<>(Concept.class);
        for (Concept concept : Concept.values()) {
            byConcept.put(concept, resolve(gaap, concept));
        }

        // A fiscal year exists wherever we have a full-year revenue, net income or cash flow figure.
        Set<LocalDate> periodEnds = new TreeSet<>();
        for (Concept c : List.of(Concept.REVENUE, Concept.NET_INCOME, Concept.OPERATING_CASH_FLOW)) {
            periodEnds.addAll(byConcept.get(c).keySet());
        }

        List<AnnualFinancials> years = new ArrayList<>();
        for (LocalDate end : periodEnds) {
            Map<Concept, String> sources = new EnumMap<>(Concept.class);
            Map<Concept, Double> values = new EnumMap<>(Concept.class);
            for (Concept concept : Concept.values()) {
                Fact fact = byConcept.get(concept).get(end);
                if (fact != null) {
                    values.put(concept, fact.value());
                    sources.put(concept, fact.tag());
                }
            }
            years.add(new AnnualFinancials(
                    fiscalYearOf(end),
                    end,
                    values.get(Concept.REVENUE),
                    values.get(Concept.NET_INCOME),
                    values.get(Concept.OPERATING_CASH_FLOW),
                    values.get(Concept.CAPITAL_EXPENDITURES),
                    values.get(Concept.DILUTED_SHARES),
                    values.get(Concept.CASH),
                    values.get(Concept.LONG_TERM_DEBT),
                    Map.copyOf(sources)));
        }
        return years;
    }

    /** Period end date -> best fact, trying tags in priority order per period. */
    private Map<LocalDate, Fact> resolve(JsonNode gaap, Concept concept) {
        Map<LocalDate, Fact> result = new TreeMap<>();
        for (String tag : concept.tags()) {
            Map<LocalDate, Fact> fromTag = latestFiledByPeriod(gaap.path(tag).path("units").path(concept.unit()), concept, tag);
            fromTag.forEach(result::putIfAbsent);
        }
        return result;
    }

    private Map<LocalDate, Fact> latestFiledByPeriod(JsonNode entries, Concept concept, String tag) {
        Map<LocalDate, Fact> best = new HashMap<>();
        if (!entries.isArray()) {
            return best;
        }
        for (JsonNode e : entries) {
            if (!ANNUAL_FORMS.contains(e.path("form").asText())) {
                continue;
            }
            if (!e.hasNonNull("end") || !e.hasNonNull("val") || !e.hasNonNull("filed")) {
                continue;
            }
            LocalDate end = LocalDate.parse(e.get("end").asText());
            boolean hasStart = e.hasNonNull("start");
            if (concept.kind() == Concept.Kind.DURATION) {
                if (!hasStart) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(LocalDate.parse(e.get("start").asText()), end);
                if (days < MIN_YEAR_DAYS || days > MAX_YEAR_DAYS) {
                    continue;
                }
            } else if (hasStart) {
                continue;
            }
            Fact candidate = new Fact(e.get("val").asDouble(), LocalDate.parse(e.get("filed").asText()), tag);
            best.merge(end, candidate, (a, b) -> b.filed().isAfter(a.filed()) ? b : a);
        }
        return best;
    }

    /** Fiscal years ending in the first few days of January belong to the prior year (52/53-week calendars). */
    static int fiscalYearOf(LocalDate periodEnd) {
        return periodEnd.getMonthValue() == 1 && periodEnd.getDayOfMonth() <= 7
                ? periodEnd.getYear() - 1
                : periodEnd.getYear();
    }
}
