package com.edgarlens.financials;

import com.edgarlens.cache.FinancialsCacheEntry;
import com.edgarlens.cache.FinancialsCacheRepository;
import com.edgarlens.sec.Company;
import com.edgarlens.sec.SecClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Returns normalized financials for a ticker, serving from the database cache while it is fresh. */
@Service
public class FinancialsService {

    private static final Logger log = LoggerFactory.getLogger(FinancialsService.class);
    private static final TypeReference<List<AnnualFinancials>> YEARS = new TypeReference<>() { };

    private final SecClient sec;
    private final XbrlNormalizer normalizer;
    private final FinancialsCacheRepository cache;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final Clock clock;

    public FinancialsService(SecClient sec,
                             XbrlNormalizer normalizer,
                             FinancialsCacheRepository cache,
                             ObjectMapper mapper,
                             @Value("${edgar.cache.ttl:PT24H}") Duration ttl,
                             Optional<Clock> clock) {
        this.sec = sec;
        this.normalizer = normalizer;
        this.cache = cache;
        this.mapper = mapper;
        this.ttl = ttl;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    public CompanyFinancials forTicker(String ticker) {
        Company company = sec.lookup(ticker);
        Instant now = clock.instant();

        Optional<FinancialsCacheEntry> cached = cache.findById(company.cik())
                .filter(e -> e.getFetchedAt().plus(ttl).isAfter(now));
        if (cached.isPresent()) {
            log.debug("Cache hit for {} ({})", company.ticker(), company.cik());
            return fromEntry(cached.get());
        }

        log.info("Fetching company facts for {} ({}) from SEC", company.ticker(), company.cik());
        List<AnnualFinancials> years = normalizer.normalize(sec.companyFacts(company.cik()));
        cache.save(new FinancialsCacheEntry(company.cik(), company.ticker(), company.name(), toJson(years), now));
        return new CompanyFinancials(company.ticker(), company.cik(), company.name(), years, now);
    }

    private CompanyFinancials fromEntry(FinancialsCacheEntry e) {
        try {
            List<AnnualFinancials> years = mapper.readValue(e.getPayload(), YEARS);
            return new CompanyFinancials(e.getTicker(), e.getCik(), e.getName(), years, e.getFetchedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Corrupt cache entry for CIK " + e.getCik(), ex);
        }
    }

    private String toJson(List<AnnualFinancials> years) {
        try {
            return mapper.writeValueAsString(years);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
