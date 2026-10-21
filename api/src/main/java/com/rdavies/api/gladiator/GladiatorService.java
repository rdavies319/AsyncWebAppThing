package com.rdavies.api.gladiator;

import com.rdavies.api.gladiator.records.Gladiator;
import com.rdavies.api.gladiator.records.RosterEntry;
import com.rdavies.api.gladiator.records.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** The fixed roster (spec section 3): loaded once on first boot, then read from the database only. */
@Service
public class GladiatorService {
    private static final Logger log = LoggerFactory.getLogger(GladiatorService.class);

    private final GladiatorRepository gladiators;

    public GladiatorService(GladiatorRepository gladiators) {
        this.gladiators = gladiators;
    }

    /** Every gladiator's public face, by id. */
    public List<Gladiator> roster() {
        return gladiators.findAllByOrderByIdAsc();
    }

    public Gladiator gladiator(long id) {
        return gladiators.findById(id).orElseThrow(() -> new IllegalStateException("No gladiator " + id));
    }

    /** The hidden stat block. For the fight feature only: never put it in a response, event or log line. */
    public Stats stats(long id) {
        return gladiators.findStats(id).orElseThrow(() -> new IllegalStateException("No gladiator " + id));
    }

    /**
     * Loads the roster file if no gladiators exist yet. After that the database is the only source
     * and the file is ignored, so a changed file never changes the roster.
     *
     * @return whether the roster was loaded
     */
    @Transactional
    public boolean loadRosterIfEmpty(Resource rosterFile) {
        if (gladiators.count() > 0) {
            return false;
        }
        List<RosterEntry> roster;
        try (InputStream in = rosterFile.getInputStream()) {
            roster = Roster.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read the roster file " + rosterFile.getDescription(), e);
        }
        for (RosterEntry entry : roster) {
            Stats s = entry.stats();
            gladiators.insert(entry.slug(), entry.name(), entry.epithet(), entry.bio(),
                    s.hp(), s.attackMin(), s.attackMax(), s.defence(), s.agility());
        }
        log.info("Loaded {} gladiators from {}", roster.size(), rosterFile.getDescription());
        return true;
    }
}
