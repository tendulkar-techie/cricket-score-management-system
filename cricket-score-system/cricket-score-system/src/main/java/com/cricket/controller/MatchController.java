package com.cricket.controller;

import com.cricket.model.Models.*;
import com.cricket.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MatchController {
    private final MatchService svc;
    public MatchController(MatchService svc) { this.svc = svc; }

    @GetMapping("/matches")
    public Collection<Match> matches() { return svc.all(); }

    @GetMapping("/matches/{id}")
    public Match match(@PathVariable String id) { return svc.get(id); }

    @GetMapping("/matches/{id}/players")
    public Map<String, Object> players(@PathVariable String id) {
        Match m = svc.get(id);
        synchronized (m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Innings in : m.innings) {
                out.put(in.battingTeam + " batting", in.batters);
                out.put(in.bowlingTeam + " bowling", in.bowlers);
            }
            return out;
        }
    }

    @GetMapping("/matches/{id}/commentary")
    public List<String> commentary(@PathVariable String id) { return svc.get(id).commentary; }

    @GetMapping("/matches/{id}/summary")
    public Map<String, Object> summary(@PathVariable String id) { return svc.summary(id); }

    @PostMapping("/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public Match create(@RequestBody CreateMatchRequest r) {
        return svc.create(r.home(), r.away(), r.overs() == null ? 20 : r.overs(),
            r.venue() == null ? "Neutral venue" : r.venue());
    }

    @PostMapping("/matches/{id}/ball")
    public Match ball(@PathVariable String id) { return svc.ball(id); }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() { return svc.subscribe(); }
}
