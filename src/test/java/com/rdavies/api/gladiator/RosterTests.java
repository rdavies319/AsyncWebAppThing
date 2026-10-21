package com.rdavies.api.gladiator;

import com.rdavies.api.gladiator.records.RosterEntry;
import com.rdavies.api.gladiator.records.Stats;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The roster file format, and the checked-in dev roster (spec section 3). */
class RosterTests {

    @Test
    void theDevRosterHasEightCompleteGladiators() throws IOException {
        List<RosterEntry> roster = devRoster();

        assertEquals(8, roster.size());
        Set<String> slugs = roster.stream().map(RosterEntry::slug).collect(Collectors.toSet());
        assertEquals(8, slugs.size(), "slugs are unique");
        for (RosterEntry entry : roster) {
            assertTrue(entry.slug().matches("[a-z0-9-]+"), entry.slug());
            assertFalse(entry.name().isBlank());
            assertFalse(entry.epithet().isBlank());
            assertFalse(entry.bio().isBlank());
            assertNotNull(entry.stats());
        }
    }

    @Test
    void aRosterEntryReadsIntoItsStatBlock() {
        String json = """
                [{"slug": "vex", "name": "Vex", "epithet": "the Quick", "bio": "Fast.",
                  "stats": {"hp": 90, "attack_min": 5, "attack_max": 12, "defence": 1, "agility": 25}}]""";

        List<RosterEntry> roster = Roster.read(stream(json));

        assertEquals(List.of(new RosterEntry("vex", "Vex", "the Quick", "Fast.", new Stats(90, 5, 12, 1, 25))), roster);
    }

    @Test
    void anInvalidStatBlockIsRefused() {
        String json = """
                [{"slug": "vex", "name": "Vex", "epithet": "the Quick", "bio": "Fast.",
                  "stats": {"hp": 90, "attack_min": 12, "attack_max": 5, "defence": 1, "agility": 25}}]""";

        assertThrows(IllegalArgumentException.class, () -> Roster.read(stream(json)));
    }

    @Test
    void aRosterEntryHidesItsStatsWhenPrinted() {
        RosterEntry entry = new RosterEntry("vex", "Vex", "the Quick", "Fast.", new Stats(90, 5, 12, 1, 25));
        assertFalse(entry.toString().contains("90"), entry.toString());
    }

    static List<RosterEntry> devRoster() throws IOException {
        try (InputStream in = RosterTests.class.getResourceAsStream("/roster-dev.json")) {
            assertNotNull(in, "roster-dev.json on the classpath");
            return Roster.read(in);
        }
    }

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
