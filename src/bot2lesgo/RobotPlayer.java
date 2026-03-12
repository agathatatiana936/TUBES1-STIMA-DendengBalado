package bot2lesgo;

import battlecode.common.*;

public class RobotPlayer {

    /**
     * Refresh semua cache sensor yang sering dipakai unit lain.
     * Panggil setiap awal ronde, dan setiap habis bergerak bila perlu.
     */
    public static void updateInfo() throws GameActionException {
        G.me = G.rc.getLocation();
        G.allyRobots = G.rc.senseNearbyRobots(-1, G.team);
        G.opponentRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
        G.nearbyMapInfos = G.rc.senseNearbyMapInfos();
        G.nearbyRuins = G.rc.senseNearbyRuins(-1);

        // cache string lokasi robot sekitar untuk heuristic/micro yang sudah kamu punya
        G.allyRobotsString = new StringBuilder();
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].type.isRobotType()) {
                G.allyRobotsString.append(G.allyRobots[i].location.toString());
            }
        }

        G.opponentRobotsString = new StringBuilder();
        for (int i = G.opponentRobots.length; --i >= 0;) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy.type.isRobotType()) {
                G.opponentRobotsString.append(enemy.location.toString());
            } else if (enemy.type.getBaseType() == UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                G.lastDefenseTower = enemy.location;
                G.lastDefenseTowerRound = G.rc.getRoundNum();
            }
        }
    }

    /**
     * Dipanggil sesudah robot bergerak.
     * Supaya cache posisi/sensing sinkron lagi untuk aksi lanjutan di turn yang sama.
     */
    public static void updateMove() throws GameActionException {
        updateInfo();

        // dipakai oleh Motion untuk anti-loop / anti bolak-balik
        Motion.lastVisitedLocations.append(G.me.toString());

        // padding tetap 8 char per location-string history chunk
        switch (Motion.lastVisitedLocations.length() % 8) {
            case 6 -> Motion.lastVisitedLocations.append("  ");
            case 7 -> Motion.lastVisitedLocations.append(" ");
        }

        G.setLastVisited(G.me, G.rc.getRoundNum());
    }

    /**
     * Dipanggil sekali di awal tiap ronde.
     */
    public static void updateRound() throws Exception {
        int currentRound = G.rc.getRoundNum();

        // bookkeeping ekonomi global
        G.maxChips = Math.max(G.maxChips, G.rc.getChips());

        // sinkronisasi movement cooldown model internal
        int elapsedRounds = currentRound - G.round;
        if (elapsedRounds > 0) {
            Motion.movementCooldown -= GameConstants.COOLDOWNS_PER_TURN * elapsedRounds;
            if (Motion.movementCooldown < 0) {
                Motion.movementCooldown = 0;
            }
        }

        G.round = currentRound;
        updateInfo();
        POI.updateRound();
    }

    private static void initGlobals(RobotController rc) throws Exception {
        G.rc = rc;

        G.mapWidth = G.rc.getMapWidth();
        G.mapHeight = G.rc.getMapHeight();
        G.mapArea = G.mapWidth * G.mapHeight;
        G.mapCenter = new MapLocation(G.mapWidth / 2, G.mapHeight / 2);

        G.team = G.rc.getTeam();
        G.opponentTeam = G.team.opponent();

        G.roundSpawned = G.rc.getRoundNum();
        G.round = G.rc.getRoundNum();

        G.indicatorString = new StringBuilder();
        G.lastChips = G.rc.getChips();
        G.lastNumberTowers = G.rc.getNumberTowers();

        updateInfo();
    }

    private static void initUnit() throws Exception {
        if (G.rc.getType().isRobotType()) {
            Robot.init();
        } else {
            Tower.init();
        }
    }

    private static void runUnitTurn() throws Exception {
        if (G.rc.getType().isRobotType()) {
            Robot.run();
        } else {
            Tower.run();
        }
    }

    private static void flushIndicator() {
        try {
            G.rc.setIndicatorString(G.indicatorString.toString());
        } catch (Exception ignored) {
        }
        G.indicatorString = new StringBuilder();
    }

    public static void run(RobotController rc) {
        try {
            initGlobals(rc);
            initUnit();

            G.indicatorString.append("INIT=").append(Clock.getBytecodeNum()).append(" ");

            while (true) {
                int roundAtTurnStart = G.rc.getRoundNum();

                try {
                    updateRound();
                    runUnitTurn();
                } catch (GameActionException e) {
                    System.out.println("Unexpected GameActionException");
                    G.indicatorString.append("GA_ERR ");
                    e.printStackTrace();
                } catch (Exception e) {
                    System.out.println("Unexpected Exception");
                    G.indicatorString.append("ERR ");
                    e.printStackTrace();
                }

                // deteksi bytecode overflow / spill ke ronde berikutnya
                if (G.rc.getRoundNum() != roundAtTurnStart) {
                    System.err.println(
                        "Bytecode overflow! Round=" + roundAtTurnStart
                        + " Type=" + G.rc.getType()
                        + " Loc=" + G.rc.getLocation()
                    );
                    G.indicatorString.append("BYTE=").append(roundAtTurnStart).append(" ");
                }

                flushIndicator();

                G.lastChips = G.rc.getChips();
                G.lastNumberTowers = G.rc.getNumberTowers();

                Clock.yield();
            }
        } catch (Exception e) {
            System.out.println("Fatal exception in RobotPlayer");
            e.printStackTrace();
        }
    }
}