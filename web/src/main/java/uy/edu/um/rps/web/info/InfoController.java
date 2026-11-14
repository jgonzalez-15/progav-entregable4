package uy.edu.um.rps.web.info;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.rps.web.config.AppProperties;

/** Expone ambiente y versión desplegada: lo usa la UI y los smoke tests del pipeline. */
@RestController
public class InfoController {

    private final AppProperties props;

    public InfoController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of(
                "app", "Piedra, Papel o Tijera 3D",
                "env", props.env(),
                "version", props.version(),
                "commit", props.commit());
    }
}
