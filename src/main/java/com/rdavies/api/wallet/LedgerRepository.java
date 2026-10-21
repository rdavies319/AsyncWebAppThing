package com.rdavies.api.wallet;

import com.rdavies.api.wallet.records.LedgerEntry;
import org.springframework.data.repository.CrudRepository;

/** Package-private, like WalletRepository: only WalletService writes the ledger, so every balance change gets its row. */
interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {
}
