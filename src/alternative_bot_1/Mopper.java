package alternative_bot_1;

import battlecode.common.*;

public class Mopper {
    public static void runMopper(RobotController rc) throws GameActionException {
        Comms.startTurn(rc);
        int round = rc.getRoundNum();
        if (round <= 600) {
            Soldier.runEarly(rc);
        } else if (round <= 1000) {
            runMid(rc);
        } else {
            runLate(rc);
        }
    }

    private static void runMid(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation nearestEnemy = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            int dist = rc.getLocation().distanceSquaredTo(e.getLocation());
            if (dist < closestDist) { closestDist = dist; nearestEnemy = e.getLocation(); }
        }
        Direction dir;
        if (nearestEnemy != null) {
            dir = Motion.moveTowardLocation(rc, nearestEnemy);
        } else {
            dir = Motion.moveTowardNearestAllySoldier(rc);
            if (dir == null) dir = Motion.moveTowardEnemyPaint(rc);
        }
        MapLocation nextLoc = null;
        if (dir != null) {
            nextLoc = rc.getLocation().add(dir);
            if (rc.canMove(dir)) rc.move(dir);
        }
        if (dir != null && rc.canMopSwing(dir)) {
            rc.mopSwing(dir);
            System.out.println("Mop Swing!");
        } else if (nextLoc != null && rc.canAttack(nextLoc)) {
            rc.attack(nextLoc);
        }
    }

    private static void runLate(RobotController rc) throws GameActionException {
        Direction dir = Motion.moveTowardNearestAllySoldier(rc);
        if (dir == null) dir = Motion.moveTowardEnemyPaint(rc);
        MapLocation nextLoc = null;
        if (dir != null) {
            nextLoc = rc.getLocation().add(dir);
            if (rc.canMove(dir)) rc.move(dir);
        }
        if (dir != null && rc.canMopSwing(dir)) {
            rc.mopSwing(dir);
        } else if (nextLoc != null && rc.canAttack(nextLoc)) {
            rc.attack(nextLoc);
        }
    }
}

