package uy.edu.um.rps.web.pve;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String id) {
        super("No existe la partida " + id);
    }
}
