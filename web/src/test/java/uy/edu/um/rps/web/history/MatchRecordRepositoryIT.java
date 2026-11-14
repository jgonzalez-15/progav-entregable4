package uy.edu.um.rps.web.history;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import uy.edu.um.rps.core.Move;

/** Integración con la base de datos real (H2) a través de JPA. */
@DataJpaTest
class MatchRecordRepositoryIT {

    @Autowired
    private MatchRecordRepository repository;

    @Test
    void savesAndReturnsNewestFirst() {
        repository.save(new MatchRecord(MatchRecord.Mode.PVE, null, "Ana", Move.ROCK, "CPU", Move.SCISSORS, "Ana"));
        repository.save(new MatchRecord(MatchRecord.Mode.PVP, "ABCDEF", "Ana", Move.ROCK, "Beto", Move.PAPER, "Beto"));
        repository.save(new MatchRecord(MatchRecord.Mode.PVP, "ABCDEF", "Ana", Move.ROCK, "Beto", Move.ROCK, null));

        var latest = repository.findAllByOrderByPlayedAtDescIdDesc(PageRequest.of(0, 2));
        assertThat(latest).hasSize(2);
        assertThat(latest.get(0).getWinner()).isNull();
        assertThat(latest.get(1).getWinner()).isEqualTo("Beto");
        assertThat(repository.countByWinnerIgnoreCase("ana")).isEqualTo(1);
    }
}
