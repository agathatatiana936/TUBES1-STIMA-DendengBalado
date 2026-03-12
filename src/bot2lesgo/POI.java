package bot2lesgo;

import battlecode.common.*;

public class POI {

    // =========================================================
    // POI TYPE CODES
    // =========================================================
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_RUIN = 1;
    public static final int TYPE_ALLY_MONEY = 2;
    public static final int TYPE_ALLY_PAINT = 3;
    public static final int TYPE_ALLY_DEFENSE = 4;
    public static final int TYPE_ENEMY_MONEY = 5;
    public static final int TYPE_ENEMY_PAINT = 6;
    public static final int TYPE_ENEMY_DEFENSE = 7;

    // =========================================================
    // MEMORY
    // max ruin pada map Battlecode 2025 dipakai banyak bot sebagai bound praktis
    // =========================================================
    public static final int MAX_POI = 144;

    public static MapLocation[] poiLocs = new MapLocation[MAX_POI];
    public static int[] poiTypes = new int[MAX_POI];
    public static int[] poiLastSeenRound = new int[MAX_POI];
    public static boolean[] poiActive = new boolean[MAX_POI];

    public static int poiCount = 0;

    // =========================================================
    // SYMMETRY MEMORY
    // true = masih mungkin
    // =========================================================
    public static boolean symmetryHorizontal = true;
    public static boolean symmetryVertical = true;
    public static boolean symmetryRotational = true;

    // =========================================================
    // OPTIONAL RELAY / COMM STATE
    // hanya helper encoding/decoding dulu;
    // transport message bisa dipanggil nanti dari Tower / Soldier
    // =========================================================
    public static final int MSG_KIND_MASK = 0x7;     // 3 bit
    public static final int MSG_LOC_MASK = 0x0FFF;   // 12 bit
    public static final int MSG_RELAY_BIT = 1 << 15; // bit ke-16 pada int 16-bit payload

    public static final int MSG_KIND_SHIFT = 12;

    public static final int SYM_BIT_H = 1;
    public static final int SYM_BIT_V = 2;
    public static final int SYM_BIT_R = 4;

    // reserved 3-bit marker untuk symmetry packet pada 16-bit payload
    public static final int SYM_MSG_MARKER = 0x7;

    // =========================================================
    // INIT / ROUND UPDATE
    // =========================================================
    public static void init() {
        poiCount = 0;
        symmetryHorizontal = true;
        symmetryVertical = true;
        symmetryRotational = true;

        for (int i = 0; i < MAX_POI; i++) {
            poiLocs[i] = null;
            poiTypes[i] = TYPE_UNKNOWN;
            poiLastSeenRound[i] = -1000000;
            poiActive[i] = false;
        }
    }

    public static void updateRound() throws GameActionException {
        if (poiLocs[0] == null && poiCount == 0 && !poiActive[0]) {
            init();
        }

        observeVisibleRuins();
        observeVisibleTowers();
        updateSymmetryFromVisibleTiles();
    }

    // =========================================================
    // OBSERVATION
    // =========================================================
    public static void observeVisibleRuins() throws GameActionException {
        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin == null) continue;

            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(ruin);
                if (robot != null && robot.type.isTowerType()) {
                    upsertPOI(ruin, towerTypeToPOIType(robot));
                    continue;
                }
            }

            upsertPOI(ruin, TYPE_RUIN);
        }
    }

    public static void observeVisibleTowers() {
        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null || !ally.type.isTowerType()) continue;
            upsertPOI(ally.location, towerTypeToPOIType(ally));
        }

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null || !enemy.type.isTowerType()) continue;
            upsertPOI(enemy.location, towerTypeToPOIType(enemy));
        }
    }

    /**
     * Symmetry elimination ringan:
     * bandingkan paint/passability tile yang kelihatan terhadap bayangannya.
     * Hanya eliminasi jika kedua sisi bisa disense.
     */
    public static void updateSymmetryFromVisibleTiles() throws GameActionException {
        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation a = info.getMapLocation();

            if (symmetryHorizontal) {
                MapLocation b = G.reflectHorizontal(a);
                if (G.rc.canSenseLocation(b)) {
                    if (!sameTileSignature(info, G.rc.senseMapInfo(b))) {
                        symmetryHorizontal = false;
                    }
                }
            }

            if (symmetryVertical) {
                MapLocation b = G.reflectVertical(a);
                if (G.rc.canSenseLocation(b)) {
                    if (!sameTileSignature(info, G.rc.senseMapInfo(b))) {
                        symmetryVertical = false;
                    }
                }
            }

            if (symmetryRotational) {
                MapLocation b = G.reflectRotational(a);
                if (G.rc.canSenseLocation(b)) {
                    if (!sameTileSignature(info, G.rc.senseMapInfo(b))) {
                        symmetryRotational = false;
                    }
                }
            }
        }
    }

    private static boolean sameTileSignature(MapInfo a, MapInfo b) {
        if (a == null || b == null) return true;
        if (a.isWall() != b.isWall()) return false;
        if (a.hasRuin() != b.hasRuin()) return false;
        if (a.isPassable() != b.isPassable()) return false;
        return true;
    }

    // =========================================================
    // UPSERT / LOOKUP
    // =========================================================
    public static int upsertPOI(MapLocation loc, int type) {
        if (loc == null) return -1;

        int idx = findPOI(loc);
        if (idx != -1) {
            poiTypes[idx] = type;
            poiLastSeenRound[idx] = G.round;
            poiActive[idx] = true;
            return idx;
        }

        if (poiCount >= MAX_POI) {
            int replace = oldestPOIIndex();
            poiLocs[replace] = loc;
            poiTypes[replace] = type;
            poiLastSeenRound[replace] = G.round;
            poiActive[replace] = true;
            return replace;
        }

        poiLocs[poiCount] = loc;
        poiTypes[poiCount] = type;
        poiLastSeenRound[poiCount] = G.round;
        poiActive[poiCount] = true;
        poiCount++;
        return poiCount - 1;
    }

    public static int findPOI(MapLocation loc) {
        if (loc == null) return -1;
        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            if (poiLocs[i] != null && poiLocs[i].equals(loc)) {
                return i;
            }
        }
        return -1;
    }

    private static int oldestPOIIndex() {
        int best = 0;
        int bestRound = Integer.MAX_VALUE;

        for (int i = 0; i < MAX_POI; i++) {
            if (!poiActive[i]) return i;
            if (poiLastSeenRound[i] < bestRound) {
                bestRound = poiLastSeenRound[i];
                best = i;
            }
        }
        return best;
    }

    // =========================================================
    // QUERIES
    // =========================================================
    public static MapLocation closestRuin() {
        return closestOfType(TYPE_RUIN);
    }

    public static MapLocation closestAllyTower() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            int t = poiTypes[i];
            if (!isAllyTowerType(t)) continue;

            MapLocation loc = poiLocs[i];
            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static MapLocation closestEnemyTower() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            int t = poiTypes[i];
            if (!isEnemyTowerType(t)) continue;

            MapLocation loc = poiLocs[i];
            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static MapLocation closestPaintTower() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            int t = poiTypes[i];
            if (t != TYPE_ALLY_PAINT) continue;

            MapLocation loc = poiLocs[i];
            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static MapLocation closestOfType(int type) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            if (poiTypes[i] != type) continue;

            MapLocation loc = poiLocs[i];
            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static MapLocation closestUnoccupiedRuin() throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            if (poiTypes[i] != TYPE_RUIN) continue;

            MapLocation loc = poiLocs[i];

            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(loc);
                if (robot != null && robot.type.isTowerType()) {
                    upsertPOI(loc, towerTypeToPOIType(robot));
                    continue;
                }
            }

            int d = G.me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }

    public static int countEnemyTowersKnown() {
        int cnt = 0;
        for (int i = poiCount - 1; i >= 0; i--) {
            if (!poiActive[i]) continue;
            if (isEnemyTowerType(poiTypes[i])) cnt++;
        }
        return cnt;
    }

    // =========================================================
    // TYPE HELPERS
    // =========================================================
    public static int towerTypeToPOIType(RobotInfo robot) {
        if (robot == null) return TYPE_UNKNOWN;

        boolean ally = robot.team == G.team;
        UnitType base = robot.type.getBaseType();

        if (ally) {
            if (base == UnitType.LEVEL_ONE_MONEY_TOWER) return TYPE_ALLY_MONEY;
            if (base == UnitType.LEVEL_ONE_PAINT_TOWER) return TYPE_ALLY_PAINT;
            return TYPE_ALLY_DEFENSE;
        } else {
            if (base == UnitType.LEVEL_ONE_MONEY_TOWER) return TYPE_ENEMY_MONEY;
            if (base == UnitType.LEVEL_ONE_PAINT_TOWER) return TYPE_ENEMY_PAINT;
            return TYPE_ENEMY_DEFENSE;
        }
    }

    public static boolean isAllyTowerType(int t) {
        return t == TYPE_ALLY_MONEY || t == TYPE_ALLY_PAINT || t == TYPE_ALLY_DEFENSE;
    }

    public static boolean isEnemyTowerType(int t) {
        return t == TYPE_ENEMY_MONEY || t == TYPE_ENEMY_PAINT || t == TYPE_ENEMY_DEFENSE;
    }

    // =========================================================
    // COMPACT ENCODING HELPERS
    // 16-bit payload:
    // bits 0..11  : location (6 bits x, 6 bits y)
    // bits 12..14 : kind
    // bit 15      : relay
    //
    // symmetry payload:
    // bits 0..2   : ruled-out bits
    // bits 12..14 : 111
    // bit 15      : optional relay
    // =========================================================
    public static int encodeLoc12(MapLocation loc) {
        if (loc == null) return 0;
        int x = G.clamp(loc.x, 0, 63);
        int y = G.clamp(loc.y, 0, 63);
        return x | (y << 6);
    }

    public static MapLocation decodeLoc12(int bits) {
        int x = bits & 63;
        int y = (bits >> 6) & 63;
        return new MapLocation(x, y);
    }

    public static int encodePOIMessage16(MapLocation loc, int type, boolean relay) {
        int msg = encodeLoc12(loc) | ((type & MSG_KIND_MASK) << MSG_KIND_SHIFT);
        if (relay) msg |= MSG_RELAY_BIT;
        return msg;
    }

    public static int encodeSymmetryMessage16(boolean ruleOutH, boolean ruleOutV, boolean ruleOutR, boolean relay) {
        int ruledOut = 0;
        if (ruleOutH) ruledOut |= SYM_BIT_H;
        if (ruleOutV) ruledOut |= SYM_BIT_V;
        if (ruleOutR) ruledOut |= SYM_BIT_R;

        int msg = ruledOut | (SYM_MSG_MARKER << MSG_KIND_SHIFT);
        if (relay) msg |= MSG_RELAY_BIT;
        return msg;
    }

    public static boolean isSymmetryMessage16(int msg) {
        return ((msg >> MSG_KIND_SHIFT) & MSG_KIND_MASK) == SYM_MSG_MARKER;
    }

    public static boolean isRelayMessage16(int msg) {
        return (msg & MSG_RELAY_BIT) != 0;
    }

    public static int decodeType16(int msg) {
        return (msg >> MSG_KIND_SHIFT) & MSG_KIND_MASK;
    }

    public static MapLocation decodeLoc16(int msg) {
        return decodeLoc12(msg & MSG_LOC_MASK);
    }

    public static void applyMessage16(int msg) {
        if (isSymmetryMessage16(msg)) {
            int bits = msg & 0x7;
            if ((bits & SYM_BIT_H) != 0) symmetryHorizontal = false;
            if ((bits & SYM_BIT_V) != 0) symmetryVertical = false;
            if ((bits & SYM_BIT_R) != 0) symmetryRotational = false;
            return;
        }

        MapLocation loc = decodeLoc16(msg);
        int type = decodeType16(msg);
        upsertPOI(loc, type);
    }

    /**
     * Gabungkan dua 16-bit payload ke 32-bit int.
     * Berguna kalau nanti kamu ingin 1 tower broadcast 2 info sekaligus.
     */
    public static int packTwo16(int lo, int hi) {
        return (lo & 0xFFFF) | ((hi & 0xFFFF) << 16);
    }

    public static int unpackLo16(int packed) {
        return packed & 0xFFFF;
    }

    public static int unpackHi16(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    // =========================================================
    // SIMPLE TARGET HELPERS FOR SOLDIER / MOPPER / SPLASHER
    // =========================================================
    public static MapLocation preferredEconomicObjective() throws GameActionException {
        MapLocation ruin = closestUnoccupiedRuin();
        if (ruin != null) return ruin;

        if (G.lowPaint()) {
            MapLocation paint = closestPaintTower();
            if (paint != null) return paint;
        }

        MapLocation enemy = closestEnemyTower();
        if (enemy != null) return enemy;

        return null;
    }

    public static MapLocation predictedEnemyFromSymmetry() {
        // prediksi ringan dari posisi sendiri terhadap symmetry map
        if (symmetryRotational) return G.reflectRotational(G.me);
        if (symmetryHorizontal) return G.reflectHorizontal(G.me);
        if (symmetryVertical) return G.reflectVertical(G.me);
        return G.mapCenter;
    }
}