package uy.edu.um.rps.web.pve;

import jakarta.validation.constraints.NotBlank;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.core.Outcome;
import uy.edu.um.rps.rooms.Player;

/** DTOs de la API REST del modo PvE. */
public final class PveDtos {

    private PveDtos() {
    }

    public record CreateSessionRequest(@NotBlank String name) {
    }

    public record PlayRequest(@NotBlank String move) {
    }

    public record PlayerView(String id, String name, String color, String colorHex) {
        static PlayerView of(Player p) {
            return new PlayerView(p.id(), p.name(), p.color().name(), p.color().hex());
        }
    }

    public record Score(int player, int cpu, int draws, int rounds) {
    }

    public record SessionView(String sessionId, PlayerView player, PlayerView cpu, Score score) {
    }

    public record RoundView(int round, Move playerMove, Move cpuMove, Outcome outcome, Score score) {
    }
}
