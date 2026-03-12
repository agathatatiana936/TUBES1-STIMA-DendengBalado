package bot2lesgo;

import battlecode.common.*;

public class Mopper {

    public static final int MODE_REFILL = 0;
    public static final int MODE_CLEAN = 1;
    public static final int MODE_DEFEND_RUIN = 2;
    public static final int MODE_CHASE_ENEMY = 3;
    public static final int MODE_EXPLORE = 4;

    public static int mode = MODE_EXPLORE;

    public static MapLocation cleanTarget = null;
    public static MapLocation defendTarget = null;
    public static MapLocation chaseTarget = null;

    public static int modeRound = -1000000;
    public static int lastObjectiveRound = -1000000;

    public static final int OBJECTIVE_TIMEOUT = 16;
    public static final int CLEAN_REACHED_DIST2 = 2;
    public static final int DEFEND_REACHED_DIST2 = 8;
    public static final int CHASE_REACHED_DIST2 = 2;

    public static final int MIN_ATTACK_SCORE = 8;
    public static final int MIN_SWING_SCORE = 2;

    public static void init() {
        mode = MODE_EXPLORE;
        cleanTarget = null;
        defendTarget = null;
        chaseTarget = null;
        modeRound = G.round;
        lastObjectiveRound = G.round;
    }

    public static void run() throws Exception {
        updateMode();

        switch (mode) {
            case MODE_REFILL -> runRefillMode();
            case MODE_CLEAN -> runCleanMode();
            case MODE_DEFEND_RUIN -> runDefendRuinMode();
            case MODE_CHASE_ENEMY -> runChaseEnemyMode();
            default -> runExploreMode();
        }

        appendIndicator();
    }

    public static void updateMode() throws GameActionException {
        if (G.lowPaint()) {
            setMode(MODE_REFILL);
            return;
        }

        MapLocation ruin = findThreatenedRuin();
        if (ruin != null) {
            defendTarget = ruin;
            setMode(MODE_DEFEND_RUIN);
            return;
        }

        MapLocation localEnemyPaint = findBestEnemyPaintTarget();
        if (localEnemyPaint != null) {
            cleanTarget = localEnemyPaint;
            setMode(MODE_CLEAN);
            return;
        }

        RobotInfo chase = bestNearbyEnemyRobot();
        if (chase != null) {
            chaseTarget = chase.location;
            setMode(MODE_CHASE_ENEMY);
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

    public static void runCleanMode() throws Exception {
        if (tryBestMopSwing()) return;
        if (tryBestMopAttack()) return;

        if (cleanTarget == null
                || G.me.distanceSquaredTo(cleanTarget) <= CLEAN_REACHED_DIST2
                || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            cleanTarget = findBestEnemyPaintTarget();
            lastObjectiveRound = G.round;
        }

        if (cleanTarget != null) {
            Motion.moveToward(cleanTarget);
            if (G.rc.isActionReady()) {
                tryBestMopAttack();
            }
            return;
        }

        Motion.explore();
    }

    public static void runDefendRuinMode() throws Exception {
        if (tryBestMopSwing()) return;
        if (tryBestMopAttack()) return;

        if (defendTarget == null
                || G.me.distanceSquaredTo(defendTarget) <= DEFEND_REACHED_DIST2
                || G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            defendTarget = findThreatenedRuin();
            lastObjectiveRound = G.round;
        }

        if (defendTarget != null) {
            Motion.moveToward(defendTarget);
            if (G.rc.isActionReady()) {
                tryBestMopAttack();
            }
            return;
        }

        Motion.explore();
    }

    public static void runChaseEnemyMode() throws Exception {
        if (tryBestMopSwing()) return;
        if (tryBestMopAttack()) return;

        RobotInfo chase = bestNearbyEnemyRobot();
        if (chase != null) {
            chaseTarget = chase.location;
            lastObjectiveRound = G.round;
        } else if (G.round - lastObjectiveRound >= OBJECTIVE_TIMEOUT) {
            chaseTarget = null;
        }

        if (chaseTarget != null) {
            if (G.me.distanceSquaredTo(chaseTarget) > CHASE_REACHED_DIST2) {
                Motion.moveToward(chaseTarget);
                if (G.rc.isActionReady()) {
                    tryBestMopAttack();
                }
                return;
            }
        }

        cleanTarget = findBestEnemyPaintTarget();
        if (cleanTarget != null) {
            Motion.moveToward(cleanTarget);
            return;
        }

        Motion.explore();
    }

    public static void runExploreMode() throws Exception {
        if (tryBestMopSwing()) return;
        if (tryBestMopAttack()) return;

        cleanTarget = findBestEnemyPaintTarget();
        if (cleanTarget != null) {
            Motion.moveToward(cleanTarget);
            if (G.rc.isActionReady()) {
                tryBestMopAttack();
            }
            return;
        }

        MapLocation threatened = findThreatenedRuin();
        if (threatened != null) {
            defendTarget = threatened;
            Motion.moveToward(defendTarget);
            return;
        }

        MapLocation enemyTower = POI.closestEnemyTower();
        if (enemyTower != null && G.isLateGame()) {
            Motion.moveToward(enemyTower);
            return;
        }

        Motion.explore();
    }

    public static boolean tryBestMopSwing() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < G.DIRECTIONS.length; i++) {
            Direction dir = G.DIRECTIONS[i];
            if (!G.rc.canMopSwing(dir)) continue;

            int score = scoreMopSwing(dir);
            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != Direction.CENTER && bestScore >= MIN_SWING_SCORE) {
            G.rc.mopSwing(bestDir);
            return true;
        }

        return false;
    }

    public static int scoreMopSwing(Direction dir) {
        int score = 0;

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;
            if (!enemy.type.isRobotType()) continue;

            Direction d = G.me.directionTo(enemy.location);
            if (d == Direction.CENTER) {
                score += 2;
                continue;
            }

            if (sameGeneralDirection(dir, d)) {
                score += 3;
                if (enemy.type == UnitType.SOLDIER) score += 2;
                if (enemy.type == UnitType.SPLASHER) score += 2;
            }
        }

        return score;
    }

    public static boolean tryBestMopAttack() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        MapLocation[] locs = G.rc.getAllLocationsWithinRadiusSquared(G.me, G.rc.getType().actionRadiusSquared);
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = locs.length - 1; i >= 0; i--) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            if (!G.rc.canAttack(loc)) continue;

            int score = scoreMopAttack(loc);
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null && bestScore >= MIN_ATTACK_SCORE) {
            G.rc.attack(best);
            return true;
        }

        return false;
    }

    public static int scoreMopAttack(MapLocation loc) throws GameActionException {
        int score = 0;

        if (G.rc.canSenseLocation(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);

            if (G.isEnemyPaint(info)) score += 20;
            else if (G.isNeutralPaint(info)) score += 2;
            else if (G.isFriendlyPaint(info)) score -= 18;

            if (isNearAnyRuin(loc, 8)) score += 30;
            if (isNearAnyAllyTower(loc, 8)) score += 26;
            if (info.hasRuin()) score += 10;
            if (info.isResourcePatternCenter()) score += 6;
        }

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;

            int d = loc.distanceSquaredTo(enemy.location);
            if (enemy.type.isRobotType()) {
                if (d <= 2) score += 18;
                else if (d <= 4) score += 8;
            }
        }

        score -= G.me.distanceSquaredTo(loc);
        return score;
    }

    public static MapLocation findBestEnemyPaintTarget() throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            int score = 0;

            if (G.isEnemyPaint(info)) score += 24;
            else if (G.isNeutralPaint(info)) score += 2;
            else score -= 16;

            if (isNearAnyRuin(loc, 8)) score += 30;
            if (isNearAnyAllyTower(loc, 8)) score += 26;
            if (info.hasRuin()) score += 10;
            if (info.isResourcePatternCenter()) score += 8;

            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(loc);
                if (robot != null && robot.team == G.opponentTeam && robot.type.isRobotType()) {
                    score += 18;
                }
            }

            score -= G.me.distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        return bestScore > 0 ? best : null;
    }

    public static MapLocation findThreatenedRuin() throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin == null) continue;

            int score = 0;

            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(ruin);
                if (robot != null && robot.type.isTowerType()) {
                    continue;
                }
            }

            for (int j = G.nearbyMapInfos.length - 1; j >= 0; j--) {
                MapInfo info = G.nearbyMapInfos[j];
                if (info == null) continue;

                MapLocation loc = info.getMapLocation();
                if (ruin.distanceSquaredTo(loc) > 8) continue;

                if (G.isEnemyPaint(info)) score += 10;
                if (G.isNeutralPaint(info)) score += 1;
            }

            for (int j = G.opponentRobots.length - 1; j >= 0; j--) {
                RobotInfo enemy = G.opponentRobots[j];
                if (enemy == null) continue;
                if (!enemy.type.isRobotType()) continue;

                if (ruin.distanceSquaredTo(enemy.location) <= 8) {
                    score += 12;
                }
            }

            score -= G.me.distanceSquaredTo(ruin);

            if (score > bestScore) {
                bestScore = score;
                best = ruin;
            }
        }

        return bestScore > 0 ? best : null;
    }

    public static RobotInfo bestNearbyEnemyRobot() {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;
            if (!enemy.type.isRobotType()) continue;

            int score = 0;
            score += 200 - enemy.health;

            if (enemy.type == UnitType.SOLDIER) score += 25;
            if (enemy.type == UnitType.SPLASHER) score += 18;
            if (enemy.type == UnitType.MOPPER) score += 12;

            score -= G.me.distanceSquaredTo(enemy.location);

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }

        return best;
    }

    private static boolean isNearAnyRuin(MapLocation loc, int dist2) {
        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin != null && loc.distanceSquaredTo(ruin) <= dist2) return true;
        }
        return false;
    }

    private static boolean isNearAnyAllyTower(MapLocation loc, int dist2) {
        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null || !ally.type.isTowerType()) continue;
            if (loc.distanceSquaredTo(ally.location) <= dist2) return true;
        }
        return false;
    }

    public static boolean sameGeneralDirection(Direction a, Direction b) {
        if (a == b) return true;
        if (a == Direction.CENTER || b == Direction.CENTER) return false;
        return a.rotateLeft() == b || a.rotateRight() == b;
    }

    public static void appendIndicator() {
        G.addIndicator("MM", mode);
        if (cleanTarget != null) G.addIndicator("MC", cleanTarget);
        if (defendTarget != null) G.addIndicator("MD", defendTarget);
        if (chaseTarget != null) G.addIndicator("MH", chaseTarget);
    }
}