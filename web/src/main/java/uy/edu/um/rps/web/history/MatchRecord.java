package uy.edu.um.rps.web.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import uy.edu.um.rps.core.Move;

/** Una ronda jugada, persistida en H2 para el historial. */
@Entity
@Table(name = "match_record")
public class MatchRecord {

    public enum Mode { PVE, PVP, RANKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Mode mode;

    @Column(length = 6)
    private String roomCode;

    @Column(nullable = false, length = 32)
    private String playerA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Move moveA;

    @Column(nullable = false, length = 32)
    private String playerB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Move moveB;

    /** Nombre del ganador, null si fue empate. */
    @Column(length = 32)
    private String winner;

    @Column(nullable = false)
    private Instant playedAt;

    protected MatchRecord() {
    }

    public MatchRecord(Mode mode, String roomCode, String playerA, Move moveA, String playerB, Move moveB,
                       String winner) {
        this.mode = mode;
        this.roomCode = roomCode;
        this.playerA = playerA;
        this.moveA = moveA;
        this.playerB = playerB;
        this.moveB = moveB;
        this.winner = winner;
        this.playedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Mode getMode() { return mode; }
    public String getRoomCode() { return roomCode; }
    public String getPlayerA() { return playerA; }
    public Move getMoveA() { return moveA; }
    public String getPlayerB() { return playerB; }
    public Move getMoveB() { return moveB; }
    public String getWinner() { return winner; }
    public Instant getPlayedAt() { return playedAt; }
}
