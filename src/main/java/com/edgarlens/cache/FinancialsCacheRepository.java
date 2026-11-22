package com.edgarlens.cache;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialsCacheRepository extends JpaRepository<FinancialsCacheEntry, String> {
}
