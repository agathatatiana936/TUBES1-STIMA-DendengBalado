package main_bot;

import battlecode.common.*;

public class MapMemory {

    public static final int MAX_RUINS = 100;
    public static int numRuins = 0;
    public static MapLocation[] ruinLocs     = new MapLocation[MAX_RUINS];
    public static Team[]        ruinTeams    = new Team[MAX_RUINS];
    public static UnitType[]    ruinTypes    = new UnitType[MAX_RUINS];
    public static int[]         ruinStatus   = new int[MAX_RUINS]; // 0=neutral ruin, 1=ally, 2=enemy
    public static int[]         ruinClaimRound = new int[MAX_RUINS];

    public static int allyPaintTowers = 0;
    public static int allyMoneyTowers = 0;
    public static int allyDefenseTowers = 0;
    public static int totalAllyTowers = 0;

    public static boolean[] symmetry = {true, true, true};

    public static int[][] towerGrid = new int[12][12];
    static {
        for (int i = 0; i < 12; i++)
            for (int j = 0; j < 12; j++)
                towerGrid[i][j] = -1;
        for (int k = 0; k < MAX_RUINS; k++) ruinClaimRound[k] = -9999;
    }

    public static void registerRuin(MapLocation loc, Team team, UnitType type) {
        int gx = loc.x / 5, gy = loc.y / 5;
        if (gx >= 12 || gy >= 12) return;
        int idx = towerGrid[gy][gx];
        if (idx >= 0) {
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
        if (ruinStatus[idx] == 1) ruinClaimRound[idx] = -9999;
        recomputeAllyCount();
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

    public static UnitType chooseTowerType() {
        if (allyPaintTowers == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (totalAllyTowers < 3 || allyMoneyTowers < Math.max(1, allyPaintTowers * 0.7))
            return UnitType.LEVEL_ONE_MONEY_TOWER;

        if (G.lastKnownEnemyTower != null && G.round - G.lastEnemyTowerRound < 60 && allyDefenseTowers == 0)
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;

        float paintRatio  = (float) allyPaintTowers  / Math.max(1, totalAllyTowers);
        float moneyRatio  = (float) allyMoneyTowers  / Math.max(1, totalAllyTowers);

        if (paintRatio < 0.45f) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (moneyRatio < 0.35f) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    public static MapLocation getEnemyMirror(MapLocation allyLoc) {
        if (symmetry[0]) return new MapLocation(G.mapWidth - 1 - allyLoc.x, allyLoc.y);
        if (symmetry[1]) return new MapLocation(allyLoc.x, G.mapHeight - 1 - allyLoc.y);
        return new MapLocation(G.mapWidth - 1 - allyLoc.x, G.mapHeight - 1 - allyLoc.y);
    }

    public static void scanNearby() throws Exception {
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
        for (int i = G.nearbyEnemies.length; --i >= 0;) {
            RobotInfo r = G.nearbyEnemies[i];
            if (!r.type.isRobotType()) {
                registerRuin(r.location, G.opponentTeam, r.type);
                G.lastKnownEnemyTower = r.location;
                G.lastEnemyTowerRound = G.round;
            }
        }
    }

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
