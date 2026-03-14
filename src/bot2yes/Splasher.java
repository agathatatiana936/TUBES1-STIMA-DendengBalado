package bot2yes;

import battlecode.common.*;

/**
 * SPLASHER - area paint coverage robot.
 *
 * Greedy Heuristic: "Maximum Paint-Per-Action"
 * Each turn, choose the attack center that maximizes unpainted tiles colored.
 * Splasher paints a 3x3 area, so we score each possible center by:
 *   - Number of empty/enemy tiles in radius 2
 *   - Bonus for being far from existing ally paint (frontier expansion)
 *   - Bonus for tiles near enemy paint (reclaiming territory)
 */
public class Splasher {

    static final int EXPLORE = 0;
    static final int RETREAT = 1;

    static int mode = EXPLORE;
    static MapLocation exploreTarget = null;
    static int exploreAge = 0;
    static final int EXPLORE_TIMEOUT = 25;

    public static void init() throws Exception {
        Rand.state = G.rc.getID() * 7654321 + 13;
    }

    public static void run() throws Exception {
        // Withdraw paint if needed
        tryWithdrawPaint();

        // Low paint → retreat
        int paint = G.rc.getPaint();
        int cap = G.rc.getType().paintCapacity;
        if (paint < 50 && mode != RETREAT) {
            mode = RETREAT;
        }
        if (mode == RETREAT && paint >= (int)(cap * 0.75)) {
            mode = EXPLORE;
        }

        // Complete patterns opportunistically
        tryCompletePatterns();

        switch (mode) {
            case EXPLORE -> doExplore();
            case RETREAT -> doRetreat();
        }

        G.indicator.append("SPL=" + (mode == EXPLORE ? "EXP" : "RET") + " P=" + G.rc.getPaint() + " ");
    }

    static void doExplore() throws Exception {
        // Movement: head toward dense unpainted regions
        if (G.rc.isMovementReady()) {
            MapLocation moveTarget = pickMoveTarget();
            if (moveTarget != null) Nav.moveToward(moveTarget);
        }

        // Action: find best splash target
        if (G.rc.isActionReady()) {
            bestSplash();
        }
    }

    /**
     * Greedy splash: score every attackable tile as a potential center.
     * Score = (empty tiles in radius √2) * 10 + (enemy tiles in radius √2) * 15
     * This maximizes new territory claimed per action.
     */
    static void bestSplash() throws Exception {
        MapLocation bestLoc = null;
        int bestScore = 0; // only attack if we gain something

        int actionRadius = G.rc.getType().actionRadiusSquared; // 4 for splasher

        for (MapInfo info : G.nearbyMapInfos) {
            MapLocation center = info.getMapLocation();
            if (!G.me.isWithinDistanceSquared(center, actionRadius)) continue;
            if (!G.rc.canAttack(center)) continue;

            int score = 0;
            // Count tiles in splash area (radius 2 = distSq <= 2 from center)
            for (MapInfo tile : G.nearbyMapInfos) {
                MapLocation tileLoc = tile.getMapLocation();
                if (!center.isWithinDistanceSquared(tileLoc, 2)) continue;
                PaintType paint = tile.getPaint();
                if (paint == PaintType.EMPTY) score += 10;
                else if (paint.isEnemy()) score += 15; // reclaim enemy territory
            }

            // Prefer attacking toward unexplored / frontier areas
            if (G.getVisited(center) == 0) score += 12;

            // Bonus for tiles in splash area that are unvisited (expand frontier)
            for (MapInfo tile : G.nearbyMapInfos) {
                MapLocation tileLoc = tile.getMapLocation();
                if (!center.isWithinDistanceSquared(tileLoc, 2)) continue;
                if (G.getVisited(tileLoc) == 0) score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                bestLoc = center;
            }
        }

        if (bestLoc != null) {
            G.rc.attack(bestLoc);
        }
    }

    /**
     * Move target: find area with most unpainted tiles.
     * Greedy: pick direction with most potential paint gain.
     */
    static MapLocation pickMoveTarget() throws Exception {
        // Refresh explore target if stale
        if (exploreTarget == null || G.me.isWithinDistanceSquared(exploreTarget, 4) || exploreAge > EXPLORE_TIMEOUT) {
            exploreTarget = pickGreedyTarget();
            exploreAge = 0;
        }
        exploreAge++;

        // Score all 8 possible next positions and pick best
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction d : G.DIRECTIONS) {
            if (!G.rc.canMove(d)) continue;
            MapLocation next = G.me.add(d);
            int score = 0;
            // Prefer toward explore target
            score += (G.me.distanceSquaredTo(exploreTarget) - next.distanceSquaredTo(exploreTarget)) * 5;
            // Prefer unvisited cells
            int v = G.getVisited(next);
            if (v == 0) score += 30;
            else score -= (G.round - v) / 5;
            // Avoid crowding allies
            for (int i = G.nearbyAllies.length; --i >= 0;) {
                if (G.nearbyAllies[i].type.isRobotType() && next.isWithinDistanceSquared(G.nearbyAllies[i].location, 2))
                    score -= 20;
            }
            if (score > bestScore) { bestScore = score; bestDir = d; }
        }
        return bestDir != null ? G.me.add(bestDir) : exploreTarget;
    }

    static MapLocation pickGreedyTarget() {
        MapLocation best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = Rand.nextInt(G.mapWidth);
            int y = Rand.nextInt(G.mapHeight);
            MapLocation c = new MapLocation(x, y);
            int v = G.getVisited(c);
            int dist = G.me.distanceSquaredTo(c);
            int score = (v == 0 ? 100 : Math.max(0, G.round - v - 20)) + dist / 10;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best != null ? best : new MapLocation(Rand.nextInt(G.mapWidth), Rand.nextInt(G.mapHeight));
    }

    static void doRetreat() throws Exception {
        MapLocation tower = findNearestPaintTowerLoc();
        if (tower == null) { mode = EXPLORE; return; }
        if (G.rc.isMovementReady()) Nav.moveToward(tower);
        if (G.me.isWithinDistanceSquared(tower, 2)) {
            int need = G.rc.getType().paintCapacity - G.rc.getPaint();
            if (need > 0 && G.rc.canTransferPaint(tower, -need)) {
                G.rc.transferPaint(tower, -need);
            }
        }
    }

    static void tryWithdrawPaint() throws Exception {
        if (G.rc.getPaint() >= (int)(G.rc.getType().paintCapacity * 0.9)) return;
        if (!G.rc.isActionReady()) return;
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (ally.type.isRobotType()) continue;
            if (ally.type.getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER) continue;
            if (!G.me.isWithinDistanceSquared(ally.location, 2)) continue;
            int need = G.rc.getType().paintCapacity - G.rc.getPaint();
            if (G.rc.canTransferPaint(ally.location, -need)) {
                G.rc.transferPaint(ally.location, -need);
                return;
            }
        }
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
        // Resource patterns
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = G.me.translate(dx, dy);
                if (G.rc.canCompleteResourcePattern(loc)) G.rc.completeResourcePattern(loc);
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
}
