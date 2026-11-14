package uy.edu.um.rps.web.pve;

import java.time.Instant;
import uy.edu.um.rps.core.Scoreboard;
import uy.edu.um.rps.rooms.Player;

/** Partida de un jugador contra el oponente virtual. */
public class PveSession {

    private final String id;
    private final Player player;
    private final Player cpu;
    private final Scoreboard scoreboard = new Scoreboard();
    private volatile Instant lastActivity = Instant.now();

    PveSession(String id, Player player, Player cpu) {
        this.id = id;
        this.player = player;
        this.cpu = cpu;
        scoreboard.register(player.id());
        scoreboard.register(cpu.id());
    }

    public String id() { return id; }
    public Player player() { return player; }
    public Player cpu() { return cpu; }
    public Scoreboard scoreboard() { return scoreboard; }
    public Instant lastActivity() { return lastActivity; }

    void touch() {
        lastActivity = Instant.now();
    }
}
