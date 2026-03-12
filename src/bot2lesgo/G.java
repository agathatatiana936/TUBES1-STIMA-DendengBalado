package bot2lesgo;

import battlecode.common.*;

public class G {

    // =========================
    // CORE ENGINE REFERENCES
    // =========================
    public static RobotController rc;

    // =========================
    // GLOBAL ROUND / MAP STATE
    // =========================
    public static int round;
    public static int roundSpawned;

    public static int mapWidth;
    public static int mapHeight;
    public static int mapArea;
    public static MapLocation mapCenter;

    public static Team team;
    public static Team opponentTeam;

    public static MapLocation me;

    // =========================
    // SENSE CACHE
    // =========================
    public static RobotInfo[] allyRobots = new RobotInfo[0];
    public static RobotInfo[] opponentRobots = new RobotInfo[0];
    public static MapInfo[] nearbyMapInfos = new MapInfo[0];
    public static MapLocation[] nearbyRuins = new MapLocation[0];

    // String cache kecil untuk micro / anti-crowding / debug ringan
    public static StringBuilder allyRobotsString = new StringBuilder();
    public static StringBuilder opponentRobotsString = new StringBuilder();

    // =========================
    // DEBUG / INDICATOR
    // =========================
    public static StringBuilder indicatorString = new StringBuilder();

    // =========================
    // ECONOMY / MACRO MEMORY
    // =========================
    public static int maxChips = 0;
    public static int lastChips = 0;
    public static int lastNumberTowers = 0;

    public static MapLocation lastDefenseTower = null;
    public static int lastDefenseTowerRound = -1000000;

    // =========================
    // LAST VISITED / SIMPLE MAP MEMORY
    // =========================
    // Dipakai untuk anti-loop movement dan scoring revisit penalty
    public static int[][] lastVisitedRound;

    // =========================
    // STATIC DIRECTION TABLES
    // =========================
    public static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    public static final Direction[] CARDINALS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    public static final Direction[] DIAGONALS = {
            Direction.NORTHEAST,
            Direction.SOUTHEAST,
            Direction.SOUTHWEST,
            Direction.NORTHWEST
    };

    public static final Direction[] ALL_DIRECTIONS_WITH_CENTER = {
            Direction.CENTER,
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    // =========================
    // STRATEGY CONSTANTS
    // Fokus: paint coverage tetap utama,
    // heuristik utama: bangun tower / ekonomi
    // =========================

    // ---- Early-game macro ----
    public static final int EARLY_GAME_ROUNDS = 80;
    public static final int MID_GAME_ROUNDS = 220;

    // ---- Tower economy preference ----
    public static final int DESIRED_EARLY_MONEY_TOWERS = 2;
    public static final int DESIRED_EARLY_PAINT_TOWERS = 1;

    public static final int MIN_SAFE_PAINT_FOR_SOLDIER = 35;
    public static final int MIN_SAFE_PAINT_FOR_SPLASHER = 45;
    public static final int MIN_SAFE_PAINT_FOR_MOPPER = 30;

    public static final int LOW_PAINT_RETREAT_THRESHOLD = 20;
    public static final int CRITICAL_PAINT_RETREAT_THRESHOLD = 10;

    // ---- Spawn scoring weights ----
    public static final int SOLDIER_BASE_SPAWN_SCORE = 100;
    public static final int SPLASHER_BASE_SPAWN_SCORE = 80;
    public static final int MOPPER_BASE_SPAWN_SCORE = 60;

    public static final int SOLDIER_EARLY_BONUS = 30;
    public static final int SPLASHER_MID_BONUS = 20;
    public static final int MOPPER_DEFENSE_BONUS = 25;

    // ---- Soldier role weights ----
    public static final int SCORE_BUILD_TOWER = 400;
    public static final int SCORE_BUILD_RESOURCE_PATTERN = 250;
    public static final int SCORE_EXPAND_PAINT = 120;
    public static final int SCORE_ATTACK_WINDOW = 100;

    // ---- Movement / navigation weights ----
    public static final int SCORE_UNVISITED_TILE = 12;
    public static final int SCORE_FRIENDLY_PAINT_TILE = 6;
    public static final int SCORE_ENEMY_PAINT_PENALTY = -10;
    public static final int SCORE_NEAR_ENEMY_TOWER_PENALTY = -30;
    public static final int SCORE_NEAR_RUIN_BONUS = 18;
    public static final int SCORE_APPROACH_TARGET = 20;
    public static final int SCORE_CROWDING_PENALTY = -8;

    // ---- Communication / memory tuning ----
    public static final int STALE_DEFENSE_TOWER_INFO_ROUNDS = 35;
    public static final int STALE_VISITED_ROUNDS = 20;

    // =========================
    // INITIALIZATION
    // =========================
    public static void initMapMemory() {
        if (lastVisitedRound == null) {
            lastVisitedRound = new int[mapWidth][mapHeight];
            for (int x = 0; x < mapWidth; x++) {
                for (int y = 0; y < mapHeight; y++) {
                    lastVisitedRound[x][y] = -1000000;
                }
            }
        }
    }

    // =========================
    // MAP / LOCATION HELPERS
    // =========================
    public static boolean onMap(MapLocation loc) {
        return loc != null
                && loc.x >= 0 && loc.x < mapWidth
                && loc.y >= 0 && loc.y < mapHeight;
    }

    public static int clampX(int x) {
        if (x < 0) return 0;
        if (x >= mapWidth) return mapWidth - 1;
        return x;
    }

    public static int clampY(int y) {
        if (y < 0) return 0;
        if (y >= mapHeight) return mapHeight - 1;
        return y;
    }

    public static MapLocation clampToMap(MapLocation loc) {
        if (loc == null) return null;
        return new MapLocation(clampX(loc.x), clampY(loc.y));
    }

    public static int locToIndex(MapLocation loc) {
        return loc.x + loc.y * mapWidth;
    }

    public static MapLocation indexToLoc(int idx) {
        return new MapLocation(idx % mapWidth, idx / mapWidth);
    }

    public static int manhattan(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int chebyshev(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public static int dist2(MapLocation a, MapLocation b) {
        return a.distanceSquaredTo(b);
    }

    public static boolean isNear(MapLocation a, MapLocation b, int dist2) {
        return a != null && b != null && a.distanceSquaredTo(b) <= dist2;
    }

    // =========================
    // LAST VISITED HELPERS
    // =========================
    public static void setLastVisited(MapLocation loc, int roundNum) {
        if (loc == null) return;
        if (lastVisitedRound == null) initMapMemory();
        if (!onMap(loc)) return;
        lastVisitedRound[loc.x][loc.y] = roundNum;
    }

    public static int getLastVisited(MapLocation loc) {
        if (loc == null) return -1000000;
        if (lastVisitedRound == null) initMapMemory();
        if (!onMap(loc)) return -1000000;
        return lastVisitedRound[loc.x][loc.y];
    }

    public static boolean recentlyVisited(MapLocation loc, int rounds) {
        return round - getLastVisited(loc) <= rounds;
    }

    // =========================
    // PAINT / TILE HELPERS
    // =========================
    public static boolean isFriendlyPaint(PaintType paint) {
        return paint == PaintType.ALLY_PRIMARY || paint == PaintType.ALLY_SECONDARY;
    }

    public static boolean isEnemyPaint(PaintType paint) {
        return paint == PaintType.ENEMY_PRIMARY || paint == PaintType.ENEMY_SECONDARY;
    }

    public static boolean isNeutralPaint(PaintType paint) {
        return paint == PaintType.EMPTY;
    }

    public static boolean isFriendlyPaint(MapInfo info) {
        return info != null && isFriendlyPaint(info.getPaint());
    }

    public static boolean isEnemyPaint(MapInfo info) {
        return info != null && isEnemyPaint(info.getPaint());
    }

    public static boolean isNeutralPaint(MapInfo info) {
        return info != null && isNeutralPaint(info.getPaint());
    }

    // =========================
    // UNIT TYPE HELPERS
    // =========================
    public static boolean isSoldier(UnitType type) {
        return type == UnitType.SOLDIER;
    }

    public static boolean isSplasher(UnitType type) {
        return type == UnitType.SPLASHER;
    }

    public static boolean isMopper(UnitType type) {
        return type == UnitType.MOPPER;
    }

    public static boolean isPaintTower(UnitType type) {
        UnitType base = type.getBaseType();
        return base == UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    public static boolean isMoneyTower(UnitType type) {
        UnitType base = type.getBaseType();
        return base == UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    public static boolean isDefenseTower(UnitType type) {
        UnitType base = type.getBaseType();
        return base == UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }

    public static boolean isAnyTower(UnitType type) {
        return type != null && type.isTowerType();
    }

    public static boolean isAnyRobot(UnitType type) {
        return type != null && type.isRobotType();
    }

    // =========================
    // GAME PHASE HELPERS
    // =========================
    public static boolean isEarlyGame() {
        return round <= EARLY_GAME_ROUNDS;
    }

    public static boolean isMidGame() {
        return round > EARLY_GAME_ROUNDS && round <= MID_GAME_ROUNDS;
    }

    public static boolean isLateGame() {
        return round > MID_GAME_ROUNDS;
    }

    // =========================
    // ECON / STATUS HELPERS
    // =========================
    public static int chipsDelta() {
        return rc.getChips() - lastChips;
    }

    public static int towerDelta() {
        return rc.getNumberTowers() - lastNumberTowers;
    }

    public static boolean hasFreshDefenseTowerInfo() {
        return lastDefenseTower != null
                && round - lastDefenseTowerRound <= STALE_DEFENSE_TOWER_INFO_ROUNDS;
    }

    public static boolean lowPaint() {
        return rc.getPaint() <= LOW_PAINT_RETREAT_THRESHOLD;
    }

    public static boolean criticalPaint() {
        return rc.getPaint() <= CRITICAL_PAINT_RETREAT_THRESHOLD;
    }

    public static int getSafePaintThreshold(UnitType type) {
        if (type == UnitType.SOLDIER) return MIN_SAFE_PAINT_FOR_SOLDIER;
        if (type == UnitType.SPLASHER) return MIN_SAFE_PAINT_FOR_SPLASHER;
        if (type == UnitType.MOPPER) return MIN_SAFE_PAINT_FOR_MOPPER;
        return 0;
    }

    public static boolean belowSafePaint(UnitType type) {
        return rc.getPaint() <= getSafePaintThreshold(type);
    }

    // =========================
    // TARGET / DIRECTION HELPERS
    // =========================
    public static Direction directionTo(MapLocation from, MapLocation to) {
        if (from == null || to == null) return Direction.CENTER;
        return from.directionTo(to);
    }

    public static Direction directionToMe(MapLocation target) {
        if (me == null || target == null) return Direction.CENTER;
        return me.directionTo(target);
    }

    public static MapLocation addDir(MapLocation loc, Direction dir) {
        if (loc == null || dir == null) return loc;
        return loc.add(dir);
    }

    public static MapLocation stepToward(MapLocation from, MapLocation to) {
        if (from == null || to == null) return from;
        return from.add(from.directionTo(to));
    }

    public static MapLocation reflectHorizontal(MapLocation loc) {
        return new MapLocation(mapWidth - 1 - loc.x, loc.y);
    }

    public static MapLocation reflectVertical(MapLocation loc) {
        return new MapLocation(loc.x, mapHeight - 1 - loc.y);
    }

    public static MapLocation reflectRotational(MapLocation loc) {
        return new MapLocation(mapWidth - 1 - loc.x, mapHeight - 1 - loc.y);
    }

    // =========================
    // ROBOT QUERIES
    // =========================
    public static RobotInfo closestRobot(RobotInfo[] robots) {
        if (robots == null || robots.length == 0 || me == null) return null;

        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = robots.length; --i >= 0;) {
            int d = me.distanceSquaredTo(robots[i].location);
            if (d < bestDist) {
                bestDist = d;
                best = robots[i];
            }
        }
        return best;
    }

    public static RobotInfo closestRobotOfType(RobotInfo[] robots, UnitType type) {
        if (robots == null || robots.length == 0 || me == null) return null;

        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = robots.length; --i >= 0;) {
            RobotInfo r = robots[i];
            if (r.type != type) continue;
            int d = me.distanceSquaredTo(r.location);
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    public static int countRobotsOfType(RobotInfo[] robots, UnitType type) {
        if (robots == null || robots.length == 0) return 0;
        int cnt = 0;
        for (int i = robots.length; --i >= 0;) {
            if (robots[i].type == type) cnt++;
        }
        return cnt;
    }

    public static int countTowerType(RobotInfo[] robots, UnitType baseType) {
        if (robots == null || robots.length == 0) return 0;
        int cnt = 0;
        for (int i = robots.length; --i >= 0;) {
            if (robots[i].type.isTowerType() && robots[i].type.getBaseType() == baseType) {
                cnt++;
            }
        }
        return cnt;
    }

    // =========================
    // LOCATION QUERIES
    // =========================
    public static MapLocation closestLocation(MapLocation[] locs) {
        if (locs == null || locs.length == 0 || me == null) return null;

        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = locs.length; --i >= 0;) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            int d = me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    public static MapLocation closestLocationTo(MapLocation origin, MapLocation[] locs) {
        if (origin == null || locs == null || locs.length == 0) return null;

        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = locs.length; --i >= 0;) {
            MapLocation loc = locs[i];
            if (loc == null) continue;
            int d = origin.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    // =========================
    // INDICATOR HELPERS
    // =========================
    public static void addIndicator(String s) {
        if (indicatorString == null) indicatorString = new StringBuilder();
        indicatorString.append(s);
    }

    public static void addIndicator(String label, int value) {
        if (indicatorString == null) indicatorString = new StringBuilder();
        indicatorString.append(label).append("=").append(value).append(" ");
    }

    public static void addIndicator(String label, MapLocation loc) {
        if (indicatorString == null) indicatorString = new StringBuilder();
        indicatorString.append(label).append("=").append(loc).append(" ");
    }

    // =========================
    // MISC SMALL HELPERS
    // =========================
    public static int abs(int x) {
        return x < 0 ? -x : x;
    }

    public static int sign(int x) {
        if (x > 0) return 1;
        if (x < 0) return -1;
        return 0;
    }

    public static int max(int a, int b) {
        return a > b ? a : b;
    }

    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    public static int clamp(int x, int lo, int hi) {
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }

    // =========================
    // OPTIONAL BOT-WIDE RESET
    // Aman dipanggil dari init unit bila nanti perlu.
    // =========================
    public static void init() {
        initMapMemory();
    }
}