package com.rdavies.api.user;

import com.rdavies.api.GameRuleException;
import com.rdavies.api.wallet.WalletService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.regex.Pattern;

/** Signup, and the login lookup Spring Security's form login uses. */
@Service
public class UserService implements UserDetailsService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DISPLAY_NAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt ignores bytes past 72, so a longer password would be silently truncated. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, WalletService walletService,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Creates the player and their wallet (with the signup bonus) together. */
    @Transactional
    public GamePrincipal signup(String email, String displayName, String password) {
        String normalisedEmail = email == null ? "" : normaliseEmail(email);
        String name = displayName == null ? "" : displayName.strip();
        if (!EMAIL.matcher(normalisedEmail).matches() || normalisedEmail.length() > 254) {
            throw GameRuleException.invalid("Enter a valid email address");
        }
        if (!DISPLAY_NAME.matcher(name).matches()) {
            throw GameRuleException.invalid("Display names are 3 to 30 letters, digits or underscores");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw GameRuleException.invalid("Passwords are " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_BYTES + " characters");
        }
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw GameRuleException.invalid("That email already has an account");
        }
        if (userRepository.existsByDisplayName(name)) {
            throw GameRuleException.invalid("That display name is taken");
        }

        User user;
        try {
            user = userRepository.save(new User(null, normalisedEmail, name, passwordEncoder.encode(password), clock.instant()));
        } catch (DuplicateKeyException e) {
            // Someone took the email or name between the checks above and the insert
            throw GameRuleException.invalid("That email or display name was just taken", e);
        }
        walletService.createFor(user.id());
        return GamePrincipal.of(user);
    }

    @Override
    public GamePrincipal loadUserByUsername(String email) {
        return userRepository.findByEmail(normaliseEmail(email))
                .map(GamePrincipal::of)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }

    /** Signup and login must agree on this, or players can't log in with the email they signed up with. */
    private static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
