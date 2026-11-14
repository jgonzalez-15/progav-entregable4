package uy.edu.um.rps.rooms;

/** Datos de un jugador emparejado por el matchmaking (id = id del perfil competitivo). */
public record MatchSeed(String playerId, String name, Celebration celebration) {
}
