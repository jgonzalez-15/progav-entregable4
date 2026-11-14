package uy.edu.um.rps.web.history;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

    public static final int MAX_LIMIT = 100;
    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);
    private final MatchRecordRepository repository;

    public HistoryService(MatchRecordRepository repository) {
        this.repository = repository;
    }

    /** Guarda la ronda. Un fallo de persistencia nunca debe cortar la partida. */
    @Transactional
    public void record(MatchRecord matchRecord) {
        try {
            repository.save(matchRecord);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la ronda en el historial: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MatchRecord> latest(int limit) {
        int safe = Math.max(1, Math.min(limit, MAX_LIMIT));
        return repository.findAllByOrderByPlayedAtDescIdDesc(PageRequest.of(0, safe));
    }
}
