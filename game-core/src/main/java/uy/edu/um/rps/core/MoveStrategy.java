package uy.edu.um.rps.core;

/** Estrategia que decide la jugada de un oponente virtual. */
@FunctionalInterface
public interface MoveStrategy {
    Move nextMove();
}
