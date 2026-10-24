package com.rdavies.arena.wallet;

import com.rdavies.arena.wallet.records.LedgerEntry;
import org.springframework.data.repository.CrudRepository;

/** Package-private, like WalletRepository: only WalletService writes the ledger, so every balance change gets its row. */
interface LedgerRepository extends CrudRepository<LedgerEntry, Long> {
}
