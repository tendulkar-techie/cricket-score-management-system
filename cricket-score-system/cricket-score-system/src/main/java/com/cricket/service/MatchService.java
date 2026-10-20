package com.cricket.service;

import com.cricket.model.Models.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MatchService {

    static final Map<String, String[]> SQUADS = Map.of(
        "India", new String[]{"Rohit", "Gill", "Kohli", "Suryakumar", "Pant", "Hardik", "Jadeja", "Axar", "Kuldeep", "Bumrah", "Siraj"},
        "Australia", new String[]{"Warner", "Head", "Smith", "Maxwell", "Inglis", "Stoinis", "Cummins", "Starc", "Zampa", "Hazlewood", "Abbott"},
        "England", new String[]{"Salt", "Buttler", "Root", "Brook", "Livingstone", "Curran", "Ali", "Rashid", "Archer", "Wood", "Topley"},
        "South Africa", new String[]{"de Kock", "Hendricks", "Markram", "Klaasen", "Miller", "Jansen", "Maharaj", "Rabada", "Nortje", "Shamsi", "Ngidi"});

    private final Map<String, Match> matches = new ConcurrentHashMap<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Random rnd = new Random();

    public MatchService() {
        create("India", "Australia", 20, "Wankhede Stadium, Mumbai");
        create("England", "South Africa", 20, "Lord's, London");
        create("Australia", "England", 10, "MCG, Melbourne");
    }

    // ---------- queries ----------
    public Collection<Match> all() { return matches.values(); }

    public Match get(String id) {
        Match m = matches.get(id);
        if (m == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + id);
        return m;
    }

    public Map<String, Object> summary(String id) {
        Match m = get(id);
        synchronized (m) {
            Innings in = m.innings.get(m.current);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("title", m.title); s.put("status", m.status); s.put("result", m.result);
            s.put("score", in.runs + "/" + in.wickets + " (" + in.getOvers() + ")");
            s.put("runRate", in.getRunRate());
            if (m.current == 1 && "LIVE".equals(m.status)) {
                int left = m.overs * 6 - in.balls;
                s.put("target", m.target);
                s.put("runsNeeded", m.target - in.runs);
                s.put("ballsLeft", left);
                s.put("requiredRate", left == 0 ? 0 : Math.round((m.target - in.runs) * 600.0 / left) / 100.0);
            } else if (in.balls > 0) {
                s.put("projected", Math.round(in.getRunRate() * m.overs));
            }
            m.innings.stream().flatMap(i -> i.batters.stream()).max(Comparator.comparingInt(b -> b.runs))
                .ifPresent(b -> s.put("topBatter", b.name + " " + b.runs + "(" + b.balls + ")"));
            m.innings.stream().flatMap(i -> i.bowlers.stream())
                .max(Comparator.comparingInt((Bowler b) -> b.wickets).thenComparingInt(b -> -b.runs))
                .ifPresent(b -> s.put("topBowler", b.name + " " + b.wickets + "/" + b.runs));
            return s;
        }
    }

    // ---------- commands ----------
    public Match create(String home, String away, int overs, String venue) {
        if (!SQUADS.containsKey(home) || !SQUADS.containsKey(away) || home.equals(away))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Pick two different teams from " + new TreeSet<>(SQUADS.keySet()));
        Match m = new Match();
        m.id = UUID.randomUUID().toString().substring(0, 8);
        m.title = home + " vs " + away; m.venue = venue; m.overs = overs;
        m.innings.add(newInnings(home, away));
        m.innings.add(newInnings(away, home));
        matches.put(m.id, m);
        return m;
    }

    private Innings newInnings(String bat, String bowl) {
        Innings in = new Innings();
        in.battingTeam = bat; in.bowlingTeam = bowl;
        for (String n : SQUADS.get(bat)) in.batters.add(new Batter(n));
        String[] bw = SQUADS.get(bowl);
        for (int i = 6; i < 11; i++) in.bowlers.add(new Bowler(bw[i]));
        return in;
    }

    /** Scheduled engine: one ball per live match every tick. */
    @Scheduled(fixedRateString = "${cricket.tick-ms:6000}")
    public void tickAll() {
        matches.values().forEach(m -> { if (tick(m)) publish(m); });
    }

    /** Also usable for manual scoring: POST /api/matches/{id}/ball */
    public Match ball(String id) {
        Match m = get(id);
        if (tick(m)) publish(m);
        return m;
    }

    private boolean tick(Match m) {
        synchronized (m) {
            if (!"LIVE".equals(m.status)) return false;
            Innings in = m.innings.get(m.current);
            Bowler bw = in.bowlers.get(in.bowler);
            Batter st = in.batters.get(in.striker);
            int r = rnd.nextInt(100), runs = 0; boolean wkt = false;
            if (r < 4) wkt = true;
            else if (r < 30) runs = 0; else if (r < 58) runs = 1; else if (r < 70) runs = 2;
            else if (r < 72) runs = 3; else if (r < 86) runs = 4; else runs = 6;

            in.balls++; bw.balls++; st.balls++;
            String over = (in.balls - 1) / 6 + "." + ((in.balls - 1) % 6 + 1);
            String text;
            if (wkt) {
                st.out = true; in.wickets++; bw.wickets++;
                text = "WICKET! " + st.name + " b " + bw.name;
                if (in.nextIn <= 10) in.striker = in.nextIn++;
            } else {
                st.runs += runs; in.runs += runs; bw.runs += runs;
                if (runs == 4) st.fours++;
                if (runs == 6) st.sixes++;
                text = bw.name + " to " + st.name + ": " + (runs == 0 ? "dot ball" : runs == 4 ? "FOUR" : runs == 6 ? "SIX" : runs + " run" + (runs > 1 ? "s" : ""));
                if (runs % 2 == 1) { int t = in.striker; in.striker = in.nonStriker; in.nonStriker = t; }
            }
            if (in.balls % 6 == 0) {
                int t = in.striker; in.striker = in.nonStriker; in.nonStriker = t;
                in.bowler = (in.bowler + 1) % in.bowlers.size();
            }
            m.commentary.add(0, over + "  " + text);
            if (m.commentary.size() > 40) m.commentary.remove(m.commentary.size() - 1);

            boolean inningsOver = in.wickets >= 10 || in.balls >= m.overs * 6
                || (m.current == 1 && in.runs >= m.target);
            if (inningsOver) {
                if (m.current == 0) {
                    m.target = in.runs + 1; m.current = 1;
                    m.commentary.add(0, "Innings break. " + m.innings.get(1).battingTeam + " need " + m.target + " to win.");
                } else {
                    m.status = "COMPLETED";
                    m.result = in.runs >= m.target
                        ? in.battingTeam + " won by " + (10 - in.wickets) + " wickets"
                        : in.runs == m.target - 1 ? "Match tied"
                        : in.bowlingTeam + " won by " + (m.target - 1 - in.runs) + " runs";
                    m.commentary.add(0, "Match over. " + m.result);
                }
            }
            return true;
        }
    }

    // ---------- real-time push (Server-Sent Events) ----------
    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        e.onCompletion(() -> emitters.remove(e));
        e.onTimeout(() -> emitters.remove(e));
        e.onError(x -> emitters.remove(e));
        return e;
    }

    private void publish(Match m) {
        for (SseEmitter e : emitters) {
            try { synchronized (m) { e.send(SseEmitter.event().name("match").data(m)); } }
            catch (IOException | IllegalStateException ex) { emitters.remove(e); }
        }
    }
}
