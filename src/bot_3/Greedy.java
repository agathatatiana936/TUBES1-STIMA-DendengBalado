package bot_3;

import battlecode.common.*;

public class Greedy {

    public static boolean tryAttackEnemyTower() throws GameActionException {
        RobotInfo best = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyEnemyLen; --i >= 0;) {
            RobotInfo e = G.nearbyEnemies[i];
            if (!Util.isTower(e.type) || !G.rc.canAttack(e.location)) continue;
            int score = 120 + (e.health <= 100 ? 60 : 0) - G.me.distanceSquaredTo(e.location);
            if (score > bestScore) { bestScore = score; best = e; }
        }
        if (best != null) { G.rc.attack(best.location); G.paint -= 5; return true; }
        return false;
    }

    public static boolean tryAttackEnemyRobotEfficient() throws GameActionException {
        for (int i = G.nearbyEnemyLen; --i >= 0;) {
            RobotInfo e = G.nearbyEnemies[i];
            if (Util.isTower(e.type)) continue;
            if (!G.rc.canAttack(e.location)) continue;
            MapInfo tileInfo = findCachedTileOrSense(e.location);
            if (tileInfo == null) continue;
            PaintType tp = tileInfo.getPaint();
            if (tp.isAlly()) continue;
            G.rc.attack(e.location, false);
            G.paint -= 5;
            return true;
        }
        return false;
    }

    public static boolean tryBestPaintAttack() throws GameActionException {
        MapLocation bestTarget = null; boolean bestSec = false; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapInfo tile = G.nearbyTiles[i];
            if (!G.rc.canAttack(tile.getMapLocation())) continue;
            int s1 = scorePaintTarget(tile, false);
            if (s1 > bestScore) { bestScore = s1; bestTarget = tile.getMapLocation(); bestSec = false; }
            int s2 = scorePaintTarget(tile, true);
            if (s2 > bestScore) { bestScore = s2; bestTarget = tile.getMapLocation(); bestSec = true; }
        }
        if (bestTarget != null && bestScore > 5) {
            G.rc.attack(bestTarget, bestSec);
            G.paint -= 5;
            return true;
        }
        return false;
    }

    public static boolean tryCleanEnemyPaint() throws GameActionException {
        MapLocation bestTarget = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapInfo tile = G.nearbyTiles[i];
            PaintType p  = tile.getPaint();
            if (p == PaintType.EMPTY || p.isAlly()) continue;
            MapLocation loc = tile.getMapLocation();
            if (!G.rc.canAttack(loc)) continue;
            int score = 30 - G.me.distanceSquaredTo(loc) + lightFrontierBonus(loc);
            for (int j = G.nearbyEnemyLen; --j >= 0;)
                if (G.nearbyEnemies[j].location.equals(loc)) { score += 25; break; }
            if (score > bestScore) { bestScore = score; bestTarget = loc; }
        }
        if (bestTarget != null) { G.rc.attack(bestTarget); return true; }

        Direction bestSwing = null; int bestSwingScore = 5;
        for (int di = 4; --di >= 0;) {
            Direction dir = G.DIRS[di * 2];
            if (!G.rc.canMopSwing(dir)) continue;
            int score = estimateSwingScore(dir);
            if (score > bestSwingScore) { bestSwingScore = score; bestSwing = dir; }
        }
        if (bestSwing != null) { G.rc.mopSwing(bestSwing); return true; }
        return false;
    }

    public static boolean tryBestSplashAttack() throws GameActionException {
        if (G.paint < 55) return false;
        MapLocation bestTarget = null; int bestScore = Integer.MIN_VALUE;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation loc = G.nearbyTiles[i].getMapLocation();
            if (!G.rc.canAttack(loc)) continue;
            int score = splashValue(loc) - 35;
            if (score > bestScore) { bestScore = score; bestTarget = loc; }
        }
        if (bestTarget != null && bestScore > 0) {
            G.rc.attack(bestTarget); G.paint -= 50; return true;
        }
        return false;
    }

    public static boolean tryExpandMove() throws GameActionException {
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        for (int di = 8; --di >= 0;) {
            Direction dir = G.DIRS[di];
            if (!G.rc.canMove(dir)) continue;
            MapLocation nxt = G.me.add(dir);
            int score = scoreMove(nxt, false)
                      + frontierBonusLight(nxt) * 5
                      + localPaintOpportunity(nxt) * 2;
            if (G.pushTarget != null) {
                int d = G.me.distanceSquaredTo(G.pushTarget) - nxt.distanceSquaredTo(G.pushTarget);
                score += d > 0 ? 30 : d < 0 ? -25 : 0;
            }
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        if (bestDir != null && bestScore > 0) { Motion.executeMove(bestDir); return true; }
        return false;
    }

    public static boolean tryContestMove() throws GameActionException {
        Direction bestDir = null; int bestScore = Integer.MIN_VALUE;
        for (int di = 8; --di >= 0;) {
            Direction dir = G.DIRS[di];
            if (!G.rc.canMove(dir)) continue;
            MapLocation nxt = G.me.add(dir);
            int score = scoreMove(nxt, true);
            if (G.pushTarget != null) {
                int d = G.me.distanceSquaredTo(G.pushTarget) - nxt.distanceSquaredTo(G.pushTarget);
                score += d > 0 ? 26 : d < 0 ? -20 : 0;
            }
            if (score > bestScore) { bestScore = score; bestDir = dir; }
        }
        if (bestDir != null) { Motion.executeMove(bestDir); return true; }
        return false;
    }

    public static int scoreMove(MapLocation nxt, boolean aggressive) throws GameActionException {
        MapInfo nxtInfo = findCachedTileOrSense(nxt);
        PaintType p     = (nxtInfo != null) ? nxtInfo.getPaint() : PaintType.EMPTY;

        int paintPct = G.paintPct();
        int score;
        if (paintPct <= 15) {
            score = p.isAlly() ? 30 : (p == PaintType.EMPTY ? 5 : -40);
        } else if (paintPct <= 40) {
            score = p.isAlly() ? 15 : (p == PaintType.EMPTY ? 18 : -20);
        } else {
            score = p.isAlly() ? -8 : (p == PaintType.EMPTY ? 20 : (aggressive ? 12 : 6));
        }

        int frontier = 0, adjacent = 0;
        int drain = p.isAlly() ? 0 : (p == PaintType.EMPTY ? 1 : 2);

        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation tl = G.nearbyTiles[i].getMapLocation();
            int dist = nxt.distanceSquaredTo(tl);
            if (dist > 0 && dist <= 2) {
                PaintType tp = G.nearbyTiles[i].getPaint();
                if      (tp == PaintType.EMPTY) frontier += 2;
                else if (!tp.isAlly())          frontier += 3;
            }
        }
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo a = G.nearbyAllies[i];
            if (!a.location.equals(G.me) && nxt.distanceSquaredTo(a.location) <= 2) adjacent++;
        }

        score += frontier * 3;
        score -= adjacent * 6;

        int futurePaint = G.paint - drain - adjacent;
        if      (futurePaint <= 5)  score -= 90;
        else if (futurePaint <= 12) score -= 40;
        else if (futurePaint <= 20) score -= 15;

        int revisit = G.timesVisited(nxt);
        score -= revisit * 55;
        if (G.prevLoc     != null && nxt.equals(G.prevLoc))     score -= 100;
        if (G.prevPrevLoc != null && nxt.equals(G.prevPrevLoc)) score -= 35;

        Direction candDir = G.me.directionTo(nxt);
        if (G.lastMoveDir != Direction.CENTER) {
            if      (candDir == G.lastMoveDir.opposite()) score -= 60;
            else if (candDir == G.lastMoveDir)            score += 10;
        }

        if (G.lastSeenAllyTower != null && paintPct <= 40)
            score -= nxt.distanceSquaredTo(G.lastSeenAllyTower) / 3;

        for (int i = G.nearbyEnemyLen; --i >= 0;) {
            int d = nxt.distanceSquaredTo(G.nearbyEnemies[i].location);
            if      (d <= 4) score -= 6;
            else if (d <= 9) score -= 2;
        }

        return score;
    }

    static int scorePaintTarget(MapInfo tile, boolean useSec) {
        PaintType   p   = tile.getPaint();
        MapLocation loc = tile.getMapLocation();
        if (p.isAlly()) return -999;

        int score = (p == PaintType.EMPTY)
            ? (22 + (G.round <= 140 ? 15 : 0))
            : 30;

        int allyN = 0, enemyN = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation al = G.nearbyTiles[i].getMapLocation();
            int d = loc.distanceSquaredTo(al);
            if (d == 0 || d > 2) continue;
            PaintType ap = G.nearbyTiles[i].getPaint();
            if      (ap.isAlly())           allyN++;
            else if (ap != PaintType.EMPTY) enemyN++;
        }
        if (allyN >= 2) score += 8;
        if (allyN >= 4) score += 12;
        if (allyN >= 6) score += 18;
        score -= enemyN * 3;

        score += lightFrontierBonus(loc) * 2;

        for (int i = G.nearbyTileLen; --i >= 0;) {
            if (G.nearbyTiles[i].hasRuin()
                    && loc.distanceSquaredTo(G.nearbyTiles[i].getMapLocation()) <= 8) {
                score += 15; break;
            }
        }

        if (G.inferredEnemyBase != null) {
            int dToEnemy = loc.distanceSquaredTo(G.inferredEnemyBase);
            int dToMe    = G.me.distanceSquaredTo(G.inferredEnemyBase);
            if (dToEnemy < dToMe) score += 6;
        }

        int after = G.paint - 5;
        if      (after <= 0)  return -999;
        else if (after <= 10) score -= 80;
        else if (after <= 20) score -= 30;

        score -= G.me.distanceSquaredTo(loc) / 2;
        return score;
    }

    // ── Lightweight helpers ───────────────────────────────────────

    public static int lightFrontierBonus(MapLocation loc) {
        int bonus = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation adj = G.nearbyTiles[i].getMapLocation();
            if (loc.distanceSquaredTo(adj) != 1) continue;
            PaintType p = G.nearbyTiles[i].getPaint();
            if      (p == PaintType.EMPTY) bonus += 3;
            else if (!p.isAlly())          bonus += 5;
        }
        return bonus;
    }

    static int frontierBonusLight(MapLocation loc) {
        int bonus = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation adj = G.nearbyTiles[i].getMapLocation();
            int d = loc.distanceSquaredTo(adj);
            if (d != 1 && d != 2) continue;
            PaintType p = G.nearbyTiles[i].getPaint();
            if      (p == PaintType.EMPTY) bonus += 3;
            else if (!p.isAlly())          bonus += 5;
        }
        return bonus;
    }

    static int localPaintOpportunity(MapLocation center) {
        int v = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation adj = G.nearbyTiles[i].getMapLocation();
            int d = center.distanceSquaredTo(adj);
            if (d != 1 && d != 2) continue;
            PaintType p = G.nearbyTiles[i].getPaint();
            if      (p == PaintType.EMPTY) v += 2;
            else if (!p.isAlly())          v += 3;
        }
        return v;
    }

    public static int countAdjacentAllies(MapLocation loc) {
        int cnt = 0;
        for (int i = G.nearbyAllyLen; --i >= 0;) {
            RobotInfo a = G.nearbyAllies[i];
            if (!a.location.equals(G.me) && loc.distanceSquaredTo(a.location) <= 2) cnt++;
        }
        return cnt;
    }

    static int estimateSwingScore(Direction dir) {
        MapLocation step1 = G.me.add(dir);
        MapLocation step2 = step1.add(dir);
        int score = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation loc = G.nearbyTiles[i].getMapLocation();
            if (step1.distanceSquaredTo(loc) <= 2 || step2.distanceSquaredTo(loc) <= 2) {
                PaintType p = G.nearbyTiles[i].getPaint();
                if (p != PaintType.EMPTY && !p.isAlly()) score += 10;
            }
        }
        for (int i = G.nearbyEnemyLen; --i >= 0;) {
            int d1 = G.nearbyEnemies[i].location.distanceSquaredTo(step1);
            int d2 = G.nearbyEnemies[i].location.distanceSquaredTo(step2);
            if (d1 <= 2 || d2 <= 2) score += 15;
        }
        if (G.paint <= 20) score -= 15;
        return score;
    }

    static int splashValue(MapLocation center) {
        int score = 0;
        for (int i = G.nearbyTileLen; --i >= 0;) {
            MapLocation loc = G.nearbyTiles[i].getMapLocation();
            if (center.distanceSquaredTo(loc) > 4) continue;
            PaintType p = G.nearbyTiles[i].getPaint();
            if      (p == PaintType.EMPTY) score += 4;
            else if (!p.isAlly())          score += 8;
            else                           score -= 2;
        }
        return score;
    }

    public static MapInfo findCachedTileOrSense(MapLocation loc) throws GameActionException {
        for (int i = G.nearbyTileLen; --i >= 0;)
            if (G.nearbyTiles[i].getMapLocation().equals(loc)) return G.nearbyTiles[i];
        return G.rc.canSenseLocation(loc) ? G.rc.senseMapInfo(loc) : null;
    }
}