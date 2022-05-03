package edu.cwru.sepia.agent.planner;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.agent.planner.actions.*;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This is an outline of the PEAgent. Implement the provided methods. You may add your own methods and members.
 */
public class PEAgent extends Agent {

    private final Stack<StripsAction> plan;

    public PEAgent(int playernum, Stack<StripsAction> plan) {
        super(playernum);
        this.plan = plan;
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView stateView, History.HistoryView historyView) {
        return middleStep(stateView, historyView);
    }

    /**
     * This is where you will read the provided plan and execute it. If your plan is correct then when the plan is empty
     * the scenario should end with a victory. If the scenario keeps running after you run out of actions to execute
     * then either your plan is incorrect or your execution of the plan has a bug.
     *
     * For the compound actions you will need to check their progress and wait until they are complete before issuing
     * another action for that unit. If you issue an action before the compound action is complete then the peasant
     * will stop what it was doing and begin executing the new action.
     *
	 * To check a unit's progress on the action they were executing last turn, you can use the following:
     * historyView.getCommandFeedback(playernum, stateView.getTurnNumber() - 1).get(unitID).getFeedback()
     * This returns an enum ActionFeedback. When the action is done, it will return ActionFeedback.COMPLETED
     *
     * Alternatively, you can see the feedback for each action being executed during the last turn. Here is a short example.
     * if (stateView.getTurnNumber() != 0) {
     *   Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(playernum, stateView.getTurnNumber() - 1);
     *   for (ActionResult result : actionResults.values()) {
     *     <stuff>
     *   }
     * }
     * Also remember to check your plan's preconditions before executing!
     */
    @Override
    public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
        Map<Integer, Action> actionMap = new HashMap<>();

        if(stateView.getTurnNumber() > 0) {
            Map<Integer, ActionResult> previousActions = historyView.getCommandFeedback(playernum, stateView.getTurnNumber() - 1);
            for(ActionResult previousAction : previousActions.values()) {
                if(previousAction.getFeedback() == ActionFeedback.FAILED) {
                    System.out.println("Action failed: " + previousAction);
                    System.exit(1);
                } else if(previousAction.getFeedback() == ActionFeedback.INCOMPLETE) {
                    return actionMap;
                }
            }
        }

        StripsAction nextAction = plan.pop();
        Unit.UnitView peasantUnitView = stateView.getUnit(nextAction.getParent().getPeasantId());
        Action sepiaAction = createSepiaAction(nextAction, peasantUnitView);
        actionMap.put(sepiaAction.getUnitId(), sepiaAction);
        return actionMap;
    }

    /**
     * Returns a SEPIA version of the specified Strips Action.
     *
     * You can create a SEPIA deposit action with the following method
     * Action.createPrimitiveDeposit(int peasantId, Direction townhallDirection)
     *
     * You can create a SEPIA harvest action with the following method
     * Action.createPrimitiveGather(int peasantId, Direction resourceDirection)
     *
     * You can create a SEPIA build action with the following method
     * Action.createPrimitiveProduction(int townhallId, int peasantTemplateId)
     *
     * You can create a SEPIA move action with the following method
     * Action.createCompoundMove(int peasantId, int x, int y)
     *
     * Hint:
     * peasantId could be found in peasantIdMap
     *
     * these actions are stored in a mapping between the peasant unit ID executing the action and the action you created.
     *
     * @param action StripsAction
     * @return SEPIA representation of same action
     */
    private Action createSepiaAction(StripsAction action, Unit.UnitView peasantUnitView) {
        return action.createSepiaAction(peasantUnitView);
    }

    @Override
    public void terminalStep(State.StateView stateView, History.HistoryView historyView) {}

    @Override
    public void savePlayerData(OutputStream outputStream) {}

    @Override
    public void loadPlayerData(InputStream inputStream) {}

}