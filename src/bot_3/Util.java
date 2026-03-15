package bot_3;

import battlecode.common.*;

public class Util {

    public static boolean isTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER
            || type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER
            || type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    public static MapLocation inferEnemyBase(MapLocation allyBase) {
        int w = G.rc.getMapWidth();
        int h = G.rc.getMapHeight();
        return new MapLocation(w - 1 - allyBase.x, h - 1 - allyBase.y);
    }

    public static MapLocation randomMapLocation() {
        return new MapLocation(G.rng.nextInt(G.rc.getMapWidth()), G.rng.nextInt(G.rc.getMapHeight()));
    }

    public static MapLocation pickFrontierLikeTarget() {
        int w = G.rc.getMapWidth(), h = G.rc.getMapHeight();
        MapLocation best = null; int bestScore = Integer.MIN_VALUE;
        for (int i = 10; --i >= 0;) {
            MapLocation cand  = new MapLocation(G.rng.nextInt(w), G.rng.nextInt(h));
            int         score = 0;
            if (G.lastSeenEnemyTower != null) score -= cand.distanceSquaredTo(G.lastSeenEnemyTower) / 3;
            else                              score += cand.distanceSquaredTo(G.me) / 4;
            if (G.lastSeenAllyTower  != null) score += cand.distanceSquaredTo(G.lastSeenAllyTower) / 8;
            if (score > bestScore) { bestScore = score; best = cand; }
        }
        return best != null ? best : randomMapLocation();
    }
}