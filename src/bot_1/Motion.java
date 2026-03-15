package bot_1;

import battlecode.common.*;

public class Motion {
    static MapLocation lastCornerTarget = null;
    static MapLocation[] queueLastLocations = new MapLocation[100];

    static MapLocation bugTarget = null;
    static boolean bugTracing = false;
    static int bugSide = 1;      
    static Direction bugTraceDir = Direction.NORTH;
    static int bugStartDist = Integer.MAX_VALUE; 
    static int bugTraceSteps = 0;

    public static Direction moveTowardLocation(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return null;

        MapLocation myLoc = rc.getLocation();
        if (myLoc.equals(target)) return null;

        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            bugTracing = false;
            bugSide = (rc.getID() % 2 == 0) ? 1 : -1;
            bugTraceDir = Direction.NORTH;
            bugStartDist = Integer.MAX_VALUE;
            bugTraceSteps = 0;
        }

        Direction direct = myLoc.directionTo(target);
        int curDist = myLoc.distanceSquaredTo(target);

        if (bugTracing && direct != Direction.CENTER && rc.canMove(direct) && curDist < bugStartDist) {
            bugTracing = false;
            bugTraceSteps = 0;
        }

        if (!bugTracing) {
            if (direct != Direction.CENTER && rc.canMove(direct)) {
                return direct;
            }

            Direction best = getBestDirection(rc, target);
            if (best != null) return best;

            bugTracing = true;
            bugStartDist = curDist;  
            bugTraceDir = (direct == Direction.CENTER) ? Direction.NORTH : direct;
            bugTraceSteps = 0;
        }

        if (bugTraceSteps > 50) {
            bugSide *= -1;
            bugTraceDir = (direct == Direction.CENTER) ? Direction.NORTH : direct;
            bugStartDist = curDist;
            bugTraceSteps = 0;
        }

        Direction follow = traceWall(rc);
        if (follow != null) return follow;

        if (direct != Direction.CENTER && rc.canMove(direct)) return direct;
        return null;
    }

    private static Direction getBestDirection(RobotController rc, MapLocation target) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        outer:
        for (Direction d : Direction.allDirections()) {
            if (d == Direction.CENTER) continue;
            if (!rc.canMove(d)) continue;

            MapLocation nextLoc = rc.getLocation().add(d);
            for (MapLocation loc : queueLastLocations) {
                if (loc != null && nextLoc.equals(loc)) continue outer;
            }

            int score = 0;

            score -= nextLoc.distanceSquaredTo(target) * 2;

            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                score += nextLoc.distanceSquaredTo(ally.getLocation());
            }

            for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
                score -= nextLoc.distanceSquaredTo(enemy.getLocation());
            }

            for (MapInfo tile : rc.senseNearbyMapInfos(nextLoc, 4)) {
                if (tile.isWall()) score -= 50;
            }

            // Preferensikan cat musuh / kosong (untuk direbut), hindari cat sendiri
            MapInfo info = rc.senseMapInfo(nextLoc);
            if (info.getPaint() == PaintType.ALLY_PRIMARY || info.getPaint() == PaintType.ALLY_SECONDARY) score -= 20;
            if (info.getPaint() == PaintType.ENEMY_PRIMARY || info.getPaint() == PaintType.ENEMY_SECONDARY
                    || info.getPaint() == PaintType.EMPTY) score += 20;

            if (score > bestScore) { bestScore = score; bestDir = d; }
        }
        return bestDir;
    }

    private static Direction traceWall(RobotController rc) {
        Direction dir = bugTraceDir;
        if (bugSide > 0) {
            dir = dir.rotateRight();
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(dir)) {
                    bugTraceDir = dir;
                    bugTraceSteps++;
                    return dir;
                }
                dir = dir.rotateLeft();
            }
        } else {
            dir = dir.rotateLeft();
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(dir)) {
                    bugTraceDir = dir;
                    bugTraceSteps++;
                    return dir;
                }
                dir = dir.rotateRight();
            }
        }
        return null;
    }

    public static Direction moveTowardEnemyPaint(RobotController rc) throws GameActionException {
        MapLocation bestTarget = null;
        int closestDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint() == PaintType.ENEMY_PRIMARY || tile.getPaint() == PaintType.ENEMY_SECONDARY) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (dist < closestDist) { closestDist = dist; bestTarget = tile.getMapLocation(); }
            }
        }
        if (bestTarget != null) return moveTowardLocation(rc, bestTarget);
        return moveTowardCorner(rc);
    }

    public static Direction moveTowardNearestEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation nearest = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            int dist = rc.getLocation().distanceSquaredTo(e.getLocation());
            if (dist < closestDist) { closestDist = dist; nearest = e.getLocation(); }
        }
        if (nearest != null) return moveTowardLocation(rc, nearest);
        return moveTowardCenter(rc);
    }

    public static Direction moveTowardNearestEnemyTower(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation nearest = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (isTowerType(e.getType())) {
                int dist = rc.getLocation().distanceSquaredTo(e.getLocation());
                if (dist < closestDist) { closestDist = dist; nearest = e.getLocation(); }
            }
        }
        if (nearest != null) return moveTowardLocation(rc, nearest);
        return moveTowardCorner(rc);
    }

    public static Direction moveTowardNearestAllySoldier(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearest = null;
        int closestDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.getType() == UnitType.SOLDIER) {
                int dist = rc.getLocation().distanceSquaredTo(a.getLocation());
                if (dist < closestDist) { closestDist = dist; nearest = a.getLocation(); }
            }
        }
        if (nearest != null) return moveTowardLocation(rc, nearest);
        return null;
    }

    public static Direction moveTowardCenter(RobotController rc) throws GameActionException {
        int mapH = rc.getMapHeight();
        int mapW = rc.getMapWidth();
        MapLocation center = new MapLocation(mapW / 2, mapH / 2);

        return moveTowardLocation(rc, center);
    } 

    public static Direction moveTowardCorner(RobotController rc) throws GameActionException {
        int mapH = rc.getMapHeight();
        int mapW = rc.getMapWidth();
        MapLocation[] corners = new MapLocation[] {
            new MapLocation(0, 0),
            new MapLocation(mapW - 1, 0),
            new MapLocation(0, mapH - 1),
            new MapLocation(mapW - 1, mapH - 1),
            new MapLocation(mapW / 2, 0),
            new MapLocation(mapW / 2, mapH - 1),
            new MapLocation(0, mapH / 2),
            new MapLocation(mapW - 1, mapH / 2)
        };

        MapLocation myLoc = rc.getLocation();
        MapLocation targetCorner = lastCornerTarget;

        if (targetCorner == null) {
            int farthestDist = -1;
            for (MapLocation c : corners) {
                int dist = myLoc.distanceSquaredTo(c);
                if (dist > farthestDist) {
                    farthestDist = dist;
                    targetCorner = c;
                }
            }
        }

        if (targetCorner != null && myLoc.distanceSquaredTo(targetCorner) <= 8) {
            int idx = 0;
            for (int i = 0; i < corners.length; i++) {
                if (corners[i].equals(targetCorner)) {
                    idx = i;
                    break;
                }
            }
            int step = (rc.getID() % 2 == 0) ? 1 : 3;
            targetCorner = corners[(idx + step) % corners.length];
        }

        lastCornerTarget = targetCorner;
        return moveTowardLocation(rc, targetCorner);
    }

    public static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_DEFENSE_TOWER || type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER || type == UnitType.LEVEL_THREE_MONEY_TOWER || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    public static void addtoQueue(MapLocation loc) {
        for (int i = queueLastLocations.length - 1; i > 0; i--) {
            queueLastLocations[i] = queueLastLocations[i - 1];
        }
        queueLastLocations[0] = loc;
    }
}
