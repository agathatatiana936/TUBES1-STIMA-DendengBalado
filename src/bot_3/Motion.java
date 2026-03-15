package bot_3;

import battlecode.common.*;

public class Motion {

    public static void executeMove(Direction dir) throws GameActionException {
        if (dir == null || dir == Direction.CENTER || !G.rc.canMove(dir)) return;
        G.rc.move(dir);
        G.lastMoveDir = dir;
        G.prevPrevLoc = G.prevLoc;
        G.prevLoc     = G.me;
        G.me          = G.rc.getLocation();
    }

    public static boolean moveToward(MapLocation target, boolean aggressive) throws GameActionException {
        if (target == null) return false;
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        int curDist = G.me.distanceSquaredTo(target);
        for (int di = 8; --di >= 0;) {
            Direction dir = G.DIRS[di];
            if (!G.rc.canMove(dir)) continue;
            MapLocation nxt     = G.me.add(dir);
            int         nxtDist = nxt.distanceSquaredTo(target);
            int         score   = Greedy.scoreMove(nxt, aggressive)
                                + (nxtDist < curDist ? (aggressive ? 42 : 32)
                                 : nxtDist > curDist ? -(aggressive ? 32 : 24) : 0);
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        if (bestDir != null) { executeMove(bestDir); return true; }
        return false;
    }

    public static boolean followWeakAlly() throws GameActionException {
        RobotInfo best = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (ally.location.equals(G.me)) continue;
            UnitType at = ally.type;
            if (at != UnitType.SOLDIER && at != UnitType.SPLASHER && at != UnitType.MOPPER) continue;
            int pct = ally.paintAmount * 100 / at.paintCapacity;
            if (pct > 40) continue;
            int score = (pct <= 10 ? 50 : pct <= 25 ? 30 : 15)
                      + (at == UnitType.SOLDIER ? 8 : 0)
                      - G.me.distanceSquaredTo(ally.location);
            if (score > bestScore) { bestScore = score; best = ally; }
        }
        return best != null && moveToward(best.location, false);
    }

    public static void safeFallbackMove(boolean aggressive) throws GameActionException {
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        for (int di = 8; --di >= 0;) {
            Direction dir = G.DIRS[di];
            if (!G.rc.canMove(dir)) continue;
            MapLocation nxt   = G.me.add(dir);
            int         score = Greedy.scoreMove(nxt, aggressive);
            if (G.pushTarget != null) {
                int d = G.me.distanceSquaredTo(G.pushTarget) - nxt.distanceSquaredTo(G.pushTarget);
                score += d > 0 ? 16 : d < 0 ? -12 : 0;
            }
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        if (bestDir != null) executeMove(bestDir);
    }
}