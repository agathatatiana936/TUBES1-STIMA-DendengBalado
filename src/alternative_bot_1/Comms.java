package alternative_bot_1;

import battlecode.common.*;

public class Comms {

    static final int TYPE_ENEMY_TOWER = 0;

    static MapLocation enemyTowerLoc = null;

    static int encode(int type, MapLocation loc) {
        return (type << 12) | (loc.x << 6) | loc.y;
    }

    static int decodeType(int b) {
        return (b >> 12) & 0x3;
    }

    static MapLocation decodeLoc(int b) {
        return new MapLocation((b >> 6) & 0x3F, b & 0x3F);
    }

    static void startTurn(RobotController rc) throws GameActionException {
        Motion.addtoQueue(rc.getLocation());
        for (Message m : rc.readMessages(-1)) {
            int b = m.getBytes();
            if (decodeType(b) == TYPE_ENEMY_TOWER) {
                enemyTowerLoc = decodeLoc(b);
            }
        }
    }

    static void broadcast(RobotController rc, int bytes) throws GameActionException {
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (rc.canSendMessage(ally.getLocation(), bytes))
                rc.sendMessage(ally.getLocation(), bytes);
        }
    }

    static void reportEnemyTower(RobotController rc, MapLocation loc) throws GameActionException {
        if (loc == null) return;
        enemyTowerLoc = loc;
        broadcast(rc, encode(TYPE_ENEMY_TOWER, loc));
    }

    static void informNewSpawn(RobotController rc, MapLocation spawnLoc) throws GameActionException {
        if (enemyTowerLoc == null) return;
        int msg = encode(TYPE_ENEMY_TOWER, enemyTowerLoc);
        if (rc.canSendMessage(spawnLoc, msg))
            rc.sendMessage(spawnLoc, msg);
    }  
}
