package bot_3;

import battlecode.common.*;

public class Robot {
    public static void runSoldier() throws GameActionException {
        Logistics.tryWithdrawAdjacent();

        if (TowerBuilder.tryTowerRoutine()) return;

        if (Logistics.needRefillSoldier()) { Logistics.goRefill(); return; }

        if (G.rc.isActionReady() && Greedy.tryAttackEnemyTower()) {
            Greedy.tryExpandMove();
            return;
        }

        boolean moved = Greedy.tryExpandMove();
        if (!moved) {
            moved = Motion.moveToward(G.pushTarget, true);
            if (!moved) Motion.safeFallbackMove(true);
        }

        if (G.rc.isActionReady()) {
            Logistics.tryWithdrawAdjacent();
            if (!Greedy.tryAttackEnemyRobotEfficient())
                if (!Greedy.tryAttackEnemyTower())
                    if (!TowerBuilder.tryTowerRoutine())
                        Greedy.tryBestPaintAttack();
        }
    }

    public static void runMopper() throws GameActionException {
        if (Logistics.tryTransferPaintToAlly()) return;

        if (Logistics.needRefillMopper()) { Logistics.goRefill(); return; }

        if (G.rc.isActionReady() && Greedy.tryCleanEnemyPaint()) return;

        if (Motion.followWeakAlly()) return;

        if (G.nearbyEnemyLen > 0) {
            MapLocation closest = null; int best = Integer.MAX_VALUE;
            for (int i = G.nearbyEnemyLen; --i >= 0;) {
                int d = G.me.distanceSquaredTo(G.nearbyEnemies[i].location);
                if (d < best) { best = d; closest = G.nearbyEnemies[i].location; }
            }
            if (closest != null && Motion.moveToward(closest, false)) return;
        }

        if (Motion.moveToward(G.pushTarget, false)) return;
        Motion.safeFallbackMove(false);
    }

    public static void runSplasher() throws GameActionException {
        Logistics.tryWithdrawAdjacent();
        if (Logistics.needRefillSplasher()) { Logistics.goRefill(); return; }
        if (G.rc.isActionReady() && Greedy.tryAttackEnemyTower()) return;
        if (G.rc.isActionReady() && Greedy.tryBestSplashAttack()) return;

        boolean moved = Greedy.tryContestMove();
        if (!moved) moved = Motion.moveToward(G.pushTarget, false);
        if (!moved) { Motion.safeFallbackMove(false); return; }

        if (G.rc.isActionReady()) {
            Logistics.tryWithdrawAdjacent();
            if (!Greedy.tryAttackEnemyTower()) Greedy.tryBestSplashAttack();
        }
    }
}