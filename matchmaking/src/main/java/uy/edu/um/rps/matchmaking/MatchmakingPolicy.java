package uy.edu.um.rps.matchmaking;

import java.time.Duration;

/**
 * Regla de tolerancia: cuántas victorias de diferencia se aceptan según el tiempo de espera.
 * Empieza estricta y se ensancha para que nadie espere para siempre.
 *
 * @param baseTolerance diferencia aceptada al entrar a la cola
 * @param step          cuánto se ensancha en cada intervalo
 * @param stepEvery     cada cuánto se ensancha
 * @param maxTolerance  tope (Integer.MAX_VALUE = sin tope)
 */
public record MatchmakingPolicy(int baseTolerance, int step, Duration stepEvery, int maxTolerance) {

    public static MatchmakingPolicy defaults() {
        return new MatchmakingPolicy(1, 2, Duration.ofSeconds(5), Integer.MAX_VALUE);
    }

    public MatchmakingPolicy {
        if (baseTolerance < 0 || step < 0 || stepEvery.isNegative() || stepEvery.isZero()) {
            throw new IllegalArgumentException("Política de matchmaking inválida");
        }
    }

    public int toleranceFor(Duration waited) {
        long steps = Math.max(0, waited.toMillis()) / stepEvery.toMillis();
        long tolerance = baseTolerance + steps * (long) step;
        return (int) Math.min(tolerance, maxTolerance);
    }
}
