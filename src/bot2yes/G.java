package bot2yes;

import battlecode.common.*;

/**
 * Global state shared across all robot logic classes.
 * Updated each round via RobotPlayer.
 */
public class G {
    // ─── Immutable per-robot ────────────────────────────────────────────────────
    public static RobotController rc;
    public static int mapWidth, mapHeight, mapArea;
    public static MapLocation mapCenter;
    public static Team team, opponentTeam;
    public static int roundSpawned;

    // ─── Direction helpers ──────────────────────────────────────────────────────
    public static final Direction[] DIRECTIONS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };
    public static final Direction[] ALL_DIRS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
        Direction.CENTER
    };

    // ─── Per-round state ─────────────────────────────────────────────────────────
    public static MapLocation me;
    public static int round;
    public static RobotInfo[] nearbyAllies;
    public static RobotInfo[] nearbyEnemies;
    public static MapInfo[]   nearbyMapInfos;
    public static MapLocation[] nearbyRuins;

    // ─── Cached map knowledge ─────────────────────────────────────────────────
    // visited[y/2][x/2] = last round we were near this cell
    public static int[][] visited = new int[30][30];
    public static MapLocation lastKnownEnemyTower = null;
    public static int lastEnemyTowerRound = -9999;

    // paint from last turn (to compute loss)
    public static int lastPaint = 200;

    // ─── Indicator ──────────────────────────────────────────────────────────────
    public static StringBuilder indicator = new StringBuilder();

    // ─── Convenience ─────────────────────────────────────────────────────────────
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

    // Chebyshev distance
    public static int cheby(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }
}
