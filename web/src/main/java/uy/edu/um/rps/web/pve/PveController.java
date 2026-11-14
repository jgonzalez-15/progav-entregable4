package uy.edu.um.rps.web.pve;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.web.pve.PveDtos.CreateSessionRequest;
import uy.edu.um.rps.web.pve.PveDtos.PlayRequest;
import uy.edu.um.rps.web.pve.PveDtos.RoundView;
import uy.edu.um.rps.web.pve.PveDtos.SessionView;

@RestController
@RequestMapping("/api/pve/sessions")
public class PveController {

    private final PveService service;

    public PveController(PveService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView create(@Valid @RequestBody CreateSessionRequest request) {
        return service.create(request.name());
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/rounds")
    public RoundView play(@PathVariable String id, @Valid @RequestBody PlayRequest request) {
        return service.play(id, Move.parse(request.move()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@PathVariable String id) {
        service.end(id);
    }
}
