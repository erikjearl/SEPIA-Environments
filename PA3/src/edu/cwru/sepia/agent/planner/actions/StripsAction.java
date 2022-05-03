package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

/**
 * A useful start of an interface representing strips actions. You may add new methods to this interface if needed, but
 * you should implement the ones provided. You may also find it useful to specify a method that returns the effects
 * of a StripsAction.
 */
public interface StripsAction {

    Direction[][] directionMatrix = {
            {Direction.NORTHWEST, Direction.NORTH, Direction.NORTHEAST},
            {Direction.WEST,      null,            Direction.EAST},
            {Direction.SOUTHWEST, Direction.SOUTH, Direction.SOUTHEAST}
    };

    default Direction findDirection(int fromX, int fromY, int toX, int toY) {
        int xDiff = toX - fromX;
        int yDiff = toY - fromY;

        if(Math.abs(xDiff) <= 1 && Math.abs(yDiff) <= 1) {
            return directionMatrix[yDiff + 1][xDiff + 1];
        }else{
            return null;
        }
    }

    /**
     * Returns true if the provided GameState meets all of the necessary conditions for this action to successfully
     * execute.
     *
     * As an example consider a Move action that moves peasant 1 in the NORTH direction. The partial game state might
     * specify that peasant 1 is at location (3, 3). In this case the game state shows that nothing is at location (3, 2)
     * and (3, 2) is within bounds. So the method returns true.
     *
     * If the peasant were at (3, 0) this method would return false because the peasant cannot move to (3, -1).
     *
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    boolean preconditionsMet(GameState state);

    /**
     * Applies the action instance to the given GameState producing a new GameState in the process.
     *
     * As an example consider a Move action that moves peasant 1 in the NORTH direction. The partial game state
     * might specify that peasant 1 is at location (3, 3). The returned GameState should specify
     * peasant 1 at location (3, 2).
     *
     * In the process of updating the peasant state you should also update the GameState's cost and parent pointers.
     *
     * @param state State to apply action to
     */
    void apply(GameState state);

    Action createSepiaAction(Unit.UnitView peasantUnitView);

    default double getCost() {
        return 0;
    }

    GameState getParent();
}