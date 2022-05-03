package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.environment.model.state.Unit;

import java.util.ArrayList;
import java.util.List;

public class BuildPeasant implements StripsAction{
    int townhallId;
    GameState parent;

    public BuildPeasant(int townhallId, GameState parent){
        this.townhallId = townhallId;
        this.parent = parent;
    }

    @Override
    public boolean preconditionsMet(GameState state) {
        int gold = state.getAmountGold();
        int food = state.getRemainingFood();
        return gold >= 400 & food > 0;
    }

    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyBuildAction();
    }

    @Override
    public List<Action> createSepiaAction(Unit.UnitView peasantUnitView) {
        List<Action> sepiaActions = new ArrayList<>();
        sepiaActions.add(Action.createPrimitiveProduction(townhallId, parent.getNewPeasantCreationID()));
        return sepiaActions;
    }

    @Override
    public GameState getParent() {
        return this.parent;
    }
}
