package main_bot;

import battlecode.common.*;

public class RobotPlayer {

    public static void run(RobotController rc) {
        try {
            G.rc = rc;
            G.mapWidth   = rc.getMapWidth();
            G.mapHeight  = rc.getMapHeight();
            G.mapArea    = G.mapWidth * G.mapHeight;
            G.mapCenter  = new MapLocation(G.mapWidth / 2, G.mapHeight / 2);
            G.team         = rc.getTeam();
            G.opponentTeam = G.team.opponent();
            G.roundSpawned = rc.getRoundNum();
            G.indicator    = new StringBuilder();

            Rand.state = rc.getID() * 0x1A3C5E7 + 0x9B2D4F;

            updateRound();

            // init tiap robot
            switch (rc.getType()) {
                case SOLDIER -> Soldier.init();
                case SPLASHER -> Splasher.init();
                case MOPPER  -> Mopper.init();
                default      -> Tower.init();
            }

            // main loopnya
            while (true) {
                int r = rc.getRoundNum();
                try {
                    updateRound();
                    switch (rc.getType()) {
                        case SOLDIER  -> Soldier.run();
                        case SPLASHER -> Splasher.run();
                        case MOPPER   -> Mopper.run();
                        default       -> Tower.run();
                    }
                    rc.setIndicatorString(G.indicator.toString());
                    G.indicator = new StringBuilder();
                } catch (GameActionException e) {
                    System.out.println("GAE at round " + r + ": " + e.getMessage());
                    G.indicator = new StringBuilder();
                } catch (Exception e) {
                    System.out.println("EX at round " + r + ": " + e.getMessage());
                    G.indicator = new StringBuilder();
                    e.printStackTrace();
                }
                if (rc.getRoundNum() != r) {
                    System.err.println("BYTECODE OVERFLOW round=" + r + " type=" + rc.getType());
                }
                Clock.yield();
            }
        } catch (Exception e) {
            System.out.println("Fatal error in init: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // perbarui state global tiap round
    static void updateRound() throws Exception {
        G.round = G.rc.getRoundNum();
        G.me    = G.rc.getLocation();
        G.nearbyAllies   = G.rc.senseNearbyRobots(-1, G.team);
        G.nearbyEnemies  = G.rc.senseNearbyRobots(-1, G.opponentTeam);
        G.nearbyMapInfos = G.rc.senseNearbyMapInfos();
        G.nearbyRuins    = G.rc.senseNearbyRuins(-1);
        G.markVisited(G.me);

        MapMemory.scanNearby();
    }
}
