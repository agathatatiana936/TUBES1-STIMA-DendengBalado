package bot2yes;

import battlecode.common.*;

/**
 * SOLDIER — primary greedy agent.
 *
 * ══════════════════════════════════════════════════════════════════════════════
 * GREEDY HEURISTICS
 * ══════════════════════════════════════════════════════════════════════════════
 * PRIMARY  : "Maximum Territory Expansion"
 *   Every move+action maximises new ally-painted tiles.
 *
 * SECONDARY: "Tower Anchor Expansion" (active only rounds 1–600)
 *   Claim neutral ruins → build towers → more spawns → more coverage.
 *   After round 600, ALL soldiers become pure expanders (no more tower building).
 *
 * ══════════════════════════════════════════════════════════════════════════════
 * TOWER BUILD PIPELINE — CORRECT IMPLEMENTATION
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  STEP 1  GOTO_RUIN  Navigate to within sensing range of target ruin.
 *
 *  STEP 2  MARK       Call markTowerPattern(type, ruinLoc).
 *                     Cost: 25 paint.
 *                     Effect: each of the 25 tiles in 5x5 gets a marker:
 *                       ALLY_PRIMARY   -> tile "1" on map, needs PRIMARY color
 *                       ALLY_SECONDARY -> tile "2" on map, needs SECONDARY color
 *
 *  STEP 3  PAINT      For each tile in the 5x5:
 *                       marker == ALLY_PRIMARY   -> attack(tile)       (primary)
 *                       marker == ALLY_SECONDARY -> attack(tile, true) (secondary)
 *                     If tile has ENEMY paint:
 *                       - mopper nearby AND waited < 40 rounds -> skip (let mopper clean)
 *                       - otherwise -> overwrite with correct color NOW
 *
 *  STEP 4  COMPLETE   Call completeTowerPattern(type, ruin) once all tiles correct.
 *
 * ══════════════════════════════════════════════════════════════════════════════
 */
public class Soldier {

    // ── Round threshold: stop building towers after this round ───────────────
    static final int TOWER_BUILD_CUTOFF = 600;

    // ── Modes ────────────────────────────────────────────────────────────────
    static final int EXPLORE   = 0;
    static final int GOTO_RUIN = 1;
    static final int MARK      = 2;
    static final int PAINT     = 3;
    static final int COMPLETE  = 4;
    static final int RETREAT   = 5;
    static final int ATTACK    = 6;

    static int mode = EXPLORE;

    // ── Tower build state ────────────────────────────────────────────────────
    static MapLocation targetRuin      = null;
    static UnitType    targetType      = null;
    static boolean     markerPlaced    = false;
    static int         buildStartRound = 0;
    static final int   BUILD_TIMEOUT   = 110;

    // ── Tower type constants ─────────────────────────────────────────────────
    static final UnitType[] TOWER_TYPES = {
        UnitType.LEVEL_ONE_PAINT_TOWER,
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_DEFENSE_TOWER
    };

    // Sentinel tile direction per tower type (guards completeTowerPattern)
    static final Direction[] SENTINEL = {
        Direction.EAST,   // PAINT
        Direction.WEST,   // MONEY
        Direction.SOUTH   // DEFENSE
    };

    // ── Pattern data ─────────────────────────────────────────────────────────
    static boolean[][][] towerPatterns = null;
    static boolean[][]   resourcePattern = null;

    // ── Explore state ─────────────────────────────────────────────────────────
    static MapLocation exploreTarget = null;
    static int         exploreAge    = 0;
    static final int   EXPLORE_TIMEOUT = 30;

    // ─────────────────────────────────────────────────────────────────────────
    public static void init() throws Exception {
        towerPatterns = new boolean[][][] {
            G.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER),
            G.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER),
            G.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER)
        };
        resourcePattern = G.rc.getResourcePattern();
        Rand.state = G.rc.getID() * 1234567 + 89;
    }

    // ─────────────────────────────────────────────────────────────────────────
    public static void run() throws Exception {

        opportunisticComplete();
        tryCompleteResourcePatterns();
        tryWithdrawPaint();

        int paint    = G.rc.getPaint();
        int paintCap = G.rc.getType().paintCapacity;
        if (paint < 35 && mode != RETREAT) mode = RETREAT;
        if (mode == RETREAT && paint >= (int)(paintCap * 0.72)) mode = EXPLORE;

        // After cutoff: abandon build, become pure expander
        if (G.round > TOWER_BUILD_CUTOFF && mode != RETREAT && mode != ATTACK) {
            if (mode != EXPLORE) abandonBuild();
        }

        if (mode != RETREAT) {
            switch (mode) {
                case EXPLORE   -> transitionExplore();
                case GOTO_RUIN -> transitionGotoRuin();
                case MARK      -> transitionMark();
                case PAINT     -> transitionPaint();
                case COMPLETE  -> transitionComplete();
                case ATTACK    -> transitionAttack();
            }
        }

        switch (mode) {
            case EXPLORE   -> doExplore();
            case GOTO_RUIN -> doGotoRuin();
            case MARK      -> doMark();
            case PAINT     -> doPaint();
            case COMPLETE  -> doComplete();
            case RETREAT   -> doRetreat();
            case ATTACK    -> doAttack();
        }

        G.indicator.append("S=" + modeStr() + " P=" + paint + " ");
    }

    // =========================================================================
    // TRANSITIONS
    // =========================================================================

    static void transitionExplore() throws Exception {
        if (G.round > TOWER_BUILD_CUTOFF) return;
        if (G.lastKnownEnemyTower != null
                && G.round - G.lastEnemyTowerRound < 60
                && G.rc.getPaint() > 130) {
            mode = ATTACK; return;
        }
        MapLocation ruin = findBestRuinToBuild();
        if (ruin != null) {
            targetRuin = ruin;
            targetType = MapMemory.chooseTowerType();
            markerPlaced = false;
            buildStartRound = G.round;
            mode = GOTO_RUIN;
        }
    }

    static void transitionGotoRuin() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (G.round - buildStartRound > BUILD_TIMEOUT) { abandonBuild(); return; }
        checkRuinStolen();
        if (mode != GOTO_RUIN) return;
        if (G.rc.canSenseLocation(targetRuin)) mode = MARK;
    }

    static void transitionMark() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (G.round - buildStartRound > BUILD_TIMEOUT) { abandonBuild(); return; }
        checkRuinStolen();
        if (mode != MARK) return;
        if (markerPlaced) mode = PAINT;
    }

    static void transitionPaint() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (G.round - buildStartRound > BUILD_TIMEOUT) { abandonBuild(); return; }
        checkRuinStolen();
        if (mode != PAINT) return;
        if (G.me.isWithinDistanceSquared(targetRuin, 20)) {
            for (int k = 0; k < TOWER_TYPES.length; k++) {
                if (G.rc.canCompleteTowerPattern(TOWER_TYPES[k], targetRuin)) {
                    targetType = TOWER_TYPES[k];
                    mode = COMPLETE;
                    return;
                }
            }
        }
    }

    static void transitionComplete() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (!tryCompleteTower()) mode = PAINT;
    }

    static void transitionAttack() throws Exception {
        if (G.lastKnownEnemyTower == null || G.round - G.lastEnemyTowerRound > 80)
            mode = EXPLORE;
    }

    // =========================================================================
    // MODE ACTIONS
    // =========================================================================

    static void doExplore() throws Exception {
        if (exploreTarget == null
                || G.me.isWithinDistanceSquared(exploreTarget, 4)
                || exploreAge > EXPLORE_TIMEOUT) {
            exploreTarget = pickGreedyExploreTarget();
            exploreAge = 0;
        }
        exploreAge++;
        if (G.rc.isMovementReady()) Nav.moveToward(exploreTarget);
        paintNearby();
    }

    static void doGotoRuin() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (G.rc.isMovementReady()) Nav.moveToward(targetRuin);
        paintNearby();
    }

    static void doMark() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (!G.rc.canSenseLocation(targetRuin)) {
            if (G.rc.isMovementReady()) Nav.moveToward(targetRuin);
            paintNearby();
            return;
        }
        if (!markerPlaced) {
            if (G.rc.getPaint() < 30) { paintNearby(); return; }
            if (G.rc.canMarkTowerPattern(targetType, targetRuin)) {
                G.rc.markTowerPattern(targetType, targetRuin);
                markerPlaced = true;
                mode = PAINT;
                return;
            }
            if (G.rc.isMovementReady() && !G.me.isWithinDistanceSquared(targetRuin, 4))
                Nav.moveToward(targetRuin);
            return;
        }
        mode = PAINT;
    }

    static void doPaint() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }

        if (G.rc.isMovementReady()) {
            MapLocation urgent = findUrgentPatternTile();
            if (urgent != null) {
                if (!G.me.isWithinDistanceSquared(urgent, G.rc.getType().actionRadiusSquared))
                    Nav.moveToward(urgent);
            } else {
                if (!G.me.isWithinDistanceSquared(targetRuin, 4))
                    Nav.moveToward(targetRuin);
            }
        }

        if (G.rc.isActionReady()) {
            if (!paintBestPatternTile()) paintNearby();
        }
    }

    static void doComplete() throws Exception {
        if (targetRuin == null) { abandonBuild(); return; }
        if (!G.me.isWithinDistanceSquared(targetRuin, 2)) {
            if (G.rc.isMovementReady()) Nav.moveToward(targetRuin);
            return;
        }
        tryCompleteTower();
    }

    static void doRetreat() throws Exception {
        MapLocation tower = findNearestAllyTowerLoc();
        if (tower == null) {
            if (G.rc.isMovementReady()) Nav.moveToward(G.mapCenter);
            return;
        }
        if (G.rc.isMovementReady()) Nav.moveToward(tower);
        if (G.me.isWithinDistanceSquared(tower, 2)) {
            int need = G.rc.getType().paintCapacity - G.rc.getPaint();
            if (need > 0 && G.rc.canTransferPaint(tower, -need))
                G.rc.transferPaint(tower, -need);
        }
    }

    static void doAttack() throws Exception {
        MapLocation target = G.lastKnownEnemyTower;
        if (target == null) { mode = EXPLORE; return; }
        if (G.rc.isMovementReady()) Nav.moveToward(target);
        if (G.rc.isActionReady()) {
            if (G.me.isWithinDistanceSquared(target, G.rc.getType().actionRadiusSquared)) {
                if (G.rc.canAttack(target)) G.rc.attack(target);
            } else {
                paintNearby();
            }
        }
    }

    // =========================================================================
    // CORE: paintBestPatternTile — THE KEY FIX
    // =========================================================================

    /**
     * Read getMark() per tile and paint with the CORRECT color:
     *
     *   ALLY_PRIMARY   -> attack(tile)        primary color  ("1" on screen)
     *   ALLY_SECONDARY -> attack(tile, true)  secondary color ("2" on screen)
     *
     * Priority:
     *   3 = empty or wrong-ally-color  (paint immediately)
     *   2 = enemy paint, no mopper     (overwrite now)
     *   1 = enemy paint, mopper near   (wait up to 40r, then overwrite)
     *
     * Returns true if an attack was issued.
     */
    static boolean paintBestPatternTile() throws Exception {
        if (targetRuin == null) return false;

        MapLocation bestTile         = null;
        boolean     bestUseSecondary = false;
        int         bestPriority     = -1;
        int         bestDist         = Integer.MAX_VALUE;

        int actionRad    = G.rc.getType().actionRadiusSquared;
        boolean mopperNearby = hasMopperNearby();

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                MapLocation tile = targetRuin.translate(dx, dy);

                if (!G.me.isWithinDistanceSquared(tile, actionRad)) continue;
                if (!G.rc.canSenseLocation(tile))                    continue;
                if (!G.rc.canAttack(tile))                           continue;

                MapInfo   info  = G.rc.senseMapInfo(tile);
                PaintType mark  = info.getMark();
                PaintType paint = info.getPaint();

                if (mark == PaintType.EMPTY) continue; // not a pattern tile

                boolean needSecondary = (mark == PaintType.ALLY_SECONDARY);

                // Skip tiles already correctly painted
                if (needSecondary  && paint == PaintType.ALLY_SECONDARY) continue;
                if (!needSecondary && paint == PaintType.ALLY_PRIMARY)   continue;

                int priority;
                if (paint.isEnemy()) {
                    if (mopperNearby && G.round - buildStartRound < 40)
                        priority = 1; // wait briefly for mopper
                    else
                        priority = 2; // overwrite enemy paint
                } else {
                    priority = 3; // empty or wrong ally color
                }

                int dist = G.me.distanceSquaredTo(tile);
                if (priority > bestPriority
                        || (priority == bestPriority && dist < bestDist)) {
                    bestPriority     = priority;
                    bestDist         = dist;
                    bestTile         = tile;
                    bestUseSecondary = needSecondary;
                }
            }
        }

        if (bestTile == null) return false;

        if (bestUseSecondary) {
            // Paint SECONDARY color — "2" shown on the map
            if (G.rc.canAttack(bestTile)) {
                G.rc.attack(bestTile, true);
                return true;
            }
        } else {
            // Paint PRIMARY color — "1" shown on the map
            if (G.rc.canAttack(bestTile)) {
                G.rc.attack(bestTile);
                return true;
            }
        }
        return false;
    }

    /** Closest unsatisfied pattern tile (to navigate toward). */
    static MapLocation findUrgentPatternTile() throws Exception {
        if (targetRuin == null) return null;
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        boolean mopperNearby = hasMopperNearby();

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                MapLocation tile = targetRuin.translate(dx, dy);
                if (!G.rc.canSenseLocation(tile)) continue;

                MapInfo   info  = G.rc.senseMapInfo(tile);
                PaintType mark  = info.getMark();
                PaintType paint = info.getPaint();

                if (mark == PaintType.EMPTY) continue;

                boolean needSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (needSecondary  && paint == PaintType.ALLY_SECONDARY) continue;
                if (!needSecondary && paint == PaintType.ALLY_PRIMARY)   continue;

                if (paint.isEnemy() && mopperNearby && G.round - buildStartRound < 40) continue;

                int d = G.me.distanceSquaredTo(tile);
                if (d < bestDist) { bestDist = d; best = tile; }
            }
        }
        return best;
    }

    static boolean hasMopperNearby() {
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo r = G.nearbyAllies[i];
            if (r.type == UnitType.MOPPER && G.me.isWithinDistanceSquared(r.location, 8))
                return true;
        }
        return false;
    }

    /** Opportunistically complete any ready tower pattern near us. */
    static void opportunisticComplete() throws Exception {
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            MapLocation ruin = G.nearbyRuins[i];
            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo ri = G.rc.senseRobotAtLocation(ruin);
                if (ri != null) continue;
            }
            for (int k = 0; k < TOWER_TYPES.length; k++) {
                if (!G.rc.canCompleteTowerPattern(TOWER_TYPES[k], ruin)) continue;
                MapLocation sentinel = ruin.add(SENTINEL[k]);
                if (!G.rc.canSenseLocation(sentinel)) continue;
                if (G.rc.senseMapInfo(sentinel).getMark() == PaintType.EMPTY) continue;
                G.rc.completeTowerPattern(TOWER_TYPES[k], ruin);
                MapMemory.registerRuin(ruin, G.team, TOWER_TYPES[k]);
                if (ruin.equals(targetRuin)) abandonBuild();
                break;
            }
        }
    }

    static boolean tryCompleteTower() throws Exception {
        if (targetRuin == null) return false;
        for (int k = 0; k < TOWER_TYPES.length; k++) {
            if (!G.rc.canCompleteTowerPattern(TOWER_TYPES[k], targetRuin)) continue;
            MapLocation sentinel = targetRuin.add(SENTINEL[k]);
            if (G.rc.canSenseLocation(sentinel)) {
                if (G.rc.senseMapInfo(sentinel).getMark() == PaintType.EMPTY) continue;
            }
            G.rc.completeTowerPattern(TOWER_TYPES[k], targetRuin);
            MapMemory.registerRuin(targetRuin, G.team, TOWER_TYPES[k]);
            abandonBuild();
            return true;
        }
        return false;
    }

    // =========================================================================
    // GREEDY HELPERS
    // =========================================================================

    static MapLocation findBestRuinToBuild() throws Exception {
        MapLocation best      = null;
        int         bestScore = Integer.MIN_VALUE;

        for (int i = G.nearbyRuins.length; --i >= 0;) {
            MapLocation ruin = G.nearbyRuins[i];
            if (G.rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo ri = G.rc.senseRobotAtLocation(ruin);
                if (ri != null) continue;
            }
            int enemyPaint = countEnemyPaint(ruin, 12);
            if (enemyPaint > 12) continue;
            int score = 1500 - G.me.distanceSquaredTo(ruin) - enemyPaint * 12;
            if (score > bestScore) { bestScore = score; best = ruin; }
        }

        for (int i = MapMemory.numRuins; --i >= 0;) {
            if (MapMemory.ruinStatus[i] != 0) continue;
            if (G.round - MapMemory.ruinClaimRound[i] < 100) continue;
            int score = 1000 - G.me.distanceSquaredTo(MapMemory.ruinLocs[i]);
            if (score > bestScore) { bestScore = score; best = MapMemory.ruinLocs[i]; }
        }
        return best;
    }

    static int countEnemyPaint(MapLocation loc, int radiusSq) throws Exception {
        int count = 0;
        MapInfo[] area = G.rc.senseNearbyMapInfos(loc, radiusSq);
        for (int j = area.length; --j >= 0;)
            if (area[j].getPaint().isEnemy()) count++;
        return count;
    }

    static MapLocation pickGreedyExploreTarget() throws Exception {
        MapLocation best      = null;
        int         bestScore = -1;

        if (G.round <= TOWER_BUILD_CUTOFF) {
            for (int i = MapMemory.numRuins; --i >= 0;) {
                if (MapMemory.ruinStatus[i] != 0) continue;
                MapLocation ruin = MapMemory.ruinLocs[i];
                int v    = G.getVisited(ruin);
                int dist = G.me.distanceSquaredTo(ruin);
                int score = 250 - (v == 0 ? 0 : Math.min(80, G.round - v)) - dist / 6;
                if (score > bestScore) { bestScore = score; best = ruin; }
            }
        }

        for (int t = 0; t < 8; t++) {
            MapLocation c = new MapLocation(Rand.nextInt(G.mapWidth), Rand.nextInt(G.mapHeight));
            int v    = G.getVisited(c);
            int dist = G.me.distanceSquaredTo(c);
            int score = (v == 0 ? 160 : Math.max(0, G.round - v - 30)) + dist / 8;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best != null ? best
                : new MapLocation(Rand.nextInt(G.mapWidth), Rand.nextInt(G.mapHeight));
    }

    /**
     * Paint best nearby tile for territory expansion.
     *   empty tile under self -> 120
     *   other empty tile      -> 100
     *   enemy tile            -> 80
     *   minus distanceSquared -> prefer closer
     */
    static void paintNearby() throws Exception {
        if (!G.rc.isActionReady()) return;

        MapLocation best      = null;
        int         bestScore = 0;

        for (MapInfo info : G.nearbyMapInfos) {
            MapLocation loc = info.getMapLocation();
            if (!G.me.isWithinDistanceSquared(loc, G.rc.getType().actionRadiusSquared)) continue;
            if (!G.rc.canAttack(loc)) continue;
            if (info.isWall() || info.hasRuin()) continue;

            PaintType paint = info.getPaint();
            int score;
            if (paint == PaintType.EMPTY)  score = loc.equals(G.me) ? 120 : 100;
            else if (paint.isEnemy())      score = 80;
            else                           continue;

            score -= G.me.distanceSquaredTo(loc);
            if (score > bestScore) { bestScore = score; best = loc; }
        }
        if (best != null) G.rc.attack(best);
    }

    // ── Support ───────────────────────────────────────────────────────────────

    static void tryCompleteResourcePatterns() throws Exception {
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = G.me.translate(dx, dy);
                if (G.rc.canCompleteResourcePattern(loc)) G.rc.completeResourcePattern(loc);
            }
    }

    static void tryWithdrawPaint() throws Exception {
        if (G.rc.getPaint() >= (int)(G.rc.getType().paintCapacity * 0.88)) return;
        if (!G.rc.isActionReady()) return;
        for (int i = G.nearbyAllies.length; --i >= 0;) {
            RobotInfo ally = G.nearbyAllies[i];
            if (ally.type.isRobotType()) continue;
            if (ally.type.getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER) continue;
            if (!G.me.isWithinDistanceSquared(ally.location, 2)) continue;
            int need = G.rc.getType().paintCapacity - G.rc.getPaint();
            if (need > 0 && G.rc.canTransferPaint(ally.location, -need)) {
                G.rc.transferPaint(ally.location, -need);
                return;
            }
        }
    }

    static MapLocation findNearestAllyTowerLoc() {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = MapMemory.numRuins; --i >= 0;) {
            if (MapMemory.ruinTeams[i] != G.team || MapMemory.ruinTypes[i] == null) continue;
            boolean isPaint = MapMemory.ruinTypes[i].getBaseType()
                    == UnitType.LEVEL_ONE_PAINT_TOWER;
            int d = G.me.distanceSquaredTo(MapMemory.ruinLocs[i]) + (isPaint ? 0 : 400);
            if (d < bestDist) { bestDist = d; best = MapMemory.ruinLocs[i]; }
        }
        return best;
    }

    static void checkRuinStolen() throws Exception {
        if (targetRuin == null || !G.rc.canSenseLocation(targetRuin)) return;
        if (!G.rc.canSenseRobotAtLocation(targetRuin)) return;
        RobotInfo ri = G.rc.senseRobotAtLocation(targetRuin);
        if (ri == null) return;
        if (ri.team == G.opponentTeam) {
            G.lastKnownEnemyTower = targetRuin;
            G.lastEnemyTowerRound = G.round;
            abandonBuild();
            mode = ATTACK;
        } else if (ri.team == G.team) {
            MapMemory.registerRuin(targetRuin, G.team, ri.type);
            abandonBuild();
        }
    }

    static void abandonBuild() {
        targetRuin = null; targetType = null; markerPlaced = false;
        mode = EXPLORE;
    }

    static String modeStr() {
        return switch (mode) {
            case EXPLORE   -> "EXP";
            case GOTO_RUIN -> "GTO";
            case MARK      -> "MRK";
            case PAINT     -> "PNT";
            case COMPLETE  -> "CMP";
            case RETREAT   -> "RET";
            case ATTACK    -> "ATK";
            default        -> "?";
        };
    }
}