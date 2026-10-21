package com.rdavies.arena.user;

import com.rdavies.arena.user.records.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    /** Emails are stored lower-case, so pass a lower-cased address. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByDisplayName(String displayName);
}
