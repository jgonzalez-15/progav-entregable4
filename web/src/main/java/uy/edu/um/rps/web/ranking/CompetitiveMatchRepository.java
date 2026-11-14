package uy.edu.um.rps.web.ranking;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitiveMatchRepository extends JpaRepository<CompetitiveMatch, String> {

    long countByWinnerIdAndLoserIdAndCountedTrueAndFinishedAtAfter(String winnerId, String loserId, Instant since);
}
