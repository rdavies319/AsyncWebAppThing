package com.rdavies.arena.bet;

import com.rdavies.arena.bet.records.Bet;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

/** Package-private: bets change only through the bet feature's services. */
interface BetRepository extends CrudRepository<Bet, Long> {

    boolean existsByUserIdAndFightId(long userId, long fightId);

    Optional<Bet> findByUserIdAndFightId(long userId, long fightId);
}
