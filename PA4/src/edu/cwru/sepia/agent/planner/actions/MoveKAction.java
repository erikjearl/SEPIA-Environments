package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Peasant;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.environment.model.state.Unit;

import java.util.ArrayList;
import java.util.List;

public class MoveKAction implements StripsAction{

    ArrayList<Peasant> peasants;
    Position position;
    GameState parent;

    public MoveKAction(ArrayList<Peasant> peasants, Position position, GameState parent){
        this.peasants = peasants;
        this.position = position;
        this.parent = parent;
    }

    @Override
    public boolean preconditionsMet(GameState state) {
        for(Peasant p : peasants){
            if(p.getPosition().equals(position)) { //FIXME we use different preconditions in normal move????
                return false;
            }
        }
        return true;
    }

    @Override
    public void apply(GameState state) {
        state.addPlan(this);
        state.applyMoveKAction(peasants, position); //FIXME maybe use peasant IDs if broken
    }

    @Override
    public List<Action> createSepiaAction(Unit.UnitView peasantUnitView) {
        List<Action> sepiaActions = new ArrayList<Action>();
        for(int i = 0; i <peasants.size(); i++){
            sepiaActions.add(Action.createCompoundMove(peasants.get(i).getId(),position.x,position.y));
        }
        return sepiaActions;
    }

    @Override
    public GameState getParent(){
        return this.parent;
    }
}
