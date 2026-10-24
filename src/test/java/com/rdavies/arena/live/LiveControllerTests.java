package com.rdavies.arena.live;

import com.rdavies.arena.GameTest;
import com.rdavies.arena.TestGame;
import com.rdavies.arena.fight.records.AnnouncedFight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/** What a client gets from /live on connect: a snapshot of the server time and the current fights. */
@GameTest
@Transactional
class LiveControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired TestGame game;

    @Test
    void aConnectStartsWithASnapshotOfTheServerTimeAndTheCurrentFights() throws Exception {
        AnnouncedFight announced = game.boot().announced();

        MvcResult result = mockMvc.perform(get("/live")).andExpect(request().asyncStarted()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.startsWith("event:snapshot\ndata:{\"server_time\":\"2026-01-01T00:00:30Z\",\"live\":null,"
                + "\"announced\":{\"fight_id\":" + announced.fightId() + ",\"starts_at\":\"2026-01-01T01:00:00Z\","), body);
        assertTrue(body.endsWith("}}\n\n"), body);
    }
}
