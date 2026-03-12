package bot_1;

import battlecode.common.*;

public class Motion {
    static MapLocation lastCornerTarget = null;
    static MapLocation lastLoc = null;
    static int stuckTurns = 0;
    static MapLocation wallTarget = null;
    static int wallFollowSign = 1;
    static int wallFollowSteps = 0;
    static int wallStartDist = Integer.MAX_VALUE;

    public static Direction moveTowardLocation(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) {
            return null;
        }

        MapLocation myLoc = rc.getLocation();
        if (lastLoc != null && lastLoc.equals(myLoc)) {
            stuckTurns++;
        } else {
            stuckTurns = 0;
        }
        lastLoc = myLoc;

        Direction greedy = getBestDirection(rc, target);
        int curDist = myLoc.distanceSquaredTo(target);

        boolean forceWallFollow = stuckTurns >= 2;
        if (!forceWallFollow && greedy != null && rc.canMove(greedy)) {
            wallTarget = null;
            wallFollowSteps = 0;
            return greedy;
        }

        if (wallTarget == null || !wallTarget.equals(target)) {
            wallTarget = target;
            wallFollowSign = (rc.getID() % 2 == 0) ? 1 : -1;
            wallFollowSteps = 0;
            wallStartDist = curDist;
        }

        Direction follow = followWallDirection(rc, target, wallFollowSign);
        if (follow == null) {
            wallFollowSign *= -1;
            follow = followWallDirection(rc, target, wallFollowSign);
        }

        if (follow != null) {
            wallFollowSteps++;
            MapLocation next = myLoc.add(follow);
            int nextDist = next.distanceSquaredTo(target);
            if (nextDist < wallStartDist - 2 || wallFollowSteps > 10) {
                wallTarget = null;
                wallFollowSteps = 0;
            }
            return follow;
        }

        return null;
    }

    private static Direction getBestDirection(RobotController rc, MapLocation target) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction d : Direction.allDirections()) {
            if (!rc.canMove(d)) {
                continue;
            }

            MapLocation nextLoc = rc.getLocation().add(d);
            MapInfo info = rc.senseMapInfo(nextLoc);
            int score = 0;
            score -= nextLoc.distanceSquaredTo(target) * 10;

            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                score += nextLoc.distanceSquaredTo(ally.getLocation());
            }

            for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
                score -= nextLoc.distanceSquaredTo(enemy.getLocation());
            }

            if (info.getPaint() == PaintType.EMPTY) {
                score += 8;
            }
            if (info.getPaint() == PaintType.ENEMY_PRIMARY || info.getPaint() == PaintType.ENEMY_SECONDARY) {
                score += 20;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }
        return bestDir;
    }

    private static Direction followWallDirection(RobotController rc, MapLocation target, int sign) {
        Direction dir = rc.getLocation().directionTo(target);
        if (dir == null) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(dir)) {
                return dir;
            }
            dir = (sign > 0) ? dir.rotateRight() : dir.rotateLeft();
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
}
