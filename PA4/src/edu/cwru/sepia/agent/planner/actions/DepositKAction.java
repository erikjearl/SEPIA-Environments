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
public class DepositKAction implements StripsAction{

    private final ArrayList<Peasant> peasants;
    private final Position townhallPos;
    private final GameState parent;

    /**
     * @param peasants the units carrying resources.
     * @param parent the GameState where the action occurs.
     */
    public DepositKAction(ArrayList<Peasant> peasants, GameState parent){
        this.peasants = peasants;
        this.townhallPos = parent.getTownhallPosition();
        this.parent = parent;
    }

    /**
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    @Override
    public boolean preconditionsMet(GameState state) {
        for(Peasant p : peasants) {
            if (!p.hasResource() || p.getPosition().equals(townhallPos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param state State to apply action to
     */
    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyDepositKAction(peasants); //FIXME maybe use IDs if broken);
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
        for(int i = 0; i <peasants.size(); i++){
            sepiaActions.add(Action.createPrimitiveDeposit(peasants.get(i).getId(), findDirection(peasantUnitView)));
        }
        return sepiaActions;
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }

}