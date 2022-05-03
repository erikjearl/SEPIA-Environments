package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.agent.planner.resources.Resource;
import edu.cwru.sepia.environment.model.state.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * This action makes moves.
 *
 */
public class MoveAction implements StripsAction{

    private final Peasant peasant;
    private final Position position;
    private final GameState parent;

    /**
     * @param peasant the unit making moves.
     * @param position the destination coordinates.
     * @param parent the GameState where the action occurs.
     */
    public MoveAction(Peasant peasant, Position position, GameState parent) {
        this.peasant = peasant;
        this.position = position;
        this.parent = parent;
    }

    /**
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    @Override
    public boolean preconditionsMet(GameState state) {
        return !peasant.getPosition().equals(position);
    }

    public boolean preconditionsMet(GameState state, Resource resource){
        return preconditionsMet(state) && resource.getAmount() > 0 &&
                resource.isGold() ? state.getAmountGold() < state.getRequiredGold() :
                    state.getAmountWood() < state.getRequiredWood();
    }

    /**
     * @param state State to apply action to
     */
    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyMoveAction(position);
    }

    @Override
    public List<Action> createSepiaAction(Unit.UnitView peasantUnitView){
        List<Action> sepiaActions = new ArrayList<Action>();
        sepiaActions.add(Action.createCompoundMove(peasant.getId(),position.x, position.y));
        return sepiaActions;
    }

    @Override
    public double getCost(){
        return Math.sqrt(
                Math.pow(position.x - peasant.getPosition().x, 2) +
                        Math.pow(position.y - peasant.getPosition().y, 2)
        );
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }

}