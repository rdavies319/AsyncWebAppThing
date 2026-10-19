package com.rdavies.api.wallet;

import org.springframework.data.repository.CrudRepository;

public interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {
}
