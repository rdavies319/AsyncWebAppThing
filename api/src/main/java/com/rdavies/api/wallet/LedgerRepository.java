package com.rdavies.api.wallet;

import org.springframework.data.repository.CrudRepository;

/** Written only by WalletService (and creditRoundPayouts), so every balance change gets its row. */
public interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {
}
