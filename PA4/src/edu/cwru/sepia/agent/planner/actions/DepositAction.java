package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * This action allows making deposits of resources.
 */
public class DepositAction implements StripsAction{

    private final Peasant peasant;
    private final Position townhallPos;
    private final GameState parent;

    /**
     * @param peasant the unit carrying resources.
     * @param parent the GameState where the action occurs.
     */
    public DepositAction(Peasant peasant, GameState parent){
        this.peasant = peasant;
        this.townhallPos = parent.getTownhallPosition();
        this.parent = parent;
    }

    /**
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    @Override
    public boolean preconditionsMet(GameState state) {
        return peasant.hasResource() && peasant.getPosition().equals(townhallPos);
    }

    /**
     * @param state State to apply action to
     */
    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyDepositAction();
    }

    public Direction findDirection(Unit.UnitView peasantUnitView) {
        return findDirection(
                peasantUnitView.getXPosition(),
                peasantUnitView.getYPosition(),
                townhallPos.x, townhallPos.y
        );
    }

    @Override
    public List<Action> createSepiaAction(Unit.UnitView peasantUnitView){
        List<Action> sepiaActions = new ArrayList<Action>();
        sepiaActions.add(Action.createPrimitiveDeposit(peasant.getId(), findDirection(peasantUnitView)));
        return sepiaActions;
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }

}