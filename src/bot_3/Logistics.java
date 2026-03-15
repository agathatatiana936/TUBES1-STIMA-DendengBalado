package bot_3;

import battlecode.common.*;

public class Logistics {

    public static boolean needRefillSoldier() {
        if (G.paint <= 35) return true;
        if (G.paint <= 60 && farFromSupply()) return true;
        if (G.paint <= 80 && G.lastSeenAllyTower != null
                && G.me.distanceSquaredTo(G.lastSeenAllyTower) <= 2) return true;
        return false;
    }

    public static boolean needRefillMopper() {
        return G.paint <= 20 || (G.paint <= 35 && farFromSupply());
    }

    public static boolean needRefillSplasher() {
        return G.paint <= 80 || (G.paint <= 120 && farFromSupply());
    }

    static boolean farFromSupply() {
        return G.lastSeenAllyTower != null && G.me.distanceSquaredTo(G.lastSeenAllyTower) > 30;
    }

    public static void goRefill() throws GameActionException {
        if (tryWithdrawAdjacent()) return;
        if (G.lastSeenAllyTower != null) Motion.moveToward(G.lastSeenAllyTower, false);
        else Motion.safeFallbackMove(false);
    }

    public static boolean tryWithdrawAdjacent() throws GameActionException {
        if (!G.isRobotUnit) return false;
        int cap = G.myType.paintCapacity;
        if (G.paint >= cap * 9 / 10) return false;
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (!Util.isTower(ally.type)) continue;
            if (G.me.distanceSquaredTo(ally.location) > 2) continue;
            int want = cap - G.paint;
            if (G.rc.canTransferPaint(ally.location, -want)) {
                G.rc.transferPaint(ally.location, -want);
                G.paint = G.rc.getPaint();
                return true;
            }
        }
        return false;
    }

    public static boolean tryTransferPaintToAlly() throws GameActionException {
        if (G.myType != UnitType.MOPPER) return false;
        if (G.paint <= 30) return false;

        RobotInfo bestAlly = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (ally.location.equals(G.me)) continue;
            UnitType at = ally.type;
            if (at != UnitType.SOLDIER && at != UnitType.SPLASHER && at != UnitType.MOPPER) continue;
            int pct = ally.paintAmount * 100 / at.paintCapacity;
            if (pct > 55) continue;
            int score = (pct <= 10 ? 80 : pct <= 25 ? 50 : pct <= 40 ? 25 : 10)
                      + (at == UnitType.SOLDIER ? 15 : at == UnitType.SPLASHER ? 10 : 0)
                      - G.me.distanceSquaredTo(ally.location) * 2;
            if (score > bestScore) { bestScore = score; bestAlly = ally; }
        }
        if (bestAlly == null) return false;

        int amount = Math.min(Math.min(G.paint - 30, bestAlly.type.paintCapacity - bestAlly.paintAmount), 70);
        if (amount <= 5) return false;

        if (G.me.distanceSquaredTo(bestAlly.location) <= 2 && G.rc.canTransferPaint(bestAlly.location, amount)) {
            G.rc.transferPaint(bestAlly.location, amount);
            return true;
        }
        Direction dir = G.me.directionTo(bestAlly.location);
        if (dir != Direction.CENTER && G.rc.canMove(dir)) { Motion.executeMove(dir); return true; }
        return false;
    }
}