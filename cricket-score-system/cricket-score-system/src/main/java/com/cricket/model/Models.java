package com.cricket.model;

import java.util.*;

public class Models {

    public static class Batter {
        public String name; public int runs, balls, fours, sixes; public boolean out;
        public Batter(String n) { name = n; }
        public double getStrikeRate() { return balls == 0 ? 0 : Math.round(runs * 10000.0 / balls) / 100.0; }
    }

    public static class Bowler {
        public String name; public int balls, runs, wickets;
        public Bowler(String n) { name = n; }
        public String getOvers() { return balls / 6 + "." + balls % 6; }
        public double getEconomy() { return balls == 0 ? 0 : Math.round(runs * 600.0 / balls) / 100.0; }
    }

    public static class Innings {
        public String battingTeam, bowlingTeam;
        public int runs, wickets, balls, striker = 0, nonStriker = 1, nextIn = 2, bowler = 0;
        public List<Batter> batters = new ArrayList<>();
        public List<Bowler> bowlers = new ArrayList<>();
        public String getOvers() { return balls / 6 + "." + balls % 6; }
        public double getRunRate() { return balls == 0 ? 0 : Math.round(runs * 600.0 / balls) / 100.0; }
    }

    public static class Match {
        public String id, title, venue, status = "LIVE", result = "";
        public int overs, current = 0, target = 0;
        public List<Innings> innings = new ArrayList<>();
        public List<String> commentary = new ArrayList<>();
    }

    public record CreateMatchRequest(String home, String away, Integer overs, String venue) {}
}
