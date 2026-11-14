package uy.edu.um.rps.web.history;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    List<MatchRecord> findAllByOrderByPlayedAtDescIdDesc(Pageable pageable);

    long countByWinnerIgnoreCase(String winner);
}
