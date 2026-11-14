package uy.edu.um.rps.web.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import uy.edu.um.rps.rooms.Celebration;

/**
 * Perfil competitivo. Sus victorias/derrotas SOLO las modifica RankingService al cerrar una
 * partida del modo competitivo: PvE y salas casuales no tienen acceso a estas columnas.
 */
@Entity
@Table(name = "player_profile", indexes = {
        @Index(name = "ix_ranking", columnList = "wins DESC, wins_reached_at ASC, id ASC")
})
public class PlayerProfile {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 16)
    private String name;

    /** Nombre en minúsculas: evita suplantación ("Ana" vs "ana"). */
    @Column(nullable = false, length = 16, unique = true)
    private String nameKey;

    /** SHA-256 del token secreto del perfil (nunca se guarda el token en claro). */
    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private int wins;

    /** Informativo: no participa del orden del ranking. */
    @Column(nullable = false)
    private int losses;

    /** Momento en que alcanzó su cantidad actual de victorias: desempata el ranking. */
    @Column(nullable = false)
    private Instant winsReachedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Celebration celebration;

    @Version
    private long version;

    protected PlayerProfile() {
    }

    public PlayerProfile(String id, String name, String tokenHash, Celebration celebration, Instant now) {
        this.id = id;
        this.name = name;
        this.nameKey = name.toLowerCase(java.util.Locale.ROOT);
        this.tokenHash = tokenHash;
        this.celebration = celebration;
        this.createdAt = now;
        this.winsReachedAt = now;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTokenHash() { return tokenHash; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public Instant getWinsReachedAt() { return winsReachedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Celebration getCelebration() { return celebration; }

    public void setCelebration(Celebration celebration) {
        this.celebration = celebration;
    }

    /** Solo para datos de demostración (seeder). */
    void seedStats(int wins, int losses, Instant reachedAt) {
        this.wins = wins;
        this.losses = losses;
        this.winsReachedAt = reachedAt;
    }
}
