package uy.edu.um.rps.web.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Resultado de una partida competitiva. La clave primaria es el matchId: si el cierre de una
 * partida se procesara dos veces (reintento, carrera), la segunda inserción falla y no se
 * cuentan victorias duplicadas.
 */
@Entity
@Table(name = "competitive_match", indexes = {
        @Index(name = "ix_pair_time", columnList = "winner_id, loser_id, finished_at")
})
public class CompetitiveMatch {

    @Id
    @Column(length = 36)
    private String matchId;

    @Column(nullable = false, length = 36)
    private String winnerId;

    @Column(nullable = false, length = 36)
    private String loserId;

    private int winnerRounds;
    private int loserRounds;
    private boolean forfeit;

    /** false si se descartó para el ranking (anti win-trading). */
    private boolean counted;

    @Column(nullable = false)
    private Instant finishedAt;

    protected CompetitiveMatch() {
    }

    public CompetitiveMatch(String matchId, String winnerId, String loserId, int winnerRounds, int loserRounds,
                            boolean forfeit, boolean counted, Instant finishedAt) {
        this.matchId = matchId;
        this.winnerId = winnerId;
        this.loserId = loserId;
        this.winnerRounds = winnerRounds;
        this.loserRounds = loserRounds;
        this.forfeit = forfeit;
        this.counted = counted;
        this.finishedAt = finishedAt;
    }

    public String getMatchId() { return matchId; }
    public String getWinnerId() { return winnerId; }
    public String getLoserId() { return loserId; }
    public int getWinnerRounds() { return winnerRounds; }
    public int getLoserRounds() { return loserRounds; }
    public boolean isForfeit() { return forfeit; }
    public boolean isCounted() { return counted; }
    public Instant getFinishedAt() { return finishedAt; }
}
