package alternative_bot_2;

import battlecode.common.*;
import java.util.Random;

public class G {
    public static RobotController rc;

    public static Team team;
    public static Team opponent;

    public static MapLocation me;
    public static MapLocation prevLoc;
    public static MapLocation prevPrevLoc;

    public static Direction lastMoveDir = Direction.CENTER;

    public static int round;
    public static int paint;
    public static int chips;
    public static UnitType myType;
    public static boolean  isRobotUnit;

    public static RobotInfo[] nearbyAllies  = new RobotInfo[0];
    public static RobotInfo[] nearbyEnemies = new RobotInfo[0];
    public static MapInfo[]   nearbyTiles   = new MapInfo[0];
    public static int nearbyAllyLen, nearbyEnemyLen, nearbyTileLen;

    public static MapLocation lastSeenAllyTower  = null;
    public static MapLocation lastSeenEnemyTower = null;

    public static MapLocation inferredEnemyBase  = null;
    public static MapLocation spawnBase          = null;

    public static MapLocation pushTarget    = null;
    public static int         pushTurnsLeft = 0;

    public static final Random rng = new Random(12345);

    public static final int           HIST       = 8;
    public static final MapLocation[] recentLocs = new MapLocation[HIST];
    public static int histPtr       = 0;
    public static int lastHistRound = -1;

    public static boolean[][] moneyPattern;
    public static boolean[][] paintPattern;
    public static boolean[][] defensePattern;

    public static final Direction[] DIRS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static void init(RobotController controller) throws GameActionException {
        rc          = controller;
        team        = rc.getTeam();
        opponent    = team.opponent();
        me          = rc.getLocation();
        myType      = rc.getType();
        isRobotUnit = (myType == UnitType.SOLDIER
                    || myType == UnitType.MOPPER
                    || myType == UnitType.SPLASHER);

        moneyPattern   = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
        paintPattern   = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        defensePattern = rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER);

        recentLocs[0] = me;
        histPtr       = 1;
        lastHistRound = rc.getRoundNum();

        if (isRobotUnit) {
            RobotInfo[] initAllies = rc.senseNearbyRobots(-1, team);
            for (int i = initAllies.length; --i >= 0;) {
                if (Util.isTower(initAllies[i].type)) {
                    spawnBase = initAllies[i].location;
                    lastSeenAllyTower = spawnBase;
                    break;
                }
            }
        } else {
            spawnBase = me;
            lastSeenAllyTower = me;
        }

        if (spawnBase != null) {
            inferredEnemyBase = Util.inferEnemyBase(spawnBase);
        }

        pushTarget    = (inferredEnemyBase != null) ? inferredEnemyBase : Util.pickFrontierLikeTarget();
        pushTurnsLeft = 60;
    }

    public static void update() throws GameActionException {
        MapLocation cur = rc.getLocation();
        if (!cur.equals(me)) {
            prevPrevLoc = prevLoc;
            prevLoc     = me;
        }
        me = cur;

        round = rc.getRoundNum();
        paint = rc.getPaint();
        chips = rc.getChips();

        nearbyAllies   = rc.senseNearbyRobots(-1, team);
        nearbyEnemies  = rc.senseNearbyRobots(-1, opponent);
        nearbyTiles    = rc.senseNearbyMapInfos();
        nearbyAllyLen  = nearbyAllies.length;
        nearbyEnemyLen = nearbyEnemies.length;
        nearbyTileLen  = nearbyTiles.length;

        rememberTowers();
        updatePushTarget();

        if (round != lastHistRound) {
            recentLocs[histPtr & (HIST - 1)] = me;
            histPtr++;
            lastHistRound = round;
        }
    }

    static void rememberTowers() {
        for (int i = nearbyAllyLen;  --i >= 0;) { RobotInfo r = nearbyAllies[i];  if (Util.isTower(r.type)) lastSeenAllyTower  = r.location; }
        for (int i = nearbyEnemyLen; --i >= 0;) {
            RobotInfo r = nearbyEnemies[i];
            if (Util.isTower(r.type)) {
                lastSeenEnemyTower = r.location;
                inferredEnemyBase  = r.location;
            }
        }
    }

    static void updatePushTarget() {
        boolean needNew = (pushTarget == null)
            || (pushTurnsLeft <= 0)
            || (me.distanceSquaredTo(pushTarget) <= 9)
            || (prevLoc != null && prevPrevLoc != null && me.equals(prevPrevLoc));

        if (needNew) {
            if (lastSeenEnemyTower != null) {
                pushTarget    = lastSeenEnemyTower;
                pushTurnsLeft = 30;
            } else if (inferredEnemyBase != null) {
                pushTarget    = inferredEnemyBase;
                pushTurnsLeft = 40;
            } else {
                pushTarget    = Util.pickFrontierLikeTarget();
                pushTurnsLeft = 22;
            }
        } else {
            pushTurnsLeft--;
        }
    }

    public static int timesVisited(MapLocation loc) {
        int cnt = 0;
        for (int i = HIST; --i >= 0;)
            if (recentLocs[i] != null && recentLocs[i].equals(loc)) cnt++;
        return cnt;
    }

    public static int paintPct() {
        int cap = myType.paintCapacity;
        return cap <= 0 ? 100 : paint * 100 / cap;
    }
}