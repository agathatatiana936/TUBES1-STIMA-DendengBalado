package main;

import battlecode.common.*;
import java.util.Arrays;

public class Tower {

    static final int PHASE2_ROUND = 601; // splasher flood starts here

    static final double W_SOLDIER_BASE  = 1.6;
    static final double W_SPLASHER_BASE = 0.5;
    static final double W_MOPPER_BASE   = 0.9;

    static double dSoldier   = 0;
    static double dSplasher  = 0;
    static double dMopper    = 0;

    static int spawnedTotal     = 0;
    static int spawnedSoldiers  = 0;
    static int spawnedSplashers = 0;
    static int spawnedMoppers   = 0;
    static int lastSpawnRound   = -1;

    static MapLocation[] spawnLocs;
    static int towerLevel = 0;

    public static void init() throws Exception {
        spawnLocs = buildSpawnLocs();
        MapMemory.registerRuin(G.me, G.team, G.rc.getType());
    }

    public static void run() throws Exception {
        MapMemory.scanNearby();

        boolean isPhase2 = (G.round >= PHASE2_ROUND);

        UnitType toSpawn = isPhase2 ? decideSpawnPhase2() : decideSpawnPhase1();

        boolean shouldSpawn = shouldSpawn(toSpawn, isPhase2);

        if (shouldSpawn && G.rc.isActionReady()) {
            if (trySpawn(toSpawn)) lastSpawnRound = G.round;
        }

        doAttack();

        tryUpgrade();

        G.indicator.append("TOW ph=" + (isPhase2 ? "2" : "1")
                + " spn=" + spawnedTotal
                + " chips=" + G.rc.getMoney() + " ");
    }

    // spawn apa?
    static UnitType decideSpawnPhase1() {
        if (G.round < 50 && spawnedTotal < 5) return UnitType.SOLDIER;

        double wS  = W_SOLDIER_BASE;
        double wSp = W_SPLASHER_BASE;
        double wM  = W_MOPPER_BASE;

        wSp += MapMemory.allyPaintTowers * 0.35;
        wS  -= Math.min(1.0, G.rc.getNumberTowers() * 0.04);
        if (MapMemory.allyMoneyTowers >= 2) wSp += 0.4;

        double sum = Math.max(0.01, wS + wSp + wM);
        wS /= sum; wSp /= sum; wM /= sum;

        double scoreS  = dSoldier  + wS  - spawnedSoldiers;
        double scoreSp = dSplasher + wSp - spawnedSplashers;
        double scoreM  = dMopper   + wM  - spawnedMoppers;

        if (scoreS >= scoreSp && scoreS >= scoreM) return UnitType.SOLDIER;
        if (scoreSp >= scoreM)                      return UnitType.SPLASHER;
        return UnitType.MOPPER;
    }

    static UnitType decideSpawnPhase2() {
        if ((spawnedTotal % 7) == 6) return UnitType.MOPPER;
        return UnitType.SPLASHER;
    }

    static boolean shouldSpawn(UnitType toSpawn, boolean isPhase2) {
        int chips  = G.rc.getMoney();
        int towers = G.rc.getNumberTowers();
        int cost   = toSpawn.moneyCost;

        if (G.round < 10)    return true; 
        if (towers >= 25)    return chips >= cost; 
        if (chips < cost)    return false;

        if (isPhase2) {
            return true;
        }

        if (chips - cost >= 600)    return true;
        if (G.round < 80)            return true; 
        if (lastSpawnRound + 2 < G.round && G.nearbyAllies.length < 3) return true;

        return false;
    }
    
    static boolean trySpawn(UnitType type) throws Exception {
        for (MapLocation loc : spawnLocs) {
            if (G.rc.canBuildRobot(type, loc)) {
                G.rc.buildRobot(type, loc);
                spawnedTotal++;
                switch (type) {
                    case SOLDIER:  spawnedSoldiers++;  dSoldier++;  break;
                    case SPLASHER: spawnedSplashers++; dSplasher++; break;
                    case MOPPER:   spawnedMoppers++;   dMopper++;   break;
                    default: break;
                }
                return true;
            }
        }
        return false;
    }

    static void doAttack() throws Exception {
        if (G.rc.canAttack(null)) G.rc.attack(null); // AoE always

        MapLocation bestTarget = null;
        int         bestScore  = Integer.MIN_VALUE;
        int         atkStr     = G.rc.getType().attackStrength;

        for (int i = G.nearbyEnemies.length; --i >= 0;) {
            RobotInfo r = G.nearbyEnemies[i];
            if (!G.me.isWithinDistanceSquared(r.location, G.rc.getType().actionRadiusSquared)) continue;
            if (!G.rc.canSenseRobotAtLocation(r.location)) continue;

            int score = (r.health <= atkStr) ? 10000 - r.health : 1000 - r.health;
            if (score > bestScore) { bestScore = score; bestTarget = r.location; }
        }

        if (bestTarget != null && G.rc.canAttack(bestTarget))
            G.rc.attack(bestTarget);
    }

    static void tryUpgrade() throws Exception {
        int upgradeCost = (towerLevel == 0) ? 2500 : 5000;
        int buffer = (G.round >= PHASE2_ROUND) ? 400 : 1000;
        if (G.rc.getMoney() - upgradeCost >= buffer && G.rc.canUpgradeTower(G.me)) {
            G.rc.upgradeTower(G.me);
            towerLevel++;
        }
    }

    static MapLocation[] buildSpawnLocs() {
        MapLocation[] locs = {
            G.me.add(Direction.NORTH),
            G.me.add(Direction.NORTHEAST),
            G.me.add(Direction.EAST),
            G.me.add(Direction.SOUTHEAST),
            G.me.add(Direction.SOUTH),
            G.me.add(Direction.SOUTHWEST),
            G.me.add(Direction.WEST),
            G.me.add(Direction.NORTHWEST),
            G.me.add(Direction.NORTH).add(Direction.NORTH),
            G.me.add(Direction.EAST).add(Direction.EAST),
            G.me.add(Direction.SOUTH).add(Direction.SOUTH),
            G.me.add(Direction.WEST).add(Direction.WEST),
        };
        final MapLocation center = G.mapCenter;
        Arrays.sort(locs, (a, b) -> b.distanceSquaredTo(center) - a.distanceSquaredTo(center));
        return locs;
    }
}