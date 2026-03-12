package bot_1;

import battlecode.common.*;

public class Tower {
    static int buildCount = 0;
    public static void runTower(RobotController rc) throws GameActionException{
        int round = rc.getRoundNum();
        if(rc.senseNearbyRobots( -1, rc.getTeam().opponent()).length > 0) {
            tryAttackEnemies(rc);
            trySpawn(rc, UnitType.MOPPER, false);
        }
        if (round <= 300) {
            trySpawn(rc, UnitType.SOLDIER, true);
        } else if (round <= 1000) {  
            int mod = buildCount % 2;
            UnitType nextType = (mod == 0) ? UnitType.SPLASHER : UnitType.SOLDIER;
            boolean allowFallbackSoldier = nextType != UnitType.SPLASHER;
            trySpawn(rc, nextType, allowFallbackSoldier);
        } else{
            int mod = buildCount % 2;
            UnitType nextType = (mod == 0) ? UnitType.SPLASHER : UnitType.MOPPER;
            boolean allowFallbackSoldier = nextType != UnitType.SPLASHER;
            trySpawn(rc, nextType, allowFallbackSoldier);
        }

        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }
    }

    private static MapLocation getBestSpawnLocation(RobotController rc, UnitType unitType) throws GameActionException {
        int mapH = rc.getMapHeight();
        int mapW = rc.getMapWidth();
        MapLocation center = new MapLocation(mapW / 2, mapH / 2);

        int bestScore = Integer.MIN_VALUE;
        MapLocation bestLoc = null;
        for (Direction d : Direction.allDirections()) {
            MapLocation nextLoc = rc.getLocation().add(d);
            if (!rc.canBuildRobot(unitType, nextLoc)) {
                continue;
            }

            int score = 0;
            score -= nextLoc.distanceSquaredTo(center);

            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                score += nextLoc.distanceSquaredTo(ally.getLocation());
            }

            for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
                score -= nextLoc.distanceSquaredTo(enemy.getLocation());
            }

            if (score > bestScore) {
                bestScore = score;
                bestLoc = nextLoc;
            }
        }

        return bestLoc;
    }

    private static boolean trySpawn(RobotController rc, UnitType unitType, boolean allowFallbackSoldier) throws GameActionException {
        MapLocation nextLoc = getBestSpawnLocation(rc, unitType);
        if (nextLoc == null && allowFallbackSoldier) {
            nextLoc = getBestSpawnLocation(rc, UnitType.SOLDIER);
            unitType = UnitType.SOLDIER;
        }
        if (nextLoc != null && rc.canBuildRobot(unitType, nextLoc)) {
            rc.buildRobot(unitType, nextLoc);
            System.out.println("BUILT A " + unitType);
            buildCount++;
            return true;
        }
        if (unitType == UnitType.SPLASHER) {
            System.out.println("SKIP SPLASHER: cannot build this turn (resource/space). Saving for next turn.");
        }
        return false;
    }

    private static void tryAttackEnemies(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                rc.attack(null);
                return;
            }
        }
    }
}
