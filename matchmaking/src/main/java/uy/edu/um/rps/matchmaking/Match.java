package uy.edu.um.rps.matchmaking;

/** Par de tickets emparejados. {@code first} es siempre el que más esperó. */
public record Match<T>(Ticket<T> first, Ticket<T> second) {

    public int winsGap() {
        return Math.abs(first.wins() - second.wins());
    }
}
