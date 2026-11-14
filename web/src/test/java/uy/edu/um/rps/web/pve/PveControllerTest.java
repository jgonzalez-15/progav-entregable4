package uy.edu.um.rps.web.pve;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.rps.core.Move;
import uy.edu.um.rps.core.Outcome;

/** Prueba de la capa web aislada (slice @WebMvcTest) con el servicio simulado. */
@WebMvcTest(PveController.class)
class PveControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PveService service;

    @Test
    void playReturnsRoundResult() throws Exception {
        when(service.play(eq("abc"), eq(Move.PAPER))).thenReturn(
                new PveDtos.RoundView(1, Move.PAPER, Move.ROCK, Outcome.WIN, new PveDtos.Score(1, 0, 0, 1)));

        mvc.perform(post("/api/pve/sessions/abc/rounds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"move\":\"papel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("WIN"))
                .andExpect(jsonPath("$.cpuMove").value("ROCK"))
                .andExpect(jsonPath("$.score.player").value(1));
    }

    @Test
    void invalidMoveIsBadRequest() throws Exception {
        mvc.perform(post("/api/pve/sessions/abc/rounds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"move\":\"lagarto\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankNameIsBadRequest() throws Exception {
        mvc.perform(post("/api/pve/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSessionIsNotFound() throws Exception {
        when(service.play(anyString(), eq(Move.ROCK))).thenThrow(new SessionNotFoundException("x"));
        mvc.perform(post("/api/pve/sessions/x/rounds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"move\":\"ROCK\"}"))
                .andExpect(status().isNotFound());
    }
}
