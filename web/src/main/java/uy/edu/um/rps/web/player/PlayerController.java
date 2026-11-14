package uy.edu.um.rps.web.player;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.rps.rooms.Celebration;
import uy.edu.um.rps.web.ranking.RankingService;

@RestController
public class PlayerController {

    public static final String TOKEN_HEADER = "X-Player-Token";

    public record RegisterRequest(@NotBlank String name, String celebration) {
    }

    public record CelebrationRequest(String celebration) {
    }

    public record ProfileView(String playerId, String name, String celebration, int wins, int losses, long position) {
    }

    public record RegisteredView(String playerId, String token, String name, String celebration) {
    }

    private final PlayerService players;
    private final RankingService ranking;

    public PlayerController(PlayerService players, RankingService ranking) {
        this.players = players;
        this.ranking = ranking;
    }

    @PostMapping("/api/players")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredView register(@Valid @RequestBody RegisterRequest request) {
        PlayerService.Registration r = players.register(request.name(), request.celebration());
        return new RegisteredView(r.profile().getId(), r.token(), r.profile().getName(), r.profile().getCelebration().name());
    }

    @GetMapping("/api/players/{id}")
    public ProfileView profile(@PathVariable String id) {
        var entry = ranking.position(id).orElseThrow(InvalidCredentialsException::new);
        return new ProfileView(id, entry.name(), null, entry.wins(), entry.losses(), entry.position());
    }

    @PutMapping("/api/players/{id}/celebration")
    public Map<String, String> updateCelebration(@PathVariable String id,
                                                 @RequestHeader(TOKEN_HEADER) String token,
                                                 @RequestBody CelebrationRequest request) {
        PlayerProfile p = players.updateCelebration(id, token, request.celebration());
        return Map.of("playerId", p.getId(), "celebration", p.getCelebration().name());
    }

    @GetMapping("/api/celebrations")
    public List<Map<String, String>> celebrations() {
        return Arrays.stream(Celebration.values()).map(c -> Map.of("id", c.name(), "label", c.label())).toList();
    }
}
