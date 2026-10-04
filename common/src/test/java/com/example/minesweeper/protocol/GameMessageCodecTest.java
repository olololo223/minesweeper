package com.example.minesweeper.protocol;

import com.example.minesweeper.protocol.payload.LoginRequest;
import com.example.minesweeper.protocol.payload.SubmitScoreRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameMessageCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loginRequestRoundTrip() throws Exception {
        LoginRequest original = new LoginRequest("alice", "secret");
        byte[] json = mapper.writeValueAsBytes(original);
        LoginRequest parsed = mapper.readValue(json, LoginRequest.class);

        assertEquals("alice", parsed.username);
        assertEquals("secret", parsed.password);
    }

    @Test
    void submitScoreRoundTrip() throws Exception {
        SubmitScoreRequest original =
                new SubmitScoreRequest("EASY", 42, true);
        byte[] json = mapper.writeValueAsBytes(original);
        SubmitScoreRequest parsed =
                mapper.readValue(json, SubmitScoreRequest.class);

        assertEquals("EASY", parsed.difficulty);
        assertEquals(42, parsed.durationSeconds);
        assertTrue(parsed.win);
    }

    @Test
    void messageTypeHasCorrectPayloadClass() {
        assertEquals(LoginRequest.class,
                MessageType.LOGIN_REQUEST.getPayloadClass());
        assertEquals(SubmitScoreRequest.class,
                MessageType.SUBMIT_SCORE_REQUEST.getPayloadClass());
        assertNull(MessageType.PING.getPayloadClass());
    }
}