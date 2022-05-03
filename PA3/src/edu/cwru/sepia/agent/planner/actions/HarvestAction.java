package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.resources.Resource;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

/**
 * This action can be depended upon to pull its weight.
 */
public class HarvestAction implements StripsAction{

    private final Peasant peasant;
    private final Resource resource;
    private final Position peasantPos;
    private final Position resourcePos;
    private final GameState parent;

    /**
     * @param peasant the unit doing the harvesting.
     * @param resource the resource being harvested.
     * @param parent the GameState where the action occurs.
     */
    public HarvestAction(Peasant peasant, Resource resource, GameState parent) {
        this.peasant = peasant;
        this.resource = resource;
        this.peasantPos = peasant.getPosition();
        this.resourcePos = resource.getPosition();
        this.parent = parent;
    }

    /**
     * @param state GameState to check if action is applicable
     * @return true if apply can be called, false otherwise
     */
    @Override
    public boolean preconditionsMet(GameState state) {
        return resource.getAmount() != 0 && !peasant.hasResource() && peasantPos.equals(resourcePos);
    }

    /**
     * @param state State to apply action to
     */
    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyHarvestAction(resource);
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
    public Action createSepiaAction(Unit.UnitView peasantUnitView){
        return Action.createPrimitiveGather(peasant.getId(), findDirection(peasantUnitView));
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }

}