package com.rdavies.arena.gladiator;

import com.rdavies.arena.DevGameTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** The dev profile is the one place the checked-in dev roster may load. */
@DevGameTest
@Transactional
class DevRosterTests {
    @Autowired GladiatorService gladiators;
    @Autowired JdbcTemplate jdbc;
    @Autowired ResourceLoader resources;

    @Test
    void theDevRosterLoadsUnderTheDevProfile() {
        jdbc.update("DELETE FROM gladiators"); // rolled back after the test

        assertTrue(gladiators.loadRosterIfEmpty(resources.getResource("classpath:roster-dev.json")));

        assertEquals(8, gladiators.roster().size());
    }
}
