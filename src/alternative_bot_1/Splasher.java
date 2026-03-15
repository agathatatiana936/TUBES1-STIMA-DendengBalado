package alternative_bot_1;

import battlecode.common.*;

public class Splasher {
    static MapLocation knownEnemyTower = null;

    public static void runSplasher(RobotController rc) throws GameActionException {
        Comms.startTurn(rc);
        if (Comms.enemyTowerLoc != null) {
            knownEnemyTower = Comms.enemyTowerLoc;
        }
        int round = rc.getRoundNum();
        if (round <= 600) {
            runEarly(rc);
        } else if (round <= 1000) {
            runMid(rc);
        } else {
            runLate(rc);
        }
    }

    private static void runEarly(RobotController rc) throws GameActionException {
        Direction dir = knownEnemyTower != null ? Motion.moveTowardLocation(rc, knownEnemyTower) : Motion.moveTowardNearestEnemyTower(rc);
        if (dir == null) {
            dir = Motion.moveTowardCenter(rc);
        }
        if (dir != null && rc.canMove(dir)) rc.move(dir);
        if (rc.canAttack(rc.getLocation())) rc.attack(rc.getLocation());
        Soldier.paintCurrentTileIfNeeded(rc);
    }

    private static void runMid(RobotController rc) throws GameActionException {
        Direction dir = knownEnemyTower != null ? Motion.moveTowardLocation(rc, knownEnemyTower) : Motion.moveTowardNearestEnemyTower(rc);
        if (dir == null) dir = Motion.moveTowardCorner(rc);
        if (dir != null && rc.canMove(dir)) { rc.move(dir); }
        if (Soldier.tryAttackEnemyTower(rc)) return;
        Soldier.tryAttackEnemyTower(rc);
        Soldier.paintCurrentTileIfNeeded(rc);
    }

    private static void runLate(RobotController rc) throws GameActionException {
        Direction dir = knownEnemyTower != null ? Motion.moveTowardLocation(rc, knownEnemyTower) : Motion.moveTowardNearestEnemyTower(rc);
        if (Soldier.tryAttackEnemyTower(rc)) return;
        if (dir == null) {
            dir = Motion.moveTowardCorner(rc);
        }
        if (dir != null && rc.canMove(dir)) rc.move(dir);
        MapLocation ahead = dir != null ? rc.getLocation().add(dir) : rc.getLocation();
        MapInfo dirInfo = rc.senseMapInfo(ahead);
        if (dirInfo.getPaint() == PaintType.ENEMY_PRIMARY
                || dirInfo.getPaint() == PaintType.ENEMY_SECONDARY
                || dirInfo.getPaint() == PaintType.EMPTY) {
            if (rc.canAttack(rc.getLocation())) rc.attack(rc.getLocation());
        }
    }
}
