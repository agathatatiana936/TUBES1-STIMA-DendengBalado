package bot2lesgo;

import battlecode.common.*;

public class Tower {

    // =========================================================
    // SPAWN / MACRO STATE
    // =========================================================
    public static int spawnedSoldiers = 0;
    public static int spawnedSplashers = 0;
    public static int spawnedMoppers = 0;

    public static int lastSpawnRound = -1000000;
    public static int lastUpgradeRound = -1000000;
    public static int lastBroadcastRound = -1000000;

    public static MapLocation spawnAnchor = null;

    // =========================================================
    // TUNING CONSTANTS
    // Fokus bot: paint coverage tetap utama,
    // heuristik utama: ekonomi / pembangunan tower.
    // =========================================================
    public static final int MIN_ROUNDS_BETWEEN_SPAWNS = 1;
    public static final int MIN_ROUNDS_BETWEEN_BROADCASTS = 3;

    public static final int EARLY_SOLDIER_TARGET = 2;
    public static final int EARLY_MOPPER_TARGET = 1;
    public static final int EARLY_SPLASHER_TARGET = 2;

    public static final int MID_SOLDIER_TARGET = 5;
    public static final int MID_MOPPER_TARGET = 2;
    public static final int MID_SPLASHER_TARGET = 2;

    public static final int LATE_SOLDIER_TARGET = 6;
    public static final int LATE_MOPPER_TARGET = 2;
    public static final int LATE_SPLASHER_TARGET = 4;

    public static final int MONEY_UPGRADE_BUFFER = 250;
    public static final int PAINT_UPGRADE_BUFFER = 200;
    public static final int DEFENSE_UPGRADE_BUFFER = 150;

    public static final int EMERGENCY_ENEMY_COUNT_FOR_MOPPER = 2;
    public static final int EMERGENCY_ENEMY_COUNT_FOR_SPLASHER = 3;

    public static final int LOW_TEAM_CHIPS = 900;
    public static final int HIGH_TEAM_CHIPS = 2200;

    // =========================================================
    // INIT
    // =========================================================
    public static void init() throws Exception {
        spawnAnchor = G.me;
    }

    // =========================================================
    // MAIN TOWER TURN
    // =========================================================
    public static void run() throws Exception {
        doTowerAttack();
        tryBroadcastUsefulInfo();
        tryUpgradeSelf();
        trySpawnUnit();
        appendIndicator();
    }

    // =========================================================
    // ATTACK LOGIC
    // =========================================================
    public static boolean doTowerAttack() throws GameActionException {
        if (!G.rc.isActionReady()) return false;

        MapLocation target = chooseBestAttackTarget();
        if (target != null && G.rc.canAttack(target)) {
            G.rc.attack(target);
            return true;
        }
        return false;
    }

    private static MapLocation chooseBestAttackTarget() {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy == null) continue;

            int score = 0;

            if (enemy.type.isRobotType()) score += 60;
            if (enemy.type == UnitType.SOLDIER) score += 25;
            if (enemy.type == UnitType.SPLASHER) score += 20;
            if (enemy.type == UnitType.MOPPER) score += 15;

            // prioritaskan target yang lebih lemah
            score += (300 - enemy.health);

            // prioritaskan yang lebih dekat
            score -= G.me.distanceSquaredTo(enemy.location);

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }

        return best == null ? null : best.location;
    }

    // =========================================================
    // SPAWN LOGIC
    // =========================================================
    public static boolean trySpawnUnit() throws GameActionException {
        if (!G.rc.isActionReady()) return false;
        if (G.round - lastSpawnRound < MIN_ROUNDS_BETWEEN_SPAWNS) return false;

        UnitType next = chooseSpawnType();
        if (next == null) return false;

        MapLocation spawnLoc = chooseSpawnLocation(next);
        if (spawnLoc == null) return false;

        if (G.rc.canBuildRobot(next, spawnLoc)) {
            G.rc.buildRobot(next, spawnLoc);
            lastSpawnRound = G.round;
            onSpawn(next);
            return true;
        }

        return false;
    }

    public static UnitType chooseSpawnType() {
        int enemyCount = countNearbyEnemyRobots();
        boolean lowPaintEconomy = shouldPreferPaintEconomy();
        boolean emergency = enemyCount > 0;

        int soldierScore = getSoldierSpawnScore(enemyCount, lowPaintEconomy, emergency);
        int splasherScore = getSplasherSpawnScore(enemyCount, lowPaintEconomy, emergency);
        int mopperScore = getMopperSpawnScore(enemyCount, lowPaintEconomy, emergency);

        UnitType best = null;
        int bestScore = Integer.MIN_VALUE;

        if (canAffordSpawn(UnitType.SOLDIER) && soldierScore > bestScore) {
            bestScore = soldierScore;
            best = UnitType.SOLDIER;
        }
        if (canAffordSpawn(UnitType.SPLASHER) && splasherScore > bestScore) {
            bestScore = splasherScore;
            best = UnitType.SPLASHER;
        }
        if (canAffordSpawn(UnitType.MOPPER) && mopperScore > bestScore) {
            bestScore = mopperScore;
            best = UnitType.MOPPER;
        }

        return best;
    }

    private static int getSoldierSpawnScore(int enemyCount, boolean lowPaintEconomy, boolean emergency) {
        int score = G.SOLDIER_BASE_SPAWN_SCORE;

        if (G.isEarlyGame()) {
            score += 35;
            if (spawnedSoldiers < EARLY_SOLDIER_TARGET) score += 70;
        } else if (G.isMidGame()) {
            if (spawnedSoldiers < MID_SOLDIER_TARGET) score += 70;
        } else {
            if (spawnedSoldiers < LATE_SOLDIER_TARGET) score += 35;
        }

        // soldier = eksekutor utama pembangunan tower / ruin hunting
        score += G.SCORE_BUILD_TOWER / 8;
        score += G.SCORE_BUILD_RESOURCE_PATTERN / 12;

        if (lowPaintEconomy) score -= 10;
        if (enemyCount >= 2) score += 20;
        if (enemyCount >= 4) score -= 10;
        if (emergency) score += 8;

        if (G.rc.getNumberTowers() < 3) score += 30;
        if (G.rc.getChips() > HIGH_TEAM_CHIPS) score += 20;

        return score;
    }

    private static int getSplasherSpawnScore(int enemyCount, boolean lowPaintEconomy, boolean emergency) {
        int score = G.SPLASHER_BASE_SPAWN_SCORE;

        if (G.isEarlyGame()) {
            if (spawnedSplashers < EARLY_SPLASHER_TARGET) score += 120;
            score += 35; // splasher memang pembuka map
        } else if (G.isMidGame()) {
            score += G.SPLASHER_MID_BONUS;
            if (spawnedSplashers < MID_SPLASHER_TARGET) score += 70;
        } else {
            if (spawnedSplashers < LATE_SPLASHER_TARGET) score += 90;
        }

        if (enemyCount >= EMERGENCY_ENEMY_COUNT_FOR_SPLASHER) score += 60;
        if (lowPaintEconomy) score -= 10; // jangan paksa splasher kalau supply paint mepet
        if (G.rc.getChips() < LOW_TEAM_CHIPS) score -= 10;

        return score;
    }

    private static int getMopperSpawnScore(int enemyCount, boolean lowPaintEconomy, boolean emergency) {
        int score = G.MOPPER_BASE_SPAWN_SCORE;

        if (G.isEarlyGame()) {
            if (spawnedMoppers < EARLY_MOPPER_TARGET) score += 95;
        } else if (G.isMidGame()) {
            if (spawnedMoppers < MID_MOPPER_TARGET) score += 55;
        } else {
            if (spawnedMoppers < LATE_MOPPER_TARGET) score += 30;
        }

        if (enemyCount >= EMERGENCY_ENEMY_COUNT_FOR_MOPPER) score += 70;
        if (G.hasFreshDefenseTowerInfo()) score += G.MOPPER_DEFENSE_BONUS;
        if (lowPaintEconomy) score += 10;
        if (G.rc.getNumberTowers() <= 2) score += 15;
        if (emergency) score += 15;

        return score;
    }

    private static boolean canAffordSpawn(UnitType type) {
        int chips = G.rc.getChips();
        int paint = G.rc.getPaint();

        return chips >= type.moneyCost && paint >= type.paintCost;
    }

    private static void onSpawn(UnitType type) {
        if (type == UnitType.SOLDIER) spawnedSoldiers++;
        else if (type == UnitType.SPLASHER) spawnedSplashers++;
        else if (type == UnitType.MOPPER) spawnedMoppers++;
    }

    private static MapLocation chooseSpawnLocation(UnitType type) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < G.DIRECTIONS.length; i++) {
            Direction d = G.DIRECTIONS[i];
            MapLocation loc = G.me.add(d);

            if (!G.rc.onTheMap(loc)) continue;
            if (!G.rc.canBuildRobot(type, loc)) continue;

            int score = 0;

            // spawn agak maju ke tengah map
            score -= loc.distanceSquaredTo(G.mapCenter);

            // hindari crowding dekat allied robot
            score -= countAdjacentAllies(loc) * 18;

            // kalau ada musuh, spawn menghadap ancaman
            RobotInfo nearestEnemy = G.closestRobot(G.opponentRobots);
            if (nearestEnemy != null) {
                score -= loc.distanceSquaredTo(nearestEnemy.location) * 2;
            }

            // robot builder utama sebaiknya lebih aktif ke area terbuka
            if (type == UnitType.SOLDIER) score += 20;
            if (type == UnitType.MOPPER) score += 10;
            if (type == UnitType.SPLASHER) score += 12;

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        return best;
    }

    private static int countAdjacentAllies(MapLocation loc) {
        int cnt = 0;
        for (int i = G.allyRobots.length - 1; i >= 0; i--) {
            RobotInfo ally = G.allyRobots[i];
            if (ally == null) continue;
            if (ally.location.distanceSquaredTo(loc) <= 2) cnt++;
        }
        return cnt;
    }

    private static int countNearbyEnemyRobots() {
        int cnt = 0;
        for (int i = G.opponentRobots.length - 1; i >= 0; i--) {
            RobotInfo enemy = G.opponentRobots[i];
            if (enemy != null && enemy.type.isRobotType()) cnt++;
        }
        return cnt;
    }

    private static boolean shouldPreferPaintEconomy() {
        UnitType base = G.rc.getType().getBaseType();
        return base == UnitType.LEVEL_ONE_PAINT_TOWER || G.lowPaint();
    }

    // =========================================================
    // UPGRADE LOGIC
    // =========================================================
    public static boolean tryUpgradeSelf() throws GameActionException {
        if (G.round == lastUpgradeRound) return false;
        if (!G.rc.getType().canUpgradeType()) return false;
        if (!G.rc.canUpgradeTower(G.me)) return false;

        int reserve = getUpgradeReserve();
        if (G.rc.getChips() < reserve) return false;

        if (shouldUpgradeNow()) {
            G.rc.upgradeTower(G.me);
            lastUpgradeRound = G.round;
            return true;
        }
        return false;
    }

    private static int getUpgradeReserve() {
        UnitType base = G.rc.getType().getBaseType();
        if (base == UnitType.LEVEL_ONE_MONEY_TOWER) return MONEY_UPGRADE_BUFFER;
        if (base == UnitType.LEVEL_ONE_PAINT_TOWER) return PAINT_UPGRADE_BUFFER;
        return DEFENSE_UPGRADE_BUFFER;
    }

    private static boolean shouldUpgradeNow() {
        UnitType type = G.rc.getType();
        UnitType base = type.getBaseType();

        if (!type.canUpgradeType()) return false;

        // money tower: upgrade lebih agresif kalau ekonomi stabil
        if (base == UnitType.LEVEL_ONE_MONEY_TOWER) {
            if (G.rc.getChips() > HIGH_TEAM_CHIPS) return true;
            if (G.isMidGame() && G.rc.getNumberTowers() >= 3) return true;
            return false;
        }

        // paint tower: penting untuk sustain paint saat ekspansi
        if (base == UnitType.LEVEL_ONE_PAINT_TOWER) {
            if (G.lowPaint()) return true;
            if (G.isMidGame()) return true;
            return G.isLateGame();
        }

        // defense tower: prioritas jika ada tekanan musuh
        if (base == UnitType.LEVEL_ONE_DEFENSE_TOWER) {
            if (countNearbyEnemyRobots() > 0) return true;
            return G.hasFreshDefenseTowerInfo();
        }

        return false;
    }

    // =========================================================
    // COMMUNICATION
    // =========================================================
    public static boolean tryBroadcastUsefulInfo() throws GameActionException {
        if (G.round - lastBroadcastRound < MIN_ROUNDS_BETWEEN_BROADCASTS) return false;
        if (!G.rc.canBroadcastMessage()) return false;

        int msg = encodeTowerStatusMessage();
        G.rc.broadcastMessage(msg);
        lastBroadcastRound = G.round;
        return true;
    }

    /**
     * Format sederhana:
     * [ type:2 ][ round mod 256:8 ][ chips band:2 ][ enemy count:4 ][ towers:6 ]
     */
    private static int encodeTowerStatusMessage() {
        int towerTypeCode = getTowerTypeCode(G.rc.getType().getBaseType());
        int roundBits = G.round & 0xFF;
        int chipBand = getChipBand(G.rc.getChips());
        int enemyCount = G.min(countNearbyEnemyRobots(), 15);
        int towers = G.min(G.rc.getNumberTowers(), 63);

        return (towerTypeCode << 20)
                | (roundBits << 12)
                | (chipBand << 10)
                | (enemyCount << 6)
                | towers;
    }

    private static int getTowerTypeCode(UnitType baseType) {
        if (baseType == UnitType.LEVEL_ONE_MONEY_TOWER) return 1;
        if (baseType == UnitType.LEVEL_ONE_PAINT_TOWER) return 2;
        return 3;
    }

    private static int getChipBand(int chips) {
        if (chips < 700) return 0;
        if (chips < 1400) return 1;
        if (chips < 2500) return 2;
        return 3;
    }

    // =========================================================
    // HELPERS FOR OTHER FILES
    // =========================================================
    public static boolean isMoneyTower() {
        return G.isMoneyTower(G.rc.getType());
    }

    public static boolean isPaintTower() {
        return G.isPaintTower(G.rc.getType());
    }

    public static boolean isDefenseTower() {
        return G.isDefenseTower(G.rc.getType());
    }

    public static UnitType preferredTowerToBuildNext() {
        if (G.isEarlyGame()) {
            if (G.rc.getNumberTowers() < G.DESIRED_EARLY_MONEY_TOWERS) {
                return UnitType.LEVEL_ONE_MONEY_TOWER;
            }
            if (G.rc.getNumberTowers() < G.DESIRED_EARLY_MONEY_TOWERS + G.DESIRED_EARLY_PAINT_TOWERS) {
                return UnitType.LEVEL_ONE_PAINT_TOWER;
            }
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        if (G.lowPaint()) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (G.hasFreshDefenseTowerInfo()) return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    // =========================================================
    // DEBUG
    // =========================================================
    private static void appendIndicator() {
        if (G.indicatorString == null) G.indicatorString = new StringBuilder();

        G.indicatorString
                .append("TT=");
        if (isMoneyTower()) G.indicatorString.append("M ");
        else if (isPaintTower()) G.indicatorString.append("P ");
        else G.indicatorString.append("D ");

        G.indicatorString
                .append("S=").append(spawnedSoldiers).append(" ")
                .append("SP=").append(spawnedSplashers).append(" ")
                .append("M=").append(spawnedMoppers).append(" ");
    }
}