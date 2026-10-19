package com.rdavies.api.repository;

import com.rdavies.api.domain.LedgerEntry;
import org.springframework.data.repository.CrudRepository;

public interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {
}
