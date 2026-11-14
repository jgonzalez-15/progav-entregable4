package uy.edu.um.rps.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uy.edu.um.rps.rooms.RoomManager;
import uy.edu.um.rps.web.pve.PveService;

/** Limpia salas y partidas abandonadas para no acumular memoria. */
@Component
public class HousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJob.class);
    private final RoomManager roomManager;
    private final PveService pveService;
    private final AppProperties props;

    public HousekeepingJob(RoomManager roomManager, PveService pveService, AppProperties props) {
        this.roomManager = roomManager;
        this.pveService = pveService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void evictIdle() {
        int rooms = roomManager.evictIdle(props.idleTimeout()).size();
        int sessions = pveService.evictIdle(props.idleTimeout());
        if (rooms + sessions > 0) {
            log.info("Limpieza: {} salas y {} partidas PvE inactivas eliminadas", rooms, sessions);
        }
    }
}
