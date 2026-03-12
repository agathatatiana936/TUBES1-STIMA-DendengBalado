package bot2lesgo;

import battlecode.common.*;

public class Robot {

    // =========================================================
    // CACHED ENGINE PATTERNS
    // =========================================================
    public static boolean[][] resourcePattern;
    public static boolean[][] moneyTowerPattern;
    public static boolean[][] paintTowerPattern;
    public static boolean[][] defenseTowerPattern;

    /**
     * Prioritas tower global untuk bot ekonomi:
     * money -> paint -> defense
     *
     * Soldier nanti boleh override keputusan finalnya,
     * tapi urutan ini jadi fallback umum.
     */
    public static final UnitType[] TOWER_PRIORITY = {
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
    };

    // =========================================================
    // SHARED ROBOT TUNING
    // =========================================================
    public static final int LOW_PAINT_REFILL_TRIGGER = 25;
    public static final int EXCESS_PAINT_DONATE_TRIGGER = 120;
    public static final int DEFAULT_REFILL_AMOUNT = 40;

    /**
     * Stop retreat jika paint sudah >= 75% capacity.
     * Tidak pakai floating point supaya simpel dan deterministic.
     */
    public static final int STOP_RETREAT_NUM = 3;
    public static final int STOP_RETREAT_DEN = 4;

    // =========================================================
    // INIT
    // =========================================================
    public static void init() throws Exception {
        resourcePattern = G.rc.getResourcePattern();
        moneyTowerPattern = G.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
        paintTowerPattern = G.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        defenseTowerPattern = G.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER);

        // sinkronisasi state motion awal
        Motion.lastPaint = G.rc.getPaint();
        Motion.paintLost = 0;
        Motion.paintNeededToStopRetreating =
                (G.rc.getType().paintCapacity * STOP_RETREAT_NUM) / STOP_RETREAT_DEN;

        dispatchInit();
    }

    // =========================================================
    // MAIN ROBOT TURN
    // =========================================================
    public static void run() throws Exception {
        // bookkeeping paint loss untuk heuristic motion / retreat
        int curPaint = G.rc.getPaint();
        if (Motion.lastPaint > curPaint) {
            Motion.paintLost += (Motion.lastPaint - curPaint);
        }

        // langkah umum sebelum unit-specific logic
        tryRefillFromNearbyAllyTower();
        tryCompleteNearbyTowerPattern();
        tryCompleteNearbyResourcePattern();

        // role-specific logic
        dispatchRun();

        // langkah umum sesudah unit-specific logic
        tryCompleteNearbyTowerPattern();
        tryCompleteNearbyResourcePattern();
        tryDonateExcessPaintToNearbyTower();

        Motion.lastPaint = G.rc.getPaint();
        appendIndicator();
    }

    // =========================================================
    // DISPATCH
    // =========================================================
    private static void dispatchInit() throws Exception {
        switch (G.rc.getType()) {
            case SOLDIER -> Soldier.init();
            case SPLASHER -> Splasher.init();
            case MOPPER -> Mopper.init();
            default -> throw new Exception("Robot.init() dipanggil pada unit non-robot");
        }
    }

    private static void dispatchRun() throws Exception {
        switch (G.rc.getType()) {
            case SOLDIER -> Soldier.run();
            case SPLASHER -> Splasher.run();
            case MOPPER -> Mopper.run();
            default -> throw new Exception("Robot.run() dipanggil pada unit non-robot");
        }
    }

    // =========================================================
    // GENERAL PATTERN COMPLETION
    // =========================================================

    /**
     * Coba menyelesaikan tower pattern di ruin yang terlihat.
     *
     * Urutan:
     * 1. Jika ada mark penentu, coba tower type itu dulu.
     * 2. Jika tidak ada / gagal, fallback ke urutan prioritas ekonomi.
     */
    public static boolean tryCompleteNearbyTowerPattern() throws GameActionException {
        boolean completed = false;

        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin == null) continue;

            UnitType intended = inferTowerTypeFromMarker(ruin);

            if (intended != null) {
                if (G.rc.canCompleteTowerPattern(intended, ruin)) {
                    G.rc.completeTowerPattern(intended, ruin);
                    completed = true;
                    continue;
                }
            }

            for (int j = 0; j < TOWER_PRIORITY.length; j++) {
                UnitType towerType = TOWER_PRIORITY[j];
                if (G.rc.canCompleteTowerPattern(towerType, ruin)) {
                    G.rc.completeTowerPattern(towerType, ruin);
                    completed = true;
                    break;
                }
            }
        }

        return completed;
    }

    /**
     * Coba menyelesaikan resource pattern yang pusatnya terlihat.
     * Scan dari nearbyMapInfos yang memang sudah di-cache di RobotPlayer.
     */
    public static boolean tryCompleteNearbyResourcePattern() throws GameActionException {
        boolean completed = false;

        for (int i = G.nearbyMapInfos.length - 1; i >= 0; i--) {
            MapInfo info = G.nearbyMapInfos[i];
            if (info == null) continue;

            MapLocation loc = info.getMapLocation();
            if (loc == null) continue;

            if (info.isResourcePatternCenter() && G.rc.canCompleteResourcePattern(loc)) {
                G.rc.completeResourcePattern(loc);
                completed = true;
            }
        }

        return completed;
    }

    /**
     * Gunakan mark sekitar ruin sebagai penanda tipe tower.
     * Konvensi:
     * - WEST  = money tower
     * - EAST  = paint tower
     * - SOUTH = defense tower
     *
     * Jika tidak ada mark yang valid, return null.
     */
    public static UnitType inferTowerTypeFromMarker(MapLocation ruin) throws GameActionException {
        if (ruin == null) return null;

        MapLocation west = ruin.add(Direction.WEST);
        if (G.rc.canSenseLocation(west)) {
            MapInfo westInfo = G.rc.senseMapInfo(west);
            if (westInfo.getMark() != PaintType.EMPTY) {
                return UnitType.LEVEL_ONE_MONEY_TOWER;
            }
        }

        MapLocation east = ruin.add(Direction.EAST);
        if (G.rc.canSenseLocation(east)) {
            MapInfo eastInfo = G.rc.senseMapInfo(east);
            if (eastInfo.getMark() != PaintType.EMPTY) {
                return UnitType.LEVEL_ONE_PAINT_TOWER;
            }
        }

        MapLocation south = ruin.add(Direction.SOUTH);
        if (G.rc.canSenseLocation(south)) {
            MapInfo southInfo = G.rc.senseMapInfo(south);
            if (southInfo.getMark() != PaintType.EMPTY) {
                return UnitType.LEVEL_ONE_DEFENSE_TOWER;
            }
        }

        return null;
    }

    // =========================================================
    // SHARED PAINT LOGIC
    // =========================================================

    /**
     * Ambil paint dari allied tower terdekat jika paint rendah.
     * Di API Battlecode, transferPaint dengan amount negatif berarti mengambil paint
     * dari allied tower/robot yang valid.
     */
    public static boolean tryRefillFromNearbyAllyTower() throws GameActionException {
        if (!G.rc.isActionReady()) return false;
        if (G.rc.getPaint() > LOW_PAINT_REFILL_TRIGGER) return false;

        RobotInfo bestTower = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null) continue;
            if (!ally.type.isTowerType()) continue;

            int dist = G.me.distanceSquaredTo(ally.location);
            if (dist < bestDist) {
                bestDist = dist;
                bestTower = ally;
            }
        }

        if (bestTower == null) return false;

        int capacity = G.rc.getType().paintCapacity;
        int need = capacity - G.rc.getPaint();
        if (need <= 0) return false;

        int amount = G.min(DEFAULT_REFILL_AMOUNT, need);
        if (G.rc.canTransferPaint(bestTower.location, -amount)) {
            G.rc.transferPaint(bestTower.location, -amount);
            return true;
        }

        return false;
    }

    /**
     * Jika paint sangat berlebih, kembalikan sebagian ke tower terdekat.
     * Ini menjaga efisiensi ekonomi paint.
     */
    public static boolean tryDonateExcessPaintToNearbyTower() throws GameActionException {
        if (!G.rc.isActionReady()) return false;
        if (G.rc.getPaint() < EXCESS_PAINT_DONATE_TRIGGER) return false;

        RobotInfo bestTower = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null) continue;
            if (!ally.type.isTowerType()) continue;

            int dist = G.me.distanceSquaredTo(ally.location);
            if (dist < bestDist) {
                bestDist = dist;
                bestTower = ally;
            }
        }

        if (bestTower == null) return false;

        int excess = G.rc.getPaint() - EXCESS_PAINT_DONATE_TRIGGER;
        if (excess <= 0) return false;

        int give = G.min(excess, 30);
        if (G.rc.canTransferPaint(bestTower.location, give)) {
            G.rc.transferPaint(bestTower.location, give);
            return true;
        }

        return false;
    }

    // =========================================================
    // SHARED UTILITIES FOR ROLE FILES
    // =========================================================

    public static boolean[][] getTowerPattern(UnitType towerType) {
        if (towerType == UnitType.LEVEL_ONE_MONEY_TOWER) return moneyTowerPattern;
        if (towerType == UnitType.LEVEL_ONE_PAINT_TOWER) return paintTowerPattern;
        return defenseTowerPattern;
    }

    public static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_MONEY_TOWER
                || type == UnitType.LEVEL_ONE_PAINT_TOWER
                || type == UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }

    /**
     * Default pilihan tower global.
     * Soldier nanti boleh override dengan heuristic yang lebih kaya.
     */
    public static UnitType defaultTowerChoice() {
        if (G.isEarlyGame()) {
            if (G.rc.getNumberTowers() < 2) {
                return UnitType.LEVEL_ONE_MONEY_TOWER;
            }
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        if (G.isMidGame()) {
            if (G.lowPaint()) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // late game: mulai lebih aman
        if (G.hasFreshDefenseTowerInfo()) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    /**
     * Cari ruin terdekat yang belum ditempati tower.
     */
    public static MapLocation closestFreeRuin() throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = G.nearbyRuins.length - 1; i >= 0; i--) {
            MapLocation ruin = G.nearbyRuins[i];
            if (ruin == null) continue;

            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo occupant = G.rc.senseRobotAtLocation(ruin);
                if (occupant != null) continue;
            }

            int d = G.me.distanceSquaredTo(ruin);
            if (d < bestDist) {
                bestDist = d;
                best = ruin;
            }
        }

        return best;
    }

    // =========================================================
    // DEBUG
    // =========================================================
    private static void appendIndicator() {
        if (G.indicatorString == null) {
            G.indicatorString = new StringBuilder();
        }

        G.indicatorString
                .append("P=").append(G.rc.getPaint()).append(" ")
                .append("CH=").append(G.rc.getChips()).append(" ")
                .append("TW=").append(G.rc.getNumberTowers()).append(" ");
    }
}