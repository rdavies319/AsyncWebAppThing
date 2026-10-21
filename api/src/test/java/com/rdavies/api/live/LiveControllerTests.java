package com.rdavies.api.live;

import com.rdavies.api.GameTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/** What a client gets from /live. */
@GameTest
class LiveControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired Clock clock;

    @Test
    void aConnectStartsWithTheServerTime() throws Exception {
        MvcResult result = mockMvc.perform(get("/live")).andExpect(request().asyncStarted()).andReturn();

        assertEquals("event:server_time\ndata:{\"server_time\":\"" + clock.instant() + "\"}\n\n",
                result.getResponse().getContentAsString());
    }
}
