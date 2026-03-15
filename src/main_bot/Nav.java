package main_bot;

import battlecode.common.*;

public class Nav {

    private static Direction bugDir = null;
    private static int bugRot = 0;          // +1 CW, -1 CCW
    private static int bugSteps = 0;
    private static MapLocation bugTarget = null;
    private static int bugStartDist = 0;
    private static final int BUG_RESET = 20;

    public static void moveToward(MapLocation target) throws Exception {
        if (!G.rc.isMovementReady()) return;
        if (G.me.equals(target)) return;

        Direction ideal = G.me.directionTo(target);

        if (bugTarget == null || !bugTarget.equals(target) || G.me.distanceSquaredTo(target) < bugStartDist) {
            bugDir = null;
            bugTarget = target;
            bugStartDist = G.me.distanceSquaredTo(target);
            bugSteps = 0;
        }

        if (bugDir == null) {
            if (tryMove(ideal)) { bugDir = null; return; }
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
            bugSteps++;
            if (bugSteps > BUG_RESET) {
                bugDir = null;
                bugSteps = 0;
                return;
            }
            if (tryMove(ideal)) { bugDir = null; return; }
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

    public static void moveAway(MapLocation avoid) throws Exception {
        if (!G.rc.isMovementReady()) return;
        Direction away = avoid.directionTo(G.me);
        if (tryMove(away)) return;
        if (tryMove(away.rotateLeft())) return;
        if (tryMove(away.rotateRight())) return;
    }

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

    public static MapLocation pickExploreTarget() throws Exception {
        MapLocation best = null;
        int bestScore = -1;
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = Rand.nextInt(G.mapWidth);
            int y = Rand.nextInt(G.mapHeight);
            MapLocation candidate = new MapLocation(x, y);
            int score = 0;
            int v = G.getVisited(candidate);
            if (v == 0) score += 100;
            else score += Math.max(0, G.round - v - 50);
            score += G.me.distanceSquaredTo(candidate) / 10;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best != null ? best : new MapLocation(Rand.nextInt(G.mapWidth), Rand.nextInt(G.mapHeight));
    }
}
