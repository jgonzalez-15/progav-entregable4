package uy.edu.um.rps.web.pvp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Dos navegadores simulados juegan una partida PvP real por WebSocket. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PvpWebSocketIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    private TestClient ana;
    private TestClient beto;

    @AfterEach
    void close() throws Exception {
        if (ana != null) ana.session.close();
        if (beto != null) beto.session.close();
    }

    @Test
    void twoPlayersCreateJoinPlayAndLeave() throws Exception {
        ana = connect();
        beto = connect();

        ana.send("{\"type\":\"CREATE\",\"name\":\"Ana\"}");
        JsonNode joinedAna = ana.expect("JOINED");
        String code = joinedAna.get("code").asText();
        String anaId = joinedAna.at("/you/id").asText();
        assertThat(code).matches("[A-Z]{6}");

        beto.send("{\"type\":\"JOIN\",\"code\":\"" + code.toLowerCase() + "\",\"name\":\"Beto\"}");
        JsonNode joinedBeto = beto.expect("JOINED");
        String betoId = joinedBeto.at("/you/id").asText();
        assertThat(joinedBeto.at("/you/colorHex").asText()).isNotEqualTo(joinedAna.at("/you/colorHex").asText());

        JsonNode state = ana.expect("ROOM_STATE");
        assertThat(state.get("players")).hasSize(2);
        assertThat(state.get("full").asBoolean()).isTrue();

        // Ronda 1: Ana piedra, Beto tijera -> gana Ana
        ana.send("{\"type\":\"MOVE\",\"move\":\"ROCK\"}");
        JsonNode readyState = beto.expect("ROOM_STATE");
        assertThat(readyState.toString()).doesNotContain("ROCK"); // la jugada no se revela antes de tiempo
        beto.send("{\"type\":\"MOVE\",\"move\":\"SCISSORS\"}");

        JsonNode resultForAna = ana.expect("ROUND_RESULT");
        JsonNode resultForBeto = beto.expect("ROUND_RESULT");
        assertThat(resultForAna.get("winnerId").asText()).isEqualTo(anaId);
        assertThat(resultForBeto.at("/moves/" + betoId).asText()).isEqualTo("SCISSORS");
        assertThat(resultForBeto.at("/scores/" + anaId).asInt()).isEqualTo(1);
        assertThat(resultForBeto.at("/scores/" + betoId).asInt()).isZero();

        // Ronda 2: empate
        ana.send("{\"type\":\"MOVE\",\"move\":\"PAPER\"}");
        beto.send("{\"type\":\"MOVE\",\"move\":\"papel\"}");
        JsonNode draw = beto.expect("ROUND_RESULT");
        assertThat(draw.get("winnerId").isNull()).isTrue();
        assertThat(draw.get("round").asInt()).isEqualTo(2);
        assertThat(draw.get("draws").asInt()).isEqualTo(1);

        // Beto abandona: Ana recibe aviso
        beto.send("{\"type\":\"LEAVE\"}");
        beto.expect("LEFT");
        JsonNode left = ana.expect("PLAYER_LEFT");
        assertThat(left.get("name").asText()).isEqualTo("Beto");
    }

    @Test
    void joiningUnknownRoomReturnsError() throws Exception {
        ana = connect();
        ana.send("{\"type\":\"JOIN\",\"code\":\"ZZZZZZ\",\"name\":\"Ana\"}");
        JsonNode error = ana.expect("ERROR");
        assertThat(error.get("reason").asText()).isEqualTo("ROOM_NOT_FOUND");
    }

    @Test
    void closingTheBrowserNotifiesOpponent() throws Exception {
        ana = connect();
        beto = connect();
        ana.send("{\"type\":\"CREATE\",\"name\":\"Ana\"}");
        String code = ana.expect("JOINED").get("code").asText();
        beto.send("{\"type\":\"JOIN\",\"code\":\"" + code + "\",\"name\":\"Beto\"}");
        beto.expect("JOINED");

        beto.session.close();
        beto = null;
        assertThat(ana.expect("PLAYER_LEFT").get("name").asText()).isEqualTo("Beto");
    }

    private TestClient connect() throws Exception {
        TestClient client = new TestClient();
        client.session = new StandardWebSocketClient()
                .execute(client, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws/game"))
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    public static class TestClient extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
        public WebSocketSession session;

        public static TestClient connect(int port) throws Exception {
            TestClient client = new TestClient();
            client.session = new StandardWebSocketClient()
                    .execute(client, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/ws/game"))
                    .get(5, TimeUnit.SECONDS);
            return client;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            inbox.add(MAPPER.readTree(message.getPayload()));
        }

        public void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        /** Espera el próximo mensaje del tipo indicado, descartando otros. */
        public JsonNode expect(String type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                JsonNode msg = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (msg != null && type.equals(msg.get("type").asText())) {
                    return msg;
                }
            }
            throw new AssertionError("No llegó un mensaje " + type);
        }
    }
}
