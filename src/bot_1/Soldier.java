package bot_1;

import battlecode.common.*;

public class Soldier {
    static Direction lastDir = null;
    static MapLocation spawnTower = null;
    static MapLocation knownEnemyTower = null;

    public static void runSoldier(RobotController rc) throws GameActionException {
        captureSpawnTower(rc);
        RobotInfo visible = findVisibleEnemyTower(rc);
        if (visible != null) knownEnemyTower = visible.getLocation();

        int round = rc.getRoundNum();
        if (round <= 200) {
            runEarly(rc);
        } else if (round <= 1000) {
            runMid(rc);
        } else {
            runLate(rc);
        }
    }

    public static void runEarly(RobotController rc) throws GameActionException {
        if (tryAttackEnemyTower(rc)) return;
        MapLocation target = knownEnemyTower != null ? knownEnemyTower : getSymmetryTarget(rc);
        if (target != null) {
            Direction dir = Motion.moveTowardLocation(rc, target);
            if (dir != null && rc.canMove(dir)) { rc.move(dir); lastDir = dir; }
        }
        tryAttackEnemyTower(rc);
        paintCurrentTileIfNeeded(rc);
    }

    private static void runMid(RobotController rc) throws GameActionException {
        if (tryAttackEnemyTower(rc)) return;
        if (knownEnemyTower != null) {
            Direction dir = Motion.moveTowardLocation(rc, knownEnemyTower);
            if (dir != null && rc.canMove(dir)) { rc.move(dir); lastDir = dir; }
            tryAttackEnemyTower(rc);
        } else {
            runEarly(rc);
            return;
        }
        paintCurrentTileIfNeeded(rc);
    }

    private static void runLate(RobotController rc) throws GameActionException {
        if (tryAttackEnemyTower(rc)) return;
        MapInfo curRuin = findNearbyRuin(rc);
        if (curRuin != null) { buildRuin(rc, curRuin.getMapLocation()); return; }
        Direction dir = Motion.moveTowardEnemyPaint(rc);
        if (dir != null && rc.canMove(dir)) { rc.move(dir); lastDir = dir; }
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (rc.canAttack(enemy.getLocation())) { rc.attack(enemy.getLocation()); break; }
        }
        paintCurrentTileIfNeeded(rc);
    }

    private static void captureSpawnTower(RobotController rc) throws GameActionException {
        if (spawnTower != null) {
            return;
        }

        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (isTowerType(ally.getType())) {
                spawnTower = ally.getLocation();
                return;
            }
        }
    }

    private static MapInfo findNearbyRuin(RobotController rc) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.hasRuin()) {
                return tile;
            }
        }
        return null;
    }

    private static RobotInfo findVisibleEnemyTower(RobotController rc) throws GameActionException {
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (isTowerType(enemy.getType())) {
                return enemy;
            }
        }
        return null;
    }

    public static boolean tryAttackEnemyTower(RobotController rc) throws GameActionException {
        RobotInfo enemyTower = findVisibleEnemyTower(rc);
        if (enemyTower == null) {
            return false;
        }

        MapLocation towerLoc = enemyTower.getLocation();
        knownEnemyTower = towerLoc;
        if (rc.canAttack(towerLoc)) {
            rc.attack(towerLoc);
            return true;
        }
        return false;
    }

    public static void paintCurrentTileIfNeeded(RobotController rc) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    public static MapLocation getSymmetryTarget(RobotController rc) {
        if (spawnTower == null) {
            return null;
        }

        int maxX = rc.getMapWidth() - 1;
        int maxY = rc.getMapHeight() - 1;
        MapLocation[] candidates = new MapLocation[] {
            new MapLocation(maxX - spawnTower.x, spawnTower.y),
            new MapLocation(spawnTower.x, maxY - spawnTower.y),
            new MapLocation(maxX - spawnTower.x, maxY - spawnTower.y)
        };
        return candidates[rc.getID() % candidates.length];
    }

    private static boolean isTowerType(UnitType unitType) {
        return unitType == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || unitType == UnitType.LEVEL_ONE_MONEY_TOWER
            || unitType == UnitType.LEVEL_ONE_PAINT_TOWER
            || unitType == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || unitType == UnitType.LEVEL_TWO_MONEY_TOWER
            || unitType == UnitType.LEVEL_TWO_PAINT_TOWER
            || unitType == UnitType.LEVEL_THREE_DEFENSE_TOWER
            || unitType == UnitType.LEVEL_THREE_MONEY_TOWER
            || unitType == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    public static void buildRuin(RobotController rc, MapLocation targetLoc) throws GameActionException{
        Direction dir = rc.getLocation().directionTo(targetLoc);
        if (rc.canMove(dir))
            rc.move(dir);
        MapLocation shouldBeMarked = targetLoc.subtract(dir);
        if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
            System.out.println("Trying to build a tower at " + targetLoc);
        }
        for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
            if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                if (rc.canAttack(patternTile.getMapLocation()))
                    rc.attack(patternTile.getMapLocation(), useSecondaryColor);
            }
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
            rc.setTimelineMarker("Tower built", 0, 255, 0);
            System.out.println("Built a tower at " + targetLoc + "!");
        }
    }

}
