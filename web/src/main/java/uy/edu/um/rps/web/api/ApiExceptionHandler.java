package uy.edu.um.rps.web.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uy.edu.um.rps.rooms.RoomException;
import uy.edu.um.rps.web.player.InvalidCredentialsException;
import uy.edu.um.rps.web.pve.SessionNotFoundException;

/** Traduce errores de negocio a respuestas HTTP con formato RFC 7807. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail notFound(SessionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RoomException.class)
    public ProblemDetail room(RoomException e) {
        HttpStatus status = switch (e.reason()) {
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NAME_TAKEN -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setProperty("reason", e.reason().name());
        return pd;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail unauthorized(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
