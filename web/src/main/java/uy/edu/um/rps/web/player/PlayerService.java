package uy.edu.um.rps.web.player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.rps.rooms.Celebration;
import uy.edu.um.rps.rooms.PlayerNames;
import uy.edu.um.rps.rooms.RoomException;

/** Alta y autenticación de perfiles competitivos (token secreto por navegador). */
@Service
public class PlayerService {

    public record Registration(PlayerProfile profile, String token) {
    }

    private final PlayerProfileRepository repository;
    private final SecureRandom random;
    private final Clock clock;

    public PlayerService(PlayerProfileRepository repository, SecureRandom random, Clock clock) {
        this.repository = repository;
        this.random = random;
        this.clock = clock;
    }

    @Transactional
    public Registration register(String rawName, String celebration) {
        String name = PlayerNames.normalize(rawName);
        if (repository.findByNameKey(name.toLowerCase(Locale.ROOT)).isPresent()) {
            throw new RoomException(RoomException.Reason.NAME_TAKEN, "El nombre " + name + " ya tiene un perfil competitivo");
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID().toString(), name, hash(token),
                Celebration.parseOrDefault(celebration), clock.instant());
        try {
            repository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException e) {
            // Dos altas simultáneas con el mismo nombre: la restricción UNIQUE decide.
            throw new RoomException(RoomException.Reason.NAME_TAKEN, "El nombre " + name + " ya tiene un perfil competitivo");
        }
        return new Registration(profile, token);
    }

    @Transactional(readOnly = true)
    public PlayerProfile authenticate(String playerId, String token) {
        if (playerId == null || token == null) {
            throw new InvalidCredentialsException();
        }
        PlayerProfile profile = repository.findById(playerId).orElseThrow(InvalidCredentialsException::new);
        byte[] expected = profile.getTokenHash().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(token).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) { // comparación en tiempo constante
            throw new InvalidCredentialsException();
        }
        return profile;
    }

    @Transactional
    public PlayerProfile updateCelebration(String playerId, String token, String celebration) {
        PlayerProfile profile = authenticate(playerId, token);
        PlayerProfile managed = repository.findById(profile.getId()).orElseThrow();
        managed.setCelebration(Celebration.parseOrDefault(celebration));
        return managed;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
