package alternative_bot_2;

import battlecode.common.*;

public class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        G.init(rc);
        while (true) {
            try {
                G.update();
                switch (rc.getType()) {
                    case SOLDIER:  Robot.runSoldier();  break;
                    case MOPPER:   Robot.runMopper();   break;
                    case SPLASHER: Robot.runSplasher(); break;
                    default:       Tower.run();         break;
                }
            } catch (Exception e) {
                System.out.println("Err:" + e.getMessage());
            }
            Clock.yield();
        }
    }
}