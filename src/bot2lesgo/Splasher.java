package bot2lesgo;

import battlecode.common.*;

public class Splasher {

    public static final int MODE_REFILL = 0;
    public static final int MODE_CLEAR_FRONTIER = 1;
    public static final int MODE_PRESSURE_ENEMY = 2;
    public static final int MODE_EXPLORE = 3;

    public static int mode = MODE_EXPLORE;

    public static MapLocation frontierTarget = null;
    public static MapLocation enemyTarget = null;

    public static int modeRound = -1000000;
    public static int lastObjectiveRound = -1000000;

    public static final int OBJECTIVE_TIMEOUT = 18;
    public static final int FRONTIER_REACHED_DIST2 = 4;
    public static final int ENEMY_PRESSURE_REACHED_DIST2 = 8;
    public static final int MIN_SCORE_TO_ATTACK = 22;
    public static final int STRONG_SPLASH_THRESHOLD = 28;

    public static void init() {
        mode = MODE_EXPLORE;
        frontierTarget = null;
        enemyTarget = null;
        modeRound = G.round;
        lastObjectiveRound = G.round;
    }

    public static void run() throws Exception {
        updateMode();

        switch (mode) {
            case MODE_REFILL -> runRefillMode();
            case MODE_CLEAR_FRONTIER -> runClearFrontierMode();
            case MODE_PRESSURE_ENEMY -> runPressureEnemyMode();
            default -> runExploreMode();
        }

        appendIndicator();
    }

    public static void updateMode() throws GameActionException {
        if (G.lowPaint()) {
            setMode(MODE_REFILL);
            return;
        }

        MapLocation enemyTower = POI.closestEnemyTower();
        if (enemyTower != null && (G.isMidGame() || G.isLateGame())) {
            enemyTarget = enemyTower;
            setMode(MODE_PRESSURE_ENEMY);
            return;
        }

        if (hasGoodLocalSplashTarget()) {
            setMode(MODE_CLEAR_FRONTIER);
            return;
        }

        setMode(MODE_EXPLORE);
    }

    private static void setMode(int newMode) {
        if (mode != newMode) {
            mode = newMode;
            modeRound = G.round;
        }
    }

    public static void runRefillMode() throws GameActionException {
        if (!G.lowPaint()) {
            setMode(MODE_EXPLORE);
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

    public static void runClearFrontierMode() throws Exception {
        if (frontierTarget == null
                || G.me.distanceSquaredTo(frontierTarget) <= FRONTIER_REACHED_DIST2
                || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            frontierTarget = chooseFrontierTarget();
            lastObjectiveRound = G.round;
        }

        int localScore = bestSplashScore();
        if (localScore >= STRONG_SPLASH_THRESHOLD) {
            if (tryBestSplashAttack()) return;
        }

        if (frontierTarget != null) {
            Motion.moveToward(frontierTarget);
            if (G.rc.isActionReady()) {
                tryBestSplashAttack();
            }
            return;
        }

        Motion.explore();
        if (G.rc.isActionReady()) {
            tryBestSplashAttack();
        }
    }

    public static void runPressureEnemyMode() throws Exception {
        if (enemyTarget == null || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            enemyTarget = POI.closestEnemyTower();
            if (enemyTarget == null) enemyTarget = POI.predictedEnemyFromSymmetry();
            lastObjectiveRound = G.round;
        }

        int localScore = bestSplashScore();
        if (localScore >= MIN_SCORE_TO_ATTACK) {
            if (tryBestSplashAttack()) return;
        }

        if (enemyTarget != null) {
            if (G.me.distanceSquaredTo(enemyTarget) > ENEMY_PRESSURE_REACHED_DIST2) {
                Motion.moveToward(enemyTarget);
                if (G.rc.isActionReady()) {
                    tryBestSplashAttack();
                }
                return;
            }

            if (tryPaintEnemySideTile()) {
                return;
            }
        }

        Motion.explore();
    }

    public static void runExploreMode() throws Exception {
        if (frontierTarget == null
                || G.me.distanceSquaredTo(frontierTarget) <= FRONTIER_REACHED_DIST2
                || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            frontierTarget = chooseFrontierTarget();
            lastObjectiveRound = G.round;
        }

        int localScore = bestSplashScore();
        if (localScore >= STRONG_SPLASH_THRESHOLD) {
            if (tryBestSplashAttack()) return;
        }

        if (frontierTarget != null) {
            Motion.moveToward(frontierTarget);
            if (G.rc.isActionReady()) {
                tryBestSplashAttack();
            }
            return;
        }

        Motion.explore();
        if (G.rc.isActionReady()) {
            tryBestSplashAttack();
        }
    }

    public static int bestSplashScore() throws GameActionException {
        int best = Integer.MIN_VALUE;
        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(
                G.me, G.rc.getType().actionRadiusSquared);

        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null || !G.rc.canAttack(loc)) continue;
            int s = scoreSplashAttack(loc);
            if (s > best) best = s;
        }
        return best;
    }

    public static boolean tryBestSplashAttack() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(G.me, G.rc.getType().actionRadiusSquared);
        MapLocation bestLoc = null;
        boolean bestSecondary = false;
        int bestScore = Integer.MIN_VALUE;

        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            if (!G.rc.canAttack(loc)) continue;

            int score = scoreSplashAttack(loc);
            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
                bestSecondary = false;
            }
        }

        if (bestLoc != null && bestScore >= MIN_SCORE_TO_ATTACK) {
            G.rc.attack(bestLoc, bestSecondary);
            return true;
        }

        return false;
    }

    public static int scoreSplashAttack(MapLocation center) throws GameActionException {
        int score = 0;

        MapLocation[] area = G.rc.getAllLocationsWithinRadiusSquared(center, 4);

        for (int i = area.length - 1; i >= 0; i--) {
            MapLocation loc = area[i];
            if (loc == null) continue;
            if (!G.rc.canSenseLocation(loc)) continue;

            MapInfo info = G.rc.senseMapInfo(loc);

            if (G.isEnemyPaint(info)) score += 12;
            else if (G.isNeutralPaint(info)) score += 8;
            else if (G.isFriendlyPaint(info)) score -= 8;

            if (info.hasRuin()) score += 6;
            if (info.isResourcePatternCenter()) score += 4;

            score += countAdjacentNonFriendly(loc) * 2;
        }

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;

            int d = center.distanceSquaredTo(enemy.location);

            if (enemy.type.isTowerType()) {
                if (d <= 8) score += 18;
            } else {
                if (d <= 4) score += 8;
                if (d <= 2) score += 4;
            }
        }

        score -= G.me.distanceSquaredTo(center) / 2;
        return score;
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

    public static boolean hasGoodLocalSplashTarget() throws GameActionException {
        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(G.me, G.rc.getType().actionRadiusSquared);

        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            if (!G.rc.canAttack(loc)) continue;
            if (scoreSplashAttack(loc) >= MIN_SCORE_TO_ATTACK) return true;
        }

        return false;
    }

    public static boolean tryPaintEnemySideTile() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(G.me, G.rc.getType().actionRadiusSquared);
        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            if (!G.rc.canAttack(loc)) continue;

            int score = 0;

            if (G.rc.canSenseLocation(loc)) {
                MapInfo info = G.rc.senseMapInfo(loc);
                if (G.isEnemyPaint(info)) score += 20;
                else if (G.isNeutralPaint(info)) score += 12;
                else if (G.isFriendlyPaint(info)) score -= 20;

                if (info.hasRuin()) score += 8;
            }

            if (enemyTarget != null) {
                score -= loc.distanceSquaredTo(enemyTarget);
            }

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null && bestScore > 0) {
            G.rc.attack(best, false);
            return true;
        }

        return false;
    }

    public static MapLocation chooseFrontierTarget() throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            int score = 0;

            if (G.isEnemyPaint(info)) score += 30;
            else if (G.isNeutralPaint(info)) score += 22;
            else score -= 10;

            if (info.hasRuin()) score += 10;
            if (info.isResourcePatternCenter()) score += 6;

            score += countAdjacentNonFriendly(loc) * 3;
            score -= G.me.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) return best;

        MapLocation enemy = POI.closestEnemyTower();
        if (enemy != null) return enemy;

        return POI.predictedEnemyFromSymmetry();
    }

    public static void appendIndicator() {
        G.addIndicator("SPM", mode);
        if (frontierTarget != null) G.addIndicator("SPF", frontierTarget);
        if (enemyTarget != null) G.addIndicator("SPE", enemyTarget);
    }
}