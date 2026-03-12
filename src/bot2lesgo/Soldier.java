package bot2lesgo;

import battlecode.common.*;

public class Soldier {

    public static final int MODE_BUILD_TOWER = 0;
    public static final int MODE_BUILD_RESOURCE = 1;
    public static final int MODE_EXPAND = 2;
    public static final int MODE_ATTACK = 3;
    public static final int MODE_REFILL = 4;

    public static int mode = MODE_EXPAND;

    public static MapLocation ruinTarget = null;
    public static MapLocation resourceTarget = null;
    public static MapLocation frontierTarget = null;

    public static UnitType plannedTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;

    public static int modeRound = -1000000;
    public static int lastObjectiveRound = -1000000;

    public static final int OBJECTIVE_TIMEOUT = 20;
    public static final int FRONTIER_TIMEOUT = 18;
    public static final int BUILD_PATTERN_REACH_DIST2 = 8;
    public static final int RESOURCE_PATTERN_REACH_DIST2 = 8;
    public static final int FRONTIER_REACHED_DIST2 = 2;

    public static final int LOCAL_EXPAND_PAINT_THRESHOLD = 24;

    public static void init() {
        mode = MODE_EXPAND;
        ruinTarget = null;
        resourceTarget = null;
        frontierTarget = null;
        plannedTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;
        modeRound = G.round;
        lastObjectiveRound = G.round;
    }

    public static void run() throws Exception {
        if (G.lowPaint()) {
            mode = MODE_REFILL;
        } else {
            updateMode();
        }

        switch (mode) {
            case MODE_REFILL -> runRefillMode();
            case MODE_BUILD_TOWER -> runBuildTowerMode();
            case MODE_BUILD_RESOURCE -> runBuildResourceMode();
            case MODE_ATTACK -> runAttackMode();
            default -> runExpandMode();
        }

        appendIndicator();
    }

    public static void updateMode() throws GameActionException {
        MapLocation visibleFreeRuin = findVisibleFreeRuin();
        if (visibleFreeRuin != null) {
            ruinTarget = visibleFreeRuin;
            plannedTowerType = chooseTowerTypeForRuin(ruinTarget);
            setMode(MODE_BUILD_TOWER);
            return;
        }

        if (hasVisibleResourceWork()) {
            resourceTarget = findVisibleResourceCenter();
            if (resourceTarget != null) {
                setMode(MODE_BUILD_RESOURCE);
                return;
            }
        }

        MapLocation knownFreeRuin = POI.closestUnoccupiedRuin();
        if (knownFreeRuin != null && isFeasibleRuin(knownFreeRuin)) {
            ruinTarget = knownFreeRuin;
            plannedTowerType = chooseTowerTypeForRuin(ruinTarget);
            setMode(MODE_BUILD_TOWER);
            return;
        }

        MapLocation enemyTower = POI.closestEnemyTower();
        if (enemyTower != null && G.isLateGame()) {
            frontierTarget = enemyTower;
            setMode(MODE_ATTACK);
            return;
        }

        setMode(MODE_EXPAND);
    }

    private static void setMode(int newMode) {
        if (mode != newMode) {
            mode = newMode;
            modeRound = G.round;
        }
    }

    public static void runRefillMode() throws GameActionException {
        if (!G.lowPaint()) {
            setMode(MODE_EXPAND);
            return;
        }

        if (Robot.tryRefillFromNearbyAllyTower()) {
            return;
        }

        MapLocation paintTower = POI.closestPaintTower();
        if (paintTower == null) {
            paintTower = POI.closestAllyTower();
        }

        if (paintTower != null) {
            Motion.moveToward(paintTower);
            return;
        }

        Motion.retreat();
    }

    public static void runBuildTowerMode() throws Exception {
        if (ruinTarget == null || shouldRefreshRuinTarget()) {
            ruinTarget = findVisibleFreeRuin();
            if (ruinTarget == null) {
                MapLocation poiRuin = POI.closestUnoccupiedRuin();
                if (poiRuin != null && isFeasibleRuin(poiRuin)) {
                    ruinTarget = poiRuin;
                }
            }
            if (ruinTarget != null) {
                plannedTowerType = chooseTowerTypeForRuin(ruinTarget);
                lastObjectiveRound = G.round;
            }
        }

        if (ruinTarget == null) {
            setMode(MODE_EXPAND);
            runExpandMode();
            return;
        }

        if (G.rc.canSenseRobotAtLocation(ruinTarget)) {
            RobotInfo occupant = G.rc.senseRobotAtLocation(ruinTarget);
            if (occupant != null && occupant.type.isTowerType()) {
                POI.upsertPOI(ruinTarget, POI.towerTypeToPOIType(occupant));
                ruinTarget = null;
                setMode(MODE_EXPAND);
                runExpandMode();
                return;
            }
        }

        if (G.rc.canCompleteTowerPattern(plannedTowerType, ruinTarget)) {
            G.rc.completeTowerPattern(plannedTowerType, ruinTarget);
            POI.upsertPOI(ruinTarget, allyTowerTypeToPOI(plannedTowerType));
            ruinTarget = null;
            setMode(MODE_EXPAND);
            return;
        }

        if (G.me.distanceSquaredTo(ruinTarget) > BUILD_PATTERN_REACH_DIST2) {
            Motion.moveToward(ruinTarget);
            if (G.rc.isActionReady()) {
                tryPaintBestLocalTile();
            }
            return;
        }

        if (tryPaintTowerPattern(ruinTarget, plannedTowerType)) {
            return;
        }

        Motion.moveToward(ruinTarget);
    }

    private static boolean shouldRefreshRuinTarget() {
        if (ruinTarget == null) return true;
        return G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT;
    }

    public static UnitType chooseTowerTypeForRuin(MapLocation ruin) {
        if (G.lowPaint()) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if (G.hasFreshDefenseTowerInfo() && G.isLateGame()) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        return Tower.preferredTowerToBuildNext();
    }

    public static boolean tryPaintTowerPattern(MapLocation ruin, UnitType towerType) throws GameActionException {
        boolean[][] pattern = Robot.getTowerPattern(towerType);
        if (pattern == null) return false;
        return tryPaintPatternAroundCenter(ruin, pattern);
    }

    public static void runBuildResourceMode() throws Exception {
        if (resourceTarget == null || shouldRefreshResourceTarget()) {
            resourceTarget = findVisibleResourceCenter();
            lastObjectiveRound = G.round;
        }

        if (resourceTarget == null) {
            setMode(MODE_EXPAND);
            runExpandMode();
            return;
        }

        if (G.rc.canCompleteResourcePattern(resourceTarget)) {
            G.rc.completeResourcePattern(resourceTarget);
            resourceTarget = null;
            setMode(MODE_EXPAND);
            return;
        }

        if (G.me.distanceSquaredTo(resourceTarget) > RESOURCE_PATTERN_REACH_DIST2) {
            Motion.moveToward(resourceTarget);
            if (G.rc.isActionReady()) {
                tryPaintBestLocalTile();
            }
            return;
        }

        if (tryPaintResourcePattern(resourceTarget)) {
            return;
        }

        Motion.moveToward(resourceTarget);
    }

    private static boolean shouldRefreshResourceTarget() {
        if (resourceTarget == null) return true;
        return G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT;
    }

    public static boolean tryPaintResourcePattern(MapLocation center) throws GameActionException {
        return tryPaintPatternAroundCenter(center, Robot.resourcePattern);
    }

    public static void runExpandMode() throws Exception {
        if (frontierTarget == null
                || G.me.distanceSquaredTo(frontierTarget) <= FRONTIER_REACHED_DIST2
                || G.round - lastObjectiveRound >= FRONTIER_TIMEOUT) {
            frontierTarget = chooseFrontierTarget();
            lastObjectiveRound = G.round;
        }

        int localScore = scoreBestLocalPaintTile();
        if (localScore >= LOCAL_EXPAND_PAINT_THRESHOLD) {
            if (tryPaintBestLocalTile()) return;
        }

        if (frontierTarget != null) {
            Motion.moveToward(frontierTarget);
            if (G.rc.isActionReady()) {
                tryPaintBestLocalTile();
            }
            return;
        }

        Motion.explore();
        if (G.rc.isActionReady()) {
            tryPaintBestLocalTile();
        }
    }

    public static int scoreBestLocalPaintTile() throws GameActionException {
        int bestScore = Integer.MIN_VALUE;

        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(
                G.me, G.rc.getType().actionRadiusSquared);

        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null || !G.rc.canAttack(loc) || !G.rc.canSenseLocation(loc)) continue;

            MapInfo info = G.rc.senseMapInfo(loc);
            int score = 0;

            if (G.isNeutralPaint(info)) score += 10;
            if (G.isEnemyPaint(info)) score += 14;
            if (G.isFriendlyPaint(info)) score -= 12;
            if (info.hasRuin()) score += 16;
            if (info.isResourcePatternCenter()) score += 12;

            score += countAdjacentNonFriendly(loc) * 4;
            score -= G.me.distanceSquaredTo(loc);

            if (score > bestScore) bestScore = score;
        }

        return bestScore;
    }

    private static int countAdjacentNonFriendly(MapLocation center) throws GameActionException {
        int count = 0;
        for (int i = G.DIRECTIONS.length - 1; i >= 0; i--) {
            MapLocation n = center.add(G.DIRECTIONS[i]);
            if (!G.rc.onTheMap(n) || !G.rc.canSenseLocation(n)) continue;
            MapInfo info = G.rc.senseMapInfo(n);
            if (!G.isFriendlyPaint(info)) count++;
        }
        return count;
    }

    public static MapLocation chooseFrontierTarget() throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            int score = 0;

            if (G.isNeutralPaint(info)) score += 28;
            if (G.isEnemyPaint(info)) score += 24;
            if (G.isFriendlyPaint(info)) score -= 10;
            if (info.hasRuin()) score += 20;
            if (info.isResourcePatternCenter()) score += 8;

            score += countAdjacentNonFriendly(loc) * 3;
            score -= G.me.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) return best;

        MapLocation predicted = POI.predictedEnemyFromSymmetry();
        if (predicted != null) return predicted;

        return G.mapCenter;
    }

    public static void runAttackMode() throws Exception {
        if (tryAttackEnemyRobot()) {
            return;
        }

        if (frontierTarget == null || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            frontierTarget = POI.closestEnemyTower();
            if (frontierTarget == null) frontierTarget = POI.predictedEnemyFromSymmetry();
            lastObjectiveRound = G.round;
        }

        if (frontierTarget != null) {
            if (G.me.distanceSquaredTo(frontierTarget) > G.rc.getType().actionRadiusSquared) {
                Motion.moveToward(frontierTarget);
                if (G.rc.isActionReady()) {
                    tryPaintBestLocalTile();
                }
                return;
            }
        }

        if (tryPaintBestLocalTile()) {
            return;
        }

        Motion.explore();
    }

    public static boolean tryPaintPatternAroundCenter(MapLocation center, boolean[][] pattern) throws GameActionException {
        if (center == null || pattern == null) return false;
        if (!G.rc.isActionReady()) return false;

        int w = pattern.length;
        int h = pattern[0].length;
        int ox = w / 2;
        int oy = h / 2;

        MapLocation bestLoc = null;
        boolean bestSecondary = false;
        int bestScore = Integer.MIN_VALUE;

        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                MapLocation loc = new MapLocation(center.x + dx - ox, center.y + dy - oy);
                if (!G.onMap(loc)) continue;
                if (!G.rc.canSenseLocation(loc)) continue;
                if (!G.rc.canAttack(loc)) continue;

                boolean wantSecondary = pattern[dx][dy];
                if (!needsPaint(loc, wantSecondary)) continue;

                int score = 0;
                score -= center.distanceSquaredTo(loc);
                score -= G.me.distanceSquaredTo(loc);

                MapInfo info = G.rc.senseMapInfo(loc);
                if (G.isNeutralPaint(info)) score += 8;
                if (G.isEnemyPaint(info)) score += 12;

                if (score > bestScore) {
                    bestScore = score;
                    bestLoc = loc;
                    bestSecondary = wantSecondary;
                }
            }
        }

        if (bestLoc != null) {
            G.rc.attack(bestLoc, bestSecondary);
            return true;
        }

        return false;
    }

    public static boolean tryPaintBestLocalTile() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        MapLocation best = null;
        boolean bestSecondary = false;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            if (!G.rc.canAttack(loc)) continue;

            int score = 0;

            if (G.isNeutralPaint(info)) score += 20;
            if (G.isEnemyPaint(info)) score += 24;
            if (G.isFriendlyPaint(info)) score -= 25;

            if (info.hasRuin()) score += 16;
            if (info.isResourcePatternCenter()) score += 8;

            score += countAdjacentNonFriendly(loc) * 3;
            score -= G.me.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
                bestSecondary = false;
            }
        }

        if (best != null && bestScore > 0) {
            G.rc.attack(best, bestSecondary);
            return true;
        }

        return false;
    }

    public static boolean tryAttackEnemyRobot() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;
            if (!enemy.type.isRobotType()) continue;
            if (!G.rc.canAttack(enemy.location)) continue;

            int score = 0;
            score += 200 - enemy.health;
            if (enemy.type == UnitType.SOLDIER) score += 25;
            if (enemy.type == UnitType.SPLASHER) score += 20;
            if (enemy.type == UnitType.MOPPER) score += 15;
            score -= G.me.distanceSquaredTo(enemy.location);

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }

        if (best != null) {
            G.rc.attack(best.location);
            return true;
        }

        return false;
    }

    public static boolean needsPaint(MapLocation loc, boolean wantSecondary) throws GameActionException {
        MapInfo info = G.rc.senseMapInfo(loc);
        PaintType paint = info.getPaint();

        if (wantSecondary) {
            return paint != PaintType.ALLY_SECONDARY;
        } else {
            return paint != PaintType.ALLY_PRIMARY;
        }
    }

    public static MapLocation findVisibleFreeRuin() throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin == null) continue;

            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(ruin);
                if (robot != null && robot.type.isTowerType()) continue;
            }

            if (!isFeasibleRuin(ruin)) continue;

            int d = G.me.distanceSquaredTo(ruin);
            if (d < bestDist) {
                bestDist = d;
                best = ruin;
            }
        }

        return best;
    }

    public static boolean isFeasibleRuin(MapLocation ruin) throws GameActionException {
        int enemyPaint = 0;
        int nearbyAllies = 0;
        int nearbyEnemyRobots = 0;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            if (loc.distanceSquaredTo(ruin) > 8) continue;
            if (G.isEnemyPaint(info)) enemyPaint++;
        }

        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null || !ally.type.isRobotType()) continue;
            if (ally.location.distanceSquaredTo(ruin) <= 8) nearbyAllies++;
        }

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null || !enemy.type.isRobotType()) continue;
            if (enemy.location.distanceSquaredTo(ruin) <= 8) nearbyEnemyRobots++;
        }

        if (enemyPaint >= 5) return false;
        if (nearbyAllies >= 3) return false;
        if (nearbyEnemyRobots >= 2 && enemyPaint >= 3) return false;

        return true;
    }

    public static boolean hasVisibleResourceWork() {
        return findVisibleResourceCenter() != null;
    }

    public static MapLocation findVisibleResourceCenter() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;
            if (!info.isResourcePatternCenter()) continue;

            MapLocation loc = info.getMapLocation();
            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static int allyTowerTypeToPOI(UnitType towerType) {
        UnitType base = towerType.getBaseType();
        if (base == UnitType.LEVEL_ONE_MONEY_TOWER) return POI.TYPE_ALLY_MONEY;
        if (base == UnitType.LEVEL_ONE_PAINT_TOWER) return POI.TYPE_ALLY_PAINT;
        return POI.TYPE_ALLY_DEFENSE;
    }

    public static void appendIndicator() {
        G.addIndicator("SM", mode);
        if (ruinTarget != null) G.addIndicator("RU", ruinTarget);
        if (resourceTarget != null) G.addIndicator("RS", resourceTarget);
        if (frontierTarget != null) G.addIndicator("FR", frontierTarget);
    }
}