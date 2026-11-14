package uy.edu.um.rps.rooms;

/** Error de negocio de las salas, con un motivo que el cliente puede interpretar. */
public class RoomException extends RuntimeException {

    public enum Reason {
        ROOM_NOT_FOUND,
        ROOM_FULL,
        INVALID_CODE,
        INVALID_NAME,
        NAME_TAKEN,
        NOT_IN_ROOM,
        WAITING_FOR_OPPONENT,
        ALREADY_MOVED,
        MATCH_FINISHED,
        ALREADY_QUEUED
    }

    private final Reason reason;

    public RoomException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
