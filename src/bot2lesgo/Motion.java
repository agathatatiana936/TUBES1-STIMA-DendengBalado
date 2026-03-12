package bot2lesgo;

import battlecode.common.*;

public class Motion {

    // =========================================================
    // STATE
    // =========================================================
    public static int movementCooldown = 0;
    public static int lastMove = -1000000;

    public static Direction lastDir = Direction.CENTER;
    public static StringBuilder lastVisitedLocations = new StringBuilder();

    // dipakai Robot.java
    public static int lastPaint = 0;
    public static int paintLost = 0;
    public static int paintNeededToStopRetreating = 0;

    // exploration state
    public static MapLocation exploreTarget = null;
    public static int exploreTargetRound = -1000000;

    // =========================================================
    // TUNING
    // =========================================================
    public static final int EXPLORE_TARGET_TIMEOUT = 20;
    public static final int EXPLORE_REACHED_DIST2 = 4;
    public static final int RETREAT_TOWER_SEARCH_DIST2 = 1000;

    public static final int ALLY_TOWER_HALO_PENALTY = 22;
    public static final int VIRGIN_FRONTIER_BONUS = 18;
    // =========================================================
    // BASIC MOVE API
    // =========================================================
    public static boolean canMove(Direction dir) {
        return dir != null
                && dir != Direction.CENTER
                && G.rc.isMovementReady()
                && G.rc.canMove(dir);
    }

    public static boolean move(Direction dir) throws GameActionException {
        if (!canMove(dir)) return false;

        G.rc.move(dir);
        lastDir = dir;
        lastMove = G.round;

        RobotPlayer.updateMove();
        return true;
    }

    public static boolean moveToward(MapLocation target) throws GameActionException {
        return bugnavTowards(target);
    }

    public static boolean moveAwayFrom(MapLocation target) throws GameActionException {
        return bugnavAway(target);
    }

    // =========================================================
    // HIGH LEVEL NAVIGATION
    // =========================================================
    public static boolean bugnavTowards(MapLocation target) throws GameActionException {
        if (!G.rc.isMovementReady()) return false;
        if (target == null) return false;
        if (G.me.equals(target)) return false;

        Direction bestDir = chooseBestDir(target, false);
        if (bestDir != Direction.CENTER) {
            return move(bestDir);
        }

        // fallback: greedy local turn order around desired direction
        Direction desired = G.me.directionTo(target);
        return tryGreedyFallback(desired, false);
    }

    public static boolean bugnavAway(MapLocation danger) throws GameActionException {
        if (!G.rc.isMovementReady()) return false;
        if (danger == null) return false;

        Direction bestDir = chooseBestDir(danger, true);
        if (bestDir != Direction.CENTER) {
            return move(bestDir);
        }

        Direction desired = danger.directionTo(G.me);
        return tryGreedyFallback(desired, true);
    }

    /**
     * Retreat utama:
     * 1. kalau ada allied tower terlihat, mundur ke tower terdekat
     * 2. kalau tidak ada, mundur dari musuh terdekat
     * 3. kalau tidak ada musuh, cari tile friendly paint terbaik
     */
    public static boolean retreat() throws GameActionException {
        if (!G.rc.isMovementReady()) return false;

        MapLocation tower = closestAllyTower();
        if (tower != null) {
            return bugnavTowards(tower);
        }

        RobotInfo nearestEnemy = G.closestRobot(G.opponentRobots);
        if (nearestEnemy != null) {
            return bugnavAway(nearestEnemy.location);
        }

        Direction bestPaintDir = bestPaintSeekingDir();
        if (bestPaintDir != Direction.CENTER) {
            return move(bestPaintDir);
        }

        return explore();
    }

    // =========================================================
    // EXPLORATION
    // =========================================================
    public static boolean explore() throws GameActionException {
        if (!G.rc.isMovementReady()) return false;

        if (shouldRefreshExploreTarget()) {
            exploreTarget = chooseExploreTarget();
            exploreTargetRound = G.round;
        }

        if (exploreTarget == null) return false;
        return bugnavTowards(exploreTarget);
    }

    private static boolean shouldRefreshExploreTarget() {
        if (exploreTarget == null) return true;
        if (G.me.distanceSquaredTo(exploreTarget) <= EXPLORE_REACHED_DIST2) return true;
        return G.round - exploreTargetRound >= EXPLORE_TARGET_TIMEOUT;
    }

    /**
     * Eksplorasi deterministik:
     * - pilih dari 4 sudut + tengah
     * - prioritaskan yang belum/recently unvisited
     * - tie-break ke yang lebih jauh dari posisi sekarang agar coverage cepat meluas
     */
    public static MapLocation chooseExploreTarget() {
        MapLocation[] candidates = new MapLocation[] {
                new MapLocation(0, 0),
                new MapLocation(0, G.mapHeight - 1),
                new MapLocation(G.mapWidth - 1, 0),
                new MapLocation(G.mapWidth - 1, G.mapHeight - 1),
                G.mapCenter
        };

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < candidates.length; i++) {
            MapLocation loc = candidates[i];
            int score = 0;

            // semakin lama tidak dikunjungi, semakin bagus
            int lastSeen = G.getLastVisited(loc);
            score += G.round - lastSeen;

            // prefer target yang agak jauh agar expansion merata
            score += G.me.distanceSquaredTo(loc);

            // sedikit variasi deterministik antar unit
            score += ((G.rc.getID() + i) & 3);

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        return best;
    }

    // =========================================================
    // LOCAL MOVE CHOICE
    // =========================================================
    /**
     * Core movement scorer.
     *
     * away == false : bergerak mendekati target
     * away == true  : bergerak menjauhi target
     */
    public static Direction chooseBestDir(MapLocation target, boolean away) throws GameActionException {
        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;

        int currentDist = G.me.distanceSquaredTo(target);
        MapLocation allyTower = closestAllyTower();

        for (int i = 0; i < G.DIRECTIONS.length; i++) {
            Direction dir = G.DIRECTIONS[i];
            if (!G.rc.canMove(dir)) continue;

            MapLocation next = G.me.add(dir);
            int score = 0;

            int nextDist = next.distanceSquaredTo(target);

            // progress ke target / menjauhi target
            if (!away) {
                score += (currentDist - nextDist) * G.SCORE_APPROACH_TARGET;
            } else {
                score += (nextDist - currentDist) * G.SCORE_APPROACH_TARGET;
            }

            // prefer tile yang belum lama dikunjungi
            if (G.recentlyVisited(next, G.STALE_VISITED_ROUNDS)) {
                score -= 20;
            } else {
                score += G.SCORE_UNVISITED_TILE;
            }

            // evaluasi info map sekali saja
            if (G.rc.canSenseLocation(next)) {
                MapInfo info = G.rc.senseMapInfo(next);

                if (G.isNeutralPaint(info)) {
                    score += 10;
                    score += VIRGIN_FRONTIER_BONUS;
                } else if (G.isEnemyPaint(info)) {
                    score += 16;
                } else if (G.isFriendlyPaint(info)) {
                    score -= 1;
                }

                if (info.hasRuin()) {
                    score += G.SCORE_NEAR_RUIN_BONUS;
                }
            }

            // jangan muter dekat tower kalau bukan retreat/refill
            if (allyTower != null && !away) {
                int towerDist = next.distanceSquaredTo(allyTower);
                if (towerDist <= 8) score -= ALLY_TOWER_HALO_PENALTY;
                else if (towerDist <= 18) score -= 8;
            }

            // hindari crowding ally
            score += countAdjacentAllies(next) * G.SCORE_CROWDING_PENALTY;
            // penalti / bonus ancaman lawan
            score += enemyThreatAdjustment(next);

            // konsistensi arah
            if (dir == lastDir) score += 4;
            if (lastDir != Direction.CENTER && dir == lastDir.opposite()) score -= 12;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    private static int enemyThreatAdjustment(MapLocation loc) {
        int score = 0;

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;

            int d = loc.distanceSquaredTo(enemy.location);

            if (enemy.type.isTowerType()) {
                if (d <= enemy.type.actionRadiusSquared) {
                    score += G.SCORE_NEAR_ENEMY_TOWER_PENALTY;
                }
                continue;
            }

            if (enemy.type.isRobotType()) {
                if (d <= enemy.type.actionRadiusSquared) {
                    score -= 14;
                }
                if (d <= 2) {
                    score -= 8;
                }
            }
        }

        return score;
    }

    private static int countAdjacentAllies(MapLocation loc) {
        int cnt = 0;
        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null) continue;
            if (!ally.type.isRobotType()) continue;
            if (ally.location.distanceSquaredTo(loc) <= 2) cnt++;
        }
        return cnt;
    }

    // =========================================================
    // FALLBACK MOVE ORDER
    // =========================================================
    private static boolean tryGreedyFallback(Direction desired, boolean away) throws GameActionException {
        Direction[] order = new Direction[] {
                desired,
                desired.rotateLeft(),
                desired.rotateRight(),
                desired.rotateLeft().rotateLeft(),
                desired.rotateRight().rotateRight(),
                desired.rotateLeft().rotateLeft().rotateLeft(),
                desired.rotateRight().rotateRight().rotateRight(),
                desired.opposite()
        };

        Direction best = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < order.length; i++) {
            Direction dir = order[i];
            if (!G.rc.canMove(dir)) continue;

            MapLocation next = G.me.add(dir);
            int score = 0;

            if (G.rc.canSenseLocation(next)) {
                MapInfo info = G.rc.senseMapInfo(next);
                if (G.isFriendlyPaint(info)) score += 4;
                if (G.isEnemyPaint(info)) score -= 6;
            }

            if (lastDir != Direction.CENTER && dir == lastDir.opposite()) score -= 8;
            if (away) score += enemyThreatAdjustment(next);

            if (score > bestScore) {
                bestScore = score;
                best = dir;
            }
        }

        if (best != Direction.CENTER) {
            return move(best);
        }
        return false;
    }

    // =========================================================
    // RETREAT HELPERS
    // =========================================================
    public static MapLocation closestAllyTower() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null) continue;
            if (!ally.type.isTowerType()) continue;

            int d = G.me.distanceSquaredTo(ally.location);
            if (d < bestDist && d <= RETREAT_TOWER_SEARCH_DIST2) {
                bestDist = d;
                best = ally.location;
            }
        }

        return best;
    }

    public static Direction bestPaintSeekingDir() throws GameActionException {
        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < G.DIRECTIONS.length; i++) {
            Direction dir = G.DIRECTIONS[i];
            if (!G.rc.canMove(dir)) continue;

            MapLocation next = G.me.add(dir);
            int score = 0;

            if (G.rc.canSenseLocation(next)) {
                MapInfo info = G.rc.senseMapInfo(next);

                if (G.isFriendlyPaint(info)) score += 18;
                else if (G.isNeutralPaint(info)) score += 4;
                else if (G.isEnemyPaint(info)) score -= 12;
            }

            if (G.recentlyVisited(next, G.STALE_VISITED_ROUNDS)) {
                score -= 5;
            }

            score += enemyThreatAdjustment(next);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        return bestDir;
    }

    // =========================================================
    // SMALL UTILITY HELPERS FOR OTHER FILES
    // =========================================================
    public static boolean adjacentTo(MapLocation a, MapLocation b) {
        return a != null && b != null && a.distanceSquaredTo(b) <= 2;
    }

    public static boolean reached(MapLocation target, int dist2) {
        return target != null && G.me.distanceSquaredTo(target) <= dist2;
    }

    public static int manhattan(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int chebyshev(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    /**
     * Reset kecil kalau nanti dipakai saat role switching / re-init.
     */
    public static void resetExplore() {
        exploreTarget = null;
        exploreTargetRound = -1000000;
    }
}