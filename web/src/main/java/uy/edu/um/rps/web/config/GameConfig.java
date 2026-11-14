package uy.edu.um.rps.web.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uy.edu.um.rps.core.MoveStrategy;
import uy.edu.um.rps.core.RandomOpponent;
import uy.edu.um.rps.matchmaking.Matchmaker;
import uy.edu.um.rps.matchmaking.MatchmakingPolicy;
import uy.edu.um.rps.rooms.ColorAssigner;
import uy.edu.um.rps.rooms.RandomRoomCodeGenerator;
import uy.edu.um.rps.rooms.RoomManager;

/** Integra los módulos game-core y rooms (Java puro) como beans de Spring. */
@Configuration
public class GameConfig {

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public ColorAssigner colorAssigner(SecureRandom random) {
        return new ColorAssigner(random);
    }

    @Bean
    public RoomManager roomManager(SecureRandom random, ColorAssigner colorAssigner) {
        return new RoomManager(new RandomRoomCodeGenerator(random), colorAssigner);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Cola competitiva; el adjunto es el id de la conexión WebSocket del jugador. */
    @Bean
    public Matchmaker<String> matchmaker(CompetitiveProperties props, Clock clock) {
        return new Matchmaker<>(new MatchmakingPolicy(props.baseTolerance(), props.toleranceStep(),
                props.toleranceStepEvery(), Integer.MAX_VALUE), clock);
    }

    @Bean
    public MoveStrategy cpuStrategy(SecureRandom random) {
        return new RandomOpponent(random);
    }
}
