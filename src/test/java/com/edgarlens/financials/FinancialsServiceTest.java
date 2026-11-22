package com.edgarlens.financials;

import com.edgarlens.cache.FinancialsCacheRepository;
import com.edgarlens.sec.Company;
import com.edgarlens.sec.SecClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Full Spring context with H2 + Flyway, so this also checks the migration and JSON round-trip. */
@SpringBootTest
class FinancialsServiceTest {

    static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock clock() {
            return new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return NOW.get(); }
            };
        }
    }

    @MockitoBean
    private SecClient sec;

    @Autowired
    private FinancialsService service;

    @Autowired
    private FinancialsCacheRepository repository;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        NOW.set(Instant.parse("2026-01-01T00:00:00Z"));
        when(sec.lookup("SAMP")).thenReturn(new Company("SAMP", "0001234567", "Sample Corp"));
        try (InputStream in = getClass().getResourceAsStream("/fixtures/companyfacts-sample.json")) {
            when(sec.companyFacts("0001234567")).thenReturn(mapper.readTree(in));
        }
    }

    @Test
    void secondCallWithinTtlIsServedFromCache() {
        CompanyFinancials first = service.forTicker("SAMP");
        CompanyFinancials second = service.forTicker("SAMP");

        verify(sec, times(1)).companyFacts("0001234567");
        assertThat(second.years()).isEqualTo(first.years());
        assertThat(second.latest().freeCashFlow()).isEqualTo(120);
        assertThat(second.latest().sources()).containsEntry(Concept.REVENUE, "RevenueFromContractWithCustomerExcludingAssessedTax");
    }

    @Test
    void staleCacheIsRefreshed() {
        service.forTicker("SAMP");
        NOW.set(NOW.get().plus(Duration.ofHours(25)));
        service.forTicker("SAMP");
        verify(sec, times(2)).companyFacts("0001234567");
        assertThat(repository.findById("0001234567")).get()
                .extracting(e -> e.getFetchedAt()).isEqualTo(NOW.get());
    }
}
