package bot2yes;

import battlecode.common.*;

/**
 * Persistent memory of towers, ruins, and map symmetry.
 * Greedy heuristic: build towers on ruins to maximize territory anchoring.
 */
public class MapMemory {

    // ─── Ruin / Tower tracking ─────────────────────────────────────────────────
    public static final int MAX_RUINS = 100;
    public static int numRuins = 0;
    public static MapLocation[] ruinLocs     = new MapLocation[MAX_RUINS];
    public static Team[]        ruinTeams    = new Team[MAX_RUINS];
    public static UnitType[]    ruinTypes    = new UnitType[MAX_RUINS];
    // -1 = ruin (no tower), 0 = neutral, 1 = ally, 2 = enemy
    public static int[]         ruinStatus   = new int[MAX_RUINS]; // 0=neutral ruin, 1=ally, 2=enemy
    // round when a ruin was claimed for building (to avoid duplicate claimers)
    public static int[]         ruinClaimRound = new int[MAX_RUINS];

    // number of ally paint towers known
    public static int allyPaintTowers = 0;
    public static int allyMoneyTowers = 0;
    public static int allyDefenseTowers = 0;
    public static int totalAllyTowers = 0;

    // ─── Symmetry ─────────────────────────────────────────────────────────────
    // symmetry[0] = horizontal reflection, [1] = vertical, [2] = rotational
    public static boolean[] symmetry = {true, true, true};

    // ─── Ruin lookup grid (divide coords by 5 to get index) ───────────────────
    // towerGrid[y/5][x/5] = index into ruinLocs, or -1
    public static int[][] towerGrid = new int[12][12];
    static {
        for (int i = 0; i < 12; i++)
            for (int j = 0; j < 12; j++)
                towerGrid[i][j] = -1;
        for (int k = 0; k < MAX_RUINS; k++) ruinClaimRound[k] = -9999;
    }

    /** Register a newly observed ruin or tower location. */
    public static void registerRuin(MapLocation loc, Team team, UnitType type) {
        int gx = loc.x / 5, gy = loc.y / 5;
        if (gx >= 12 || gy >= 12) return;
        int idx = towerGrid[gy][gx];
        if (idx >= 0) {
            // update existing
            ruinTeams[idx] = team;
            ruinTypes[idx] = type;
            ruinStatus[idx] = teamToStatus(team);
            if (team == G.team) recomputeAllyCount();
            return;
        }
        if (numRuins >= MAX_RUINS) return;
        idx = numRuins++;
        towerGrid[gy][gx] = idx;
        ruinLocs[idx]   = loc;
        ruinTeams[idx]  = team;
        ruinTypes[idx]  = type;
        ruinStatus[idx] = teamToStatus(team);
        // if a ruin becomes owned by us, clear claim time so others know it's done
        if (ruinStatus[idx] == 1) ruinClaimRound[idx] = -9999;
        recomputeAllyCount();
    }

    /**
     * Try to claim a neutral ruin for building work. Returns true if claim succeeded.
     * A claim expires after 80 rounds so failed attempts can be retried.
     */
    public static boolean tryClaimRuin(MapLocation loc) {
        if (loc == null) return false;
        for (int i = numRuins; --i >= 0;) {
            if (ruinLocs[i] != null && ruinLocs[i].equals(loc)) {
                if (ruinStatus[i] != 0) return false; // already claimed/built
                if (G.round - ruinClaimRound[i] > 80) {
                    ruinClaimRound[i] = G.round;
                    return true;
                }
                return false; // someone else claimed recently
            }
        }
        return false;
    }

    private static int teamToStatus(Team t) {
        if (t == null || t == Team.NEUTRAL) return 0;
        if (t == G.team) return 1;
        return 2;
    }

    public static void recomputeAllyCount() {
        allyPaintTowers = 0; allyMoneyTowers = 0; allyDefenseTowers = 0; totalAllyTowers = 0;
        for (int i = numRuins; --i >= 0;) {
            if (ruinTeams[i] == G.team) {
                totalAllyTowers++;
                if (ruinTypes[i] != null) {
                    UnitType base = ruinTypes[i].getBaseType();
                    if (base == UnitType.LEVEL_ONE_PAINT_TOWER)   allyPaintTowers++;
                    else if (base == UnitType.LEVEL_ONE_MONEY_TOWER)  allyMoneyTowers++;
                    else if (base == UnitType.LEVEL_ONE_DEFENSE_TOWER) allyDefenseTowers++;
                }
            }
        }
    }

    /**
     * Greedy tower type selection heuristic:
     * Maximize economy first (money towers), then paint supply (paint towers),
     * then defense if we have many towers.
     * Ratio target: 2 money : 2 paint : 1 defense (cycling)
     */
    public static UnitType chooseTowerType() {
        // If we have no paint towers, prefer paint (ensures painting capacity)
        if (allyPaintTowers == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        // Favor money towers early to fund aggressive spawning/upgrades
        if (totalAllyTowers < 3 || allyMoneyTowers < Math.max(1, allyPaintTowers * 0.7))
            return UnitType.LEVEL_ONE_MONEY_TOWER;

        // If enemy towers seen recently, prioritize defense
        if (G.lastKnownEnemyTower != null && G.round - G.lastEnemyTowerRound < 60 && allyDefenseTowers == 0)
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;

        // Maintain simple balance: prefer paint slightly over money once economy exists
        float paintRatio  = (float) allyPaintTowers  / Math.max(1, totalAllyTowers);
        float moneyRatio  = (float) allyMoneyTowers  / Math.max(1, totalAllyTowers);

        if (paintRatio < 0.45f) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (moneyRatio < 0.35f) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    /**
     * Update symmetry based on newly seen ruins/walls.
     * A symmetric map means we can predict enemy tower locations.
     */
    public static void updateSymmetry(MapLocation loc, boolean isWall) {
        // Horizontal reflection: (x, y) <-> (x, H-1-y)
        if (symmetry[0]) {
            MapLocation mirror = new MapLocation(loc.x, G.mapHeight - 1 - loc.y);
            // If mirror is observed as a wall and loc is not (or vice versa), symmetry broken
            // We'll do a simple check based on ruin presence
        }
        // For now symmetry is inferred from ruin data in getSymmetricLoc
    }

    /** Get the likely enemy position of an ally tower (for attack targeting). */
    public static MapLocation getEnemyMirror(MapLocation allyLoc) {
        // Try all 3 symmetry types and return best guess
        if (symmetry[0]) return new MapLocation(G.mapWidth - 1 - allyLoc.x, allyLoc.y);
        if (symmetry[1]) return new MapLocation(allyLoc.x, G.mapHeight - 1 - allyLoc.y);
        return new MapLocation(G.mapWidth - 1 - allyLoc.x, G.mapHeight - 1 - allyLoc.y);
    }

    /** Scan nearby tiles and register ruins/towers. */
    public static void scanNearby() throws Exception {
        // Register ruins
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            MapLocation ruin = G.nearbyRuins[i];
            Team t = Team.NEUTRAL;
            UnitType ut = null;
            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo ri = G.rc.senseRobotAtLocation(ruin);
                if (ri != null) { t = ri.team; ut = ri.type; }
            }
            registerRuin(ruin, t, ut);
        }
        // Track enemy towers from nearby enemies
        for (int i = G.nearbyEnemies.length; --i >= 0;) {
            RobotInfo r = G.nearbyEnemies[i];
            if (!r.type.isRobotType()) {
                // It's an enemy tower
                registerRuin(r.location, G.opponentTeam, r.type);
                G.lastKnownEnemyTower = r.location;
                G.lastEnemyTowerRound = G.round;
            }
        }
    }

    /** Find closest unbuilt ruin to 'from' that we haven't yet claimed. */
    public static MapLocation closestNeutralRuin(MapLocation from) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = numRuins; --i >= 0;) {
            if (ruinStatus[i] == 0) { // neutral
                int d = from.distanceSquaredTo(ruinLocs[i]);
                if (d < bestDist) { bestDist = d; best = ruinLocs[i]; }
            }
        }
        return best;
    }

    /** Find closest enemy ruin. */
    public static MapLocation closestEnemyTower(MapLocation from) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = numRuins; --i >= 0;) {
            if (ruinStatus[i] == 2) {
                int d = from.distanceSquaredTo(ruinLocs[i]);
                if (d < bestDist) { bestDist = d; best = ruinLocs[i]; }
            }
        }
        return best;
    }
}
