package uy.edu.um.rps.web.player;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Perfil o token inválido");
    }
}
