package main;

import battlecode.common.*;

// Global State
public class G {
    public static RobotController rc;
    public static int mapWidth, mapHeight, mapArea;
    public static MapLocation mapCenter;
    public static Team team, opponentTeam;
    public static int roundSpawned;

    public static final Direction[] DIRECTIONS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static MapLocation me;
    public static int round;
    public static RobotInfo[] nearbyAllies;
    public static RobotInfo[] nearbyEnemies;
    public static MapInfo[]   nearbyMapInfos;
    public static MapLocation[] nearbyRuins;

    public static int[][] visited = new int[30][30];
    public static MapLocation lastKnownEnemyTower = null;
    public static int lastEnemyTowerRound = -9999;

    public static StringBuilder indicator = new StringBuilder();

    public static void markVisited(MapLocation loc) {
        int gy = loc.y / 2, gx = loc.x / 2;
        if (gy >= 0 && gy < 30 && gx >= 0 && gx < 30)
            visited[gy][gx] = round;
    }

    public static int getVisited(MapLocation loc) {
        int gy = loc.y / 2, gx = loc.x / 2;
        if (gy < 0 || gy >= 30 || gx < 0 || gx >= 30) return 0;
        return visited[gy][gx];
    }

    public static int cheby(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }
}
