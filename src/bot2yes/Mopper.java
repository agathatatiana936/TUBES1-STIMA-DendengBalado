package bot2yes;

import battlecode.common.*;

/**
 * MOPPER - paint cleanup and support robot.
 *
 * Greedy Heuristic: "Enemy Paint Removal Priority"
 * At every step, choose the action that removes the most enemy paint tiles,
 * while supporting ally units with paint transfers when needed.
 * Also swings in direction that hits most enemy robots.
 */
public class Mopper {

    static final int EXPLORE = 0;
    static final int SUPPORT = 1;  // transfer paint to low-paint ally
    static final int RETREAT = 2;

    static int mode = EXPLORE;
    static MapLocation exploreTarget = null;
    static int exploreAge = 0;
    static final int EXPLORE_TIMEOUT = 20;

    public static void init() throws Exception {
        Rand.state = G.rc.getID() * 31415926 + 271;
    }

    public static void run() throws Exception {
        // Check if we should retreat
        int paint = G.rc.getPaint();
        if (paint < 20 && mode != RETREAT) mode = RETREAT;
        if (mode == RETREAT && paint >= 70) mode = EXPLORE;

        // Complete patterns
        tryCompletePatterns();

        switch (mode) {
            case EXPLORE -> doExplore();
            case SUPPORT -> doSupport();
            case RETREAT -> doRetreat();
        }

        G.indicator.append("MOP=" + modeStr() + " P=" + G.rc.getPaint() + " ");
    }

    static void doExplore() throws Exception {
        // Check if any nearby ally robot needs paint
        RobotInfo needyAlly = findNeedyAlly();
        if (needyAlly != null) {
            mode = SUPPORT;
            doSupport();
            return;
        }

        // Movement: go toward densest enemy paint region
        if (G.rc.isMovementReady()) {
            Direction bestDir = pickMopDirection();
            if (bestDir != null && G.rc.canMove(bestDir)) {
                G.rc.move(bestDir);
                G.me = G.rc.getLocation();
                G.markVisited(G.me);
            } else {
                // Navigate toward explore target
                if (exploreTarget == null || G.me.isWithinDistanceSquared(exploreTarget, 4) || exploreAge > EXPLORE_TIMEOUT) {
                    exploreTarget = Nav.pickExploreTarget();
                    exploreAge = 0;
                }
                exploreAge++;
                Nav.moveToward(exploreTarget);
            }
        }

        // Action: mop enemy paint (greedy: attack tile with most enemy paint)
        if (G.rc.isActionReady()) {
            doMopAction();
        }
    }

    /**
     * Greedy move: pick direction that moves us toward most enemy paint.
     */
    static Direction pickMopDirection() throws Exception {
        Direction bestDir = null;
        int bestScore = 0;

        for (Direction d : G.DIRECTIONS) {
            if (!G.rc.canMove(d)) continue;
            MapLocation next = G.me.add(d);
            int score = 0;

            // Count enemy paint around next position (radius √8 = 2 steps)
            for (MapInfo info : G.nearbyMapInfos) {
                MapLocation loc = info.getMapLocation();
                if (!next.isWithinDistanceSquared(loc, 8)) continue;
                if (info.getPaint().isEnemy()) score += 14;
            }
            // Bonus for enemy robots nearby
            for (int i = G.nearbyEnemies.length; --i >= 0;) {
                if (G.nearbyEnemies[i].type.isRobotType() && next.isWithinDistanceSquared(G.nearbyEnemies[i].location, 8))
                    score += 8;
            }
            // Prefer unvisited
            if (G.getVisited(next) == 0) score += 3;

            if (score > bestScore) { bestScore = score; bestDir = d; }
        }
        return bestDir;
    }

    /**
     * Greedy mop action:
     * 1. Try swing if multiple enemies in a line
     * 2. Otherwise mop (transfer) enemy tile or enemy robot
     */
    static void doMopAction() throws Exception {
        // Check for swing opportunity (hits 2+ enemies in a line)
        Direction bestSwing = findBestSwing();
        if (bestSwing != null) {
            if (G.rc.canAttack(G.me.add(bestSwing))) {
                // Swing direction encoded as attacking in a cardinal direction
                // Mopper swing: attack adjacent cell in cardinal direction
                // The engine interprets mopper attacking in cardinal direction as swing
                G.rc.attack(G.me.add(bestSwing));
                return;
            }
        }

        // Otherwise: find best single mop target
        MapLocation bestTarget = null;
        int bestScore = 0;

        for (MapInfo info : G.nearbyMapInfos) {
            MapLocation loc = info.getMapLocation();
            if (!G.me.isWithinDistanceSquared(loc, 2)) continue; // mop radius √2
            if (!G.rc.canAttack(loc)) continue;

            int score = 0;
            PaintType paint = info.getPaint();
            if (paint.isEnemy()) score += 20; // primary goal: remove enemy paint

            // Bonus if enemy robot is on this tile
            for (int i = G.nearbyEnemies.length; --i >= 0;) {
                if (G.nearbyEnemies[i].type.isRobotType() && G.nearbyEnemies[i].location.equals(loc)) {
                    score += 10; // steal paint from enemy robot
                    break;
                }
            }

            if (score > bestScore) { bestScore = score; bestTarget = loc; }
        }

        if (bestTarget != null) {
            G.rc.attack(bestTarget);
        }
    }

    /**
     * Find best swing direction: maximize enemy robots hit.
     * Swing hits 2 tiles in a cardinal direction.
     */
    static Direction findBestSwing() throws Exception {
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction bestDir = null;
        int bestHits = 1; // only swing if 2+ hits

        for (Direction d : cardinals) {
            MapLocation step1 = G.me.add(d);
            MapLocation step2 = step1.add(d);
            int hits = 0;
            for (int i = G.nearbyEnemies.length; --i >= 0;) {
                RobotInfo enemy = G.nearbyEnemies[i];
                if (!enemy.type.isRobotType()) continue;
                if (enemy.location.isWithinDistanceSquared(step1, 1) || enemy.location.isWithinDistanceSquared(step2, 1))
                    hits++;
            }
            if (hits > bestHits) { bestHits = hits; bestDir = d; }
        }
        return bestDir;
    }

    /** Find a nearby ally robot that has low paint. */
    static RobotInfo findNeedyAlly() {
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (!ally.type.isRobotType()) continue;
            if (!G.me.isWithinDistanceSquared(ally.location, 2)) continue;
            // Rough threshold: if ally has < 30% paint
            int paintCap = ally.type.paintCapacity;
            if (paintCap > 0 && ally.paintAmount < paintCap * 0.3) return ally;
        }
        return null;
    }

    static void doSupport() throws Exception {
        RobotInfo ally = findNeedyAlly();
        if (ally == null) { mode = EXPLORE; return; }

        if (G.rc.isMovementReady()) Nav.moveToward(ally.location);

        if (G.rc.isActionReady() && G.me.isWithinDistanceSquared(ally.location, 2)) {
            int give = Math.min(G.rc.getPaint() - 10, ally.type.paintCapacity - ally.paintAmount);
            if (give > 0 && G.rc.canTransferPaint(ally.location, give)) {
                G.rc.transferPaint(ally.location, give);
                mode = EXPLORE;
            }
        }
    }

    static void doRetreat() throws Exception {
        // Withdraw from nearby tower
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (ally.type.isRobotType()) continue;
            if (ally.type.getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER) continue;
            if (!G.me.isWithinDistanceSquared(ally.location, 2)) continue;
            int need = G.rc.getType().paintCapacity - G.rc.getPaint();
            if (need > 0 && G.rc.canTransferPaint(ally.location, -need)) {
                G.rc.transferPaint(ally.location, -need);
                return;
            }
        }
        // Move toward nearest paint tower
        MapLocation tower = findNearestPaintTowerLoc();
        if (tower != null && G.rc.isMovementReady()) Nav.moveToward(tower);
    }

    static void tryCompletePatterns() throws Exception {
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            MapLocation ruin = G.nearbyRuins[i];
            for (UnitType tt : Soldier.TOWER_TYPES) {
                if (G.rc.canCompleteTowerPattern(tt, ruin)) {
                    G.rc.completeTowerPattern(tt, ruin);
                    MapMemory.registerRuin(ruin, G.team, tt);
                    break;
                }
            }
        }
    }

    static MapLocation findNearestPaintTowerLoc() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = MapMemory.numRuins; --i >= 0;) {
            if (MapMemory.ruinTeams[i] == G.team && MapMemory.ruinTypes[i] != null) {
                if (MapMemory.ruinTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                    int d = G.me.distanceSquaredTo(MapMemory.ruinLocs[i]);
                    if (d < bestDist) { bestDist = d; best = MapMemory.ruinLocs[i]; }
                }
            }
        }
        return best;
    }

    static String modeStr() {
        switch (mode) {
            case EXPLORE: return "EXP";
            case SUPPORT: return "SUP";
            case RETREAT: return "RET";
            default: return "?";
        }
    }
}
