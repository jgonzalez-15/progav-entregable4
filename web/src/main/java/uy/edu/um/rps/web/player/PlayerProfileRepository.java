package uy.edu.um.rps.web.player;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, String> {

    Optional<PlayerProfile> findByNameKey(String nameKey);

    /** Ranking: más victorias primero; empate -> quien llegó antes a esa cifra; luego id. */
    @Query("""
            select p from PlayerProfile p
            where p.wins + p.losses > 0
            order by p.wins desc, p.winsReachedAt asc, p.id asc""")
    List<PlayerProfile> findRanking(Pageable pageable);

    @Query("select count(p) from PlayerProfile p where p.wins + p.losses > 0")
    long countRanked();

    /** Cantidad exacta de jugadores por delante (posición = esto + 1). Usa el índice ix_ranking. */
    @Query("""
            select count(p) from PlayerProfile p
            where p.wins + p.losses > 0 and (
                  p.wins > :wins
               or (p.wins = :wins and p.winsReachedAt < :reachedAt)
               or (p.wins = :wins and p.winsReachedAt = :reachedAt and p.id < :id))""")
    long countAhead(@Param("wins") int wins, @Param("reachedAt") Instant reachedAt, @Param("id") String id);

    /** Incremento atómico en la base: sin "lost updates" aunque haya escrituras concurrentes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlayerProfile p set p.wins = p.wins + 1, p.winsReachedAt = :now, p.version = p.version + 1 where p.id = :id")
    int addWin(@Param("id") String id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlayerProfile p set p.losses = p.losses + 1, p.version = p.version + 1 where p.id = :id")
    int addLoss(@Param("id") String id);
}
