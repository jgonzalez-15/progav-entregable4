package uy.edu.um.rps.matchmaking;

/** Un perfil intentó entrar dos veces a la cola (por ejemplo, desde dos pestañas). */
public class AlreadyQueuedException extends RuntimeException {
    public AlreadyQueuedException(String playerId) {
        super("El jugador " + playerId + " ya está buscando partida");
    }
}
