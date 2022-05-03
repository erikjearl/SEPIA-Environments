package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.resources.Resource;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * This action can be depended upon to pull its weight.
 */
public class HarvestKAction implements StripsAction{

    private final ArrayList<Peasant> peasants;
    private final Resource resource;
    private final Position resourcePos;
    private final GameState parent;

    /**
     * @param peasants the units doing the harvesting.
     * @param resource the resource being harvested.
     * @param parent the GameState where the action occurs.
     */
    public HarvestKAction(ArrayList<Peasant> peasants, Resource resource, GameState parent) {
        this.peasants = peasants;
        this.resource = resource;
        this.resourcePos = resource.getPosition();
        this.parent = parent;
    }

    /**
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    @Override
    public boolean preconditionsMet(GameState state) {
        if(resource.getAmount() <= 0) return false;
        for(Peasant p : peasants){
            if(p.hasResource() || p.getPosition().equals(resourcePos)){
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
        state.applyHarvestKAction(peasants, resource); //FIXME maybe use IDs if broken);
    }

    /**
     * @param peasantUnitView a point of reference.
     * @return a direction for the next move.
     */
    public Direction findDirection(Unit.UnitView peasantUnitView) {
        Direction direction = findDirection(
                peasantUnitView.getXPosition(),
                peasantUnitView.getYPosition(),
                resourcePos.x, resourcePos.y
        );
        return direction;
    }

    @Override
    public List<Action> createSepiaAction(Unit.UnitView peasantUnitView){
        List<Action> sepiaActions = new ArrayList<Action>();
        for(int i = 0; i <peasants.size(); i++){
            sepiaActions.add(Action.createPrimitiveGather(peasants.get(i).getId(), findDirection(peasantUnitView)));
        }
        return sepiaActions;
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }

}