package bot2yes;

import battlecode.common.*;

/**
 * Navigation helper.
 * Uses greedy move-toward-target with bug-nav obstacle avoidance.
 * Also provides: moveAwayFrom, spreadFromAllies, paintUnderSelf.
 */
public class Nav {

    private static Direction bugDir = null;
    private static int bugRot = 0;          // +1 CW, -1 CCW
    private static int bugSteps = 0;
    private static MapLocation bugTarget = null;
    private static int bugStartDist = 0;
    private static final int BUG_RESET = 20;

    /** Move toward target, painting current tile if possible. */
    public static void moveToward(MapLocation target) throws Exception {
        if (!G.rc.isMovementReady()) return;
        if (G.me.equals(target)) return;

        Direction ideal = G.me.directionTo(target);

        // Reset bug nav if target changed or we got closer
        if (bugTarget == null || !bugTarget.equals(target) || G.me.distanceSquaredTo(target) < bugStartDist) {
            bugDir = null;
            bugTarget = target;
            bugStartDist = G.me.distanceSquaredTo(target);
            bugSteps = 0;
        }

        if (bugDir == null) {
            // Try ideal direction first
            if (tryMove(ideal)) { bugDir = null; return; }
            // Try small rotations, preferring the smaller rotation (right then left at same offset)
            for (int offset = 1; offset <= 4; offset++) {
                Direction dr = ideal;
                for (int k = 0; k < offset; k++) dr = dr.rotateRight();
                if (tryMove(dr)) {
                    bugDir = dr; bugRot = 1; bugSteps = 0; return;
                }
                Direction dl = ideal;
                for (int k = 0; k < offset; k++) dl = dl.rotateLeft();
                if (tryMove(dl)) {
                    bugDir = dl; bugRot = -1; bugSteps = 0; return;
                }
            }
        } else {
            // Bug nav: follow the wall
            bugSteps++;
            if (bugSteps > BUG_RESET) {
                bugDir = null;
                bugSteps = 0;
                return;
            }
            // Try to steer back toward ideal first
            if (tryMove(ideal)) { bugDir = null; return; }
            // Continue around wall
            Direction attempt = (bugRot == 1) ? bugDir.rotateLeft() : bugDir.rotateRight();
            for (int i = 0; i < 8; i++) {
                if (tryMove(attempt)) {
                    bugDir = attempt;
                    return;
                }
                attempt = (bugRot == 1) ? attempt.rotateRight() : attempt.rotateLeft();
            }
        }
    }

    /** Move away from a location. */
    public static void moveAway(MapLocation avoid) throws Exception {
        if (!G.rc.isMovementReady()) return;
        Direction away = avoid.directionTo(G.me);
        if (tryMove(away)) return;
        if (tryMove(away.rotateLeft())) return;
        if (tryMove(away.rotateRight())) return;
    }

    /** Spread out from nearby allies (useful for soldiers). */
    public static void spreadFromAllies() throws Exception {
        if (!G.rc.isMovementReady()) return;
        // compute repulsion vector
        int dx = 0, dy = 0;
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo r = G.nearbyAllies[i];
            if (!r.type.isRobotType()) continue;
            int ddx = G.me.x - r.location.x;
            int ddy = G.me.y - r.location.y;
            dx += ddx; dy += ddy;
        }
        if (dx == 0 && dy == 0) return;
        // find closest direction to (dx,dy)
        double best = -999;
        Direction bestD = null;
        for (Direction d : G.DIRECTIONS) {
            double dot = d.getDeltaX() * dx + d.getDeltaY() * dy;
            if (dot > best && G.rc.canMove(d)) { best = dot; bestD = d; }
        }
        if (bestD != null) tryMove(bestD);
    }

    /** Try to move in a direction. Returns true on success. */
    private static boolean tryMove(Direction d) throws Exception {
        if (d == null || d == Direction.CENTER) return false;
        if (G.rc.canMove(d)) {
            G.rc.move(d);
            G.me = G.rc.getLocation();
            G.markVisited(G.me);
            return true;
        }
        return false;
    }

    /**
     * Move toward target while preferring unvisited / unpainted tiles.
     * This is the "greedy exploration" move - prefers tiles we haven't been to.
     */
    public static void greedyExploreTo(MapLocation target) throws Exception {
        if (!G.rc.isMovementReady()) return;
        Direction ideal = G.me.directionTo(target);

        // Score all 8 directions
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : G.DIRECTIONS) {
            if (!G.rc.canMove(d)) continue;
            MapLocation next = G.me.add(d);

            int score = 0;
            // Prefer moving toward target
            int oldDist = G.me.distanceSquaredTo(target);
            int newDist = next.distanceSquaredTo(target);
            score += (oldDist - newDist) * 10;

            // Prefer unvisited cells (greedy: maximize new territory)
            int lastV = G.getVisited(next);
            if (lastV == 0) score += 50;
            else score -= (G.round - lastV);

            // Prefer unpainted tiles nearby (we want to paint them)
            // We'll just use the direction score as proxy

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        if (bestDir != null) {
            tryMove(bestDir);
        }
    }

    /** Paint the current tile if it's empty or enemy. Returns whether paint was used. */
    public static boolean paintUnderSelf() throws Exception {
        if (!G.rc.isActionReady()) return false;
        MapLocation loc = G.me;
        if (!G.rc.canAttack(loc)) return false;
        MapInfo info = G.rc.senseMapInfo(loc);
        PaintType paint = info.getPaint();
        if (paint.isEnemy() || paint == PaintType.EMPTY) {
            G.rc.attack(loc);
            return true;
        }
        return false;
    }

    /** Choose random unexplored direction for roaming. */
    public static MapLocation pickExploreTarget() throws Exception {
        // Try to find an unvisited location far from us
        MapLocation best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = Rand.nextInt(G.mapWidth);
            int y = Rand.nextInt(G.mapHeight);
            MapLocation candidate = new MapLocation(x, y);
            int score = 0;
            // prefer unvisited
            int v = G.getVisited(candidate);
            if (v == 0) score += 100;
            else score += Math.max(0, G.round - v - 50);
            // prefer far from current position
            score += G.me.distanceSquaredTo(candidate) / 10;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best != null ? best : new MapLocation(Rand.nextInt(G.mapWidth), Rand.nextInt(G.mapHeight));
    }
}
