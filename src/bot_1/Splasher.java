package bot_1;

import battlecode.common.*;

public class Splasher {
    public static void runSplasher(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        if (round <= 200) {
            runEarly(rc);
        } else if (round <= 800) {
            runMid(rc);
        } else {
            runLate(rc);
        }
    }

    private static void runEarly(RobotController rc) throws GameActionException {
        Direction dir = Motion.moveTowardCenter(rc);
        if (dir != null && rc.canMove(dir)) rc.move(dir);
        if (rc.canAttack(rc.getLocation())) rc.attack(rc.getLocation());
    }

    private static void runMid(RobotController rc) throws GameActionException {
        // if (Soldier.tryAttackEnemyTower(rc)) return;
        // Soldier.tryAttackEnemyTower(rc);
        // for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
        //     if (rc.canAttack(enemy.getLocation())) {
        //         rc.attack(enemy.getLocation());
        //         return;
        //     }
        // }
        // Direction dir = Motion.moveTowardEnemyPaint(rc);
        // if (dir != null && rc.canMove(dir)) rc.move(dir);
        // if (rc.canAttack(rc.getLocation())) rc.attack(rc.getLocation());
        Soldier.runEarly(rc);
    }

    private static void runLate(RobotController rc) throws GameActionException {
        Direction dir = Motion.moveTowardCorner(rc);
        if (dir == null) return;
        MapInfo dirInfo = rc.senseMapInfo(rc.getLocation().add(dir));
        if (rc.canMove(dir)) rc.move(dir);
        if (dirInfo.getPaint() == PaintType.ENEMY_PRIMARY
                || dirInfo.getPaint() == PaintType.ENEMY_SECONDARY
                || dirInfo.getPaint() == PaintType.EMPTY) {
            if (rc.canAttack(rc.getLocation())) rc.attack(rc.getLocation());
        }
    }
}

