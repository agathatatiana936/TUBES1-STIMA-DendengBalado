package alternative_bot_2;

import battlecode.common.*;

public class Tower {

    public static void run() throws GameActionException {
        tryAttack();
        trySpawn();
    }

    static void tryAttack() throws GameActionException {
        RobotInfo best = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyEnemyLen; --i >= 0;) {
            RobotInfo e = G.nearbyEnemies[i];
            if (!G.rc.canAttack(e.location)) continue;
            int score = (e.health <= 50  ? 80 : e.health <= 150 ? 40 : 10)
                      + (Util.isTower(e.type) ? 50
                       : e.type == UnitType.SOLDIER ? 25
                       : e.type == UnitType.SPLASHER ? 20 : 10)
                      - G.me.distanceSquaredTo(e.location);
            if (score > bestScore) { bestScore = score; best = e; }
        }
        if (best != null && G.rc.canAttack(best.location)) G.rc.attack(best.location);
    }

    static int spawnedSoldiers  = 0;
    static int spawnedMoppers   = 0;
    static int spawnedSplashers = 0;

    static void trySpawn() throws GameActionException {
        if (!G.rc.isActionReady()) return;
        int tp = G.rc.getPaint();
        UnitType toBuild = chooseUnit(tp);
        if (toBuild == null) return;
        if (tryBuild(toBuild)) {
            if      (toBuild == UnitType.SOLDIER)  spawnedSoldiers++;
            else if (toBuild == UnitType.MOPPER)   spawnedMoppers++;
            else if (toBuild == UnitType.SPLASHER) spawnedSplashers++;
        }
    }

    static UnitType chooseUnit(int tp) {
        if (G.round <= 40) return (tp >= 200 && G.chips >= 250) ? UnitType.SOLDIER : null;

        int wantMoppers   = Math.max(1, spawnedSoldiers / 3);
        int wantSplashers = spawnedSoldiers >= 3 ? Math.max(1, spawnedSoldiers / 5) : 0;

        if (spawnedMoppers < wantMoppers && tp >= 100 && G.chips >= 300)
            return UnitType.MOPPER;

        if (spawnedSplashers < wantSplashers && tp >= 300 && G.chips >= 400)
            return UnitType.SPLASHER;

        if (tp >= 200 && G.chips >= 250) return UnitType.SOLDIER;

        return null;
    }

    static boolean tryBuild(UnitType type) throws GameActionException {
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        for (int di = 8; --di >= 0;) {
            Direction dir = G.DIRS[di];
            MapLocation nxt = G.me.add(dir);
            if (!G.rc.canBuildRobot(type, nxt)) continue;

            MapInfo info = TowerBuilder.findTileInfo(nxt);
            PaintType p  = (info != null) ? info.getPaint() : PaintType.EMPTY;

            int score = (p.isAlly() ? 20 : p == PaintType.EMPTY ? 8 : -15)
                      - Greedy.countAdjacentAllies(nxt) * 4;

            if (G.inferredEnemyBase != null) {
                Direction toEnemy = G.me.directionTo(G.inferredEnemyBase);
                if (dir == toEnemy || dir == toEnemy.rotateLeft() || dir == toEnemy.rotateRight())
                    score += 15;
            }

            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        if (bestDir != null) { G.rc.buildRobot(type, G.me.add(bestDir)); return true; }
        return false;
    }
}

class TowerBuilder {
    static MapLocation cachedRuin     = null;
    static UnitType    cachedGoalType = null;
    static int         cachedRound    = -99;

    static boolean tryTowerRoutine() throws GameActionException {
        if (G.myType != UnitType.SOLDIER) return false;
        if (G.paint < 35) return false;

        if (tryUpgradeAdjacentTower()) return true;

        MapLocation ruin;
        UnitType    goalType;
        if (cachedRuin != null && (G.round - cachedRound) <= 3) {
            ruin     = cachedRuin;
            goalType = cachedGoalType;
        } else {
            ruin = findBestNearbyRuin();
            if (ruin == null) return false;
            goalType    = chooseTowerType(ruin);
            cachedRuin  = ruin; cachedGoalType = goalType; cachedRound = G.round;
        }

        if (G.rc.canCompleteTowerPattern(goalType, ruin)) {
            G.rc.completeTowerPattern(goalType, ruin);
            cachedRuin = null;
            return true;
        }
        if (tryPaintTowerPattern(ruin, goalType)) return true;
        if (Motion.moveToward(ruin, true)) return true;
        return false;
    }

    static boolean tryUpgradeAdjacentTower() throws GameActionException {
        if (G.chips < 2600) return false;
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (!Util.isTower(ally.type)) continue;
            if (G.me.distanceSquaredTo(ally.location) > 2) continue;
            if (ally.type == UnitType.LEVEL_ONE_PAINT_TOWER
                    || ally.type == UnitType.LEVEL_ONE_MONEY_TOWER) {
                if (G.rc.canUpgradeTower(ally.location)) {
                    G.rc.upgradeTower(ally.location);
                    return true;
                }
            }
        }
        return false;
    }

    static MapLocation findBestNearbyRuin() throws GameActionException {
        MapLocation best = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapInfo info = G.nearbyTiles[i];
            if (!info.hasRuin()) continue;
            MapLocation loc = info.getMapLocation();
            RobotInfo onRuin = G.rc.canSenseLocation(loc) ? G.rc.senseRobotAtLocation(loc) : null;
            if (onRuin != null && Util.isTower(onRuin.type)) continue;

            int score = -G.me.distanceSquaredTo(loc) * 3
                      + quickPatternCount(loc, chooseTowerType(loc)) * 6
                      + (G.round <= 140 ? 30 : G.round <= 650 ? 15 : 0);

            if (G.inferredEnemyBase != null)
                score += loc.distanceSquaredTo(G.inferredEnemyBase) <= 100 ? 20 : 0;

            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return best;
    }

    static int quickPatternCount(MapLocation ruin, UnitType type) {
        boolean[][] pat = getPattern(type);
        int correct = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapInfo tile = G.nearbyTiles[i];
            MapLocation loc = tile.getMapLocation();
            int dx = loc.x - ruin.x, dy = loc.y - ruin.y;
            if (dx < -2 || dx > 2 || dy < -2 || dy > 2) continue;
            if (dx == 0 && dy == 0) continue;
            if (tile.hasRuin() || tile.isWall()) continue;
            boolean ns = pat[dx + 2][dy + 2];
            PaintType p = tile.getPaint();
            if (p.isAlly() && ((ns && p == PaintType.ALLY_SECONDARY) || (!ns && p == PaintType.ALLY_PRIMARY))) correct++;
        }
        return correct;
    }

    static UnitType chooseTowerType(MapLocation ruin) {
        if (G.lastSeenEnemyTower != null && ruin.distanceSquaredTo(G.lastSeenEnemyTower) <= 36)
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        if (G.inferredEnemyBase != null && ruin.distanceSquaredTo(G.inferredEnemyBase) <= 50)
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        if (G.round <= 80)  return UnitType.LEVEL_ONE_MONEY_TOWER;
        if (G.round <= 600) return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static boolean tryPaintTowerPattern(MapLocation ruin, UnitType type) throws GameActionException {
        if (!G.rc.isActionReady()) return false;
        boolean[][] pat = getPattern(type);
        int progressBonus = quickPatternCount(ruin, type) * 4;

        MapLocation bestLoc = null; boolean bestSec = false; int bestScore = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation loc = ruin.translate(dx, dy);
                if (!G.rc.canAttack(loc)) continue;
                MapInfo info = findTileInfo(loc);
                if (info == null || info.hasRuin() || info.isWall()) continue;
                boolean ns = pat[dx + 2][dy + 2];
                PaintType cur = info.getPaint();
                if (cur.isAlly() && ((ns && cur == PaintType.ALLY_SECONDARY) || (!ns && cur == PaintType.ALLY_PRIMARY))) continue;
                int score = 50 + progressBonus
                          + (cur != PaintType.EMPTY && !cur.isAlly() ? 20 : 0)
                          - G.me.distanceSquaredTo(loc);
                if (score > bestScore) { bestScore = score; bestLoc = loc; bestSec = ns; }
            }
        }
        if (bestLoc != null) { G.rc.attack(bestLoc, bestSec); G.paint -= 5; return true; }
        return false;
    }

    static MapInfo findTileInfo(MapLocation loc) {
        for (int i = G.nearbyTileLen; --i >= 0;)
            if (G.nearbyTiles[i].getMapLocation().equals(loc)) return G.nearbyTiles[i];
        return null;
    }

    static boolean[][] getPattern(UnitType type) {
        if (type == UnitType.LEVEL_ONE_PAINT_TOWER)   return G.paintPattern;
        if (type == UnitType.LEVEL_ONE_DEFENSE_TOWER) return G.defensePattern;
        return G.moneyPattern;
    }
}