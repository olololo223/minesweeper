package com.example.minesweeper.protocol;

import com.example.minesweeper.protocol.payload.GetFriendsRequest;
import com.example.minesweeper.protocol.payload.LeaderboardRequest;
import com.example.minesweeper.protocol.payload.LeaderboardResponse;
import com.example.minesweeper.protocol.payload.LoginRequest;
import com.example.minesweeper.protocol.payload.SubmitScoreRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameMessageCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Запрос без полей (GetFriendsRequest) должен проходить через кодек.
     * Регрессия: Jackson по умолчанию отказывается сериализовать бин без
     * свойств (FAIL_ON_EMPTY_BEANS), из-за чего сообщение не уходило в сеть,
     * а клиент ждал ответа до таймаута.
     */
    @Test
    void emptyPayloadSurvivesCodec() throws Exception {
        EmbeddedChannel enc = new EmbeddedChannel(new GameMessageCodec.Encoder());
        EmbeddedChannel dec = new EmbeddedChannel(new GameMessageCodec.Decoder());

        enc.writeOutbound(new GameMessage(MessageType.GET_FRIENDS_REQUEST,
                new GetFriendsRequest()));
        ByteBuf buf = enc.readOutbound();
        assertNotNull(buf, "сообщение без полей должно кодироваться");

        dec.writeInbound(buf);
        Object o = dec.readInbound();
        assertInstanceOf(GameMessage.class, o, "сообщение должно декодироваться обратно");
        GameMessage decoded = (GameMessage) o;
        assertEquals(MessageType.GET_FRIENDS_REQUEST, decoded.getType());
        assertInstanceOf(GetFriendsRequest.class, decoded.getPayload());

        enc.finish();
        dec.finish();
    }

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
                new SubmitScoreRequest("EASY", "TIMED", 42, true);
        byte[] json = mapper.writeValueAsBytes(original);
        SubmitScoreRequest parsed =
                mapper.readValue(json, SubmitScoreRequest.class);

        assertEquals("EASY", parsed.difficulty);
        assertEquals("TIMED", parsed.mode);
        assertEquals(42, parsed.durationSeconds);
        assertTrue(parsed.win);
    }

    @Test
    void leaderboardRequestRoundTrip() throws Exception {
        LeaderboardRequest original = new LeaderboardRequest("HARD", "TIMED");
        byte[] json = mapper.writeValueAsBytes(original);
        LeaderboardRequest parsed = mapper.readValue(json, LeaderboardRequest.class);

        assertEquals("HARD", parsed.difficulty);
        assertEquals("TIMED", parsed.mode);
    }

    @Test
    void leaderboardResponseRoundTrip() throws Exception {
        LeaderboardResponse original = new LeaderboardResponse("EASY", "CLASSIC",
                java.util.List.of(new LeaderboardResponse.Entry("alice", 12)));
        byte[] json = mapper.writeValueAsBytes(original);
        LeaderboardResponse parsed = mapper.readValue(json, LeaderboardResponse.class);

        assertEquals("EASY", parsed.difficulty);
        assertEquals("CLASSIC", parsed.mode);
        assertEquals(1, parsed.entries.size());
        assertEquals("alice", parsed.entries.get(0).username);
        assertEquals(12, parsed.entries.get(0).bestTimeSeconds);
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