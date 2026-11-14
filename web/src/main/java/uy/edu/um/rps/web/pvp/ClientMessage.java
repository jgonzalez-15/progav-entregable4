package uy.edu.um.rps.web.pvp;

/**
 * Mensaje que envía el navegador por WebSocket.
 * <pre>
 * {"type":"CREATE","name":"Ana","celebration":"PEACE"}
 * {"type":"JOIN","code":"ABCDEF","name":"Beto","celebration":"DRUM"}
 * {"type":"MOVE","move":"ROCK"}
 * {"type":"LEAVE"}
 * {"type":"QUEUE","playerId":"...","token":"..."}      (modo competitivo)
 * {"type":"CANCEL_QUEUE"}
 * </pre>
 */
public record ClientMessage(String type, String name, String code, String move, String celebration,
                            String playerId, String token) {
}
