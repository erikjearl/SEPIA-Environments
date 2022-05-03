package edu.cwru.sepia.agent;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.history.DamageLog;
import edu.cwru.sepia.environment.model.history.DeathLog;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;

import java.io.*;
import java.util.*;

public class RLAgent extends Agent {

    /**
     * Set in the constructor. Defines how many learning episodes your agent should run for.
     * When starting an episode. If the count is greater than this value print a message
     * and call sys.exit(0)
     */
    public final int numEpisodes;

    /**
     * List of your footmen and your enemies footmen
     */
    private List<Integer> myFootmen;
    private List<Integer> enemyFootmen;

    /**
     * Convenience variable specifying enemy agent number. Use this whenever referring
     * to the enemy agent. We will make sure it is set to the proper number when testing your code.
     */
    public static final int ENEMY_PLAYERNUM = 1;

    /** Use this random number generator for your epsilon exploration. When you submit we will
     * change this seed so make sure that your agent works for more than the default seed.
     */
    public final Random random = new Random(12345);

    /**
     * Your Q-function weights.
     */
    public Double[] weights;

    /**
     * These variables are set for you according to the assignment definition. You can change them,
     * but it is not recommended. If you do change them please let us know and explain your reasoning for
     * changing them.
     */
    public final double gamma = 0.9;
    public final double learningRate = 0.0001;
    public final double epsilon = 0.02;

    /**
     * Our list of state feature runnables.
     */
    public ArrayList<StateFeature> stateFeatures;

    private static final int NUM_LEARNING_EPISODES = 10;
    private static final int NUM_EVALUATION_EPISODES = 5;

    private int currentEpisode = 0;
    private int numEpisodesThisSeason = 0;
    private boolean currentlyEvaluating = true;

    public final Map<Integer, List<Double>> rewards = new HashMap<>();
    private final List<Double> averageRewards = new ArrayList<>(10);
    private double cumulativeReward = 0.0;

    private boolean killPointsAwarded;
    private static final double KILL_REWARD = 100.0;

    private State.StateView previousStateView;

    /**
     * constructor for RLAgent object
     *
     * @param playernum number of game player
     * @param args array of episodes and load weights
     */
    public RLAgent(int playernum, String[] args) {
        super(playernum);

        stateFeatures = new ArrayList<>(10);
        stateFeatures.add(this::featureAttackerHP);
        stateFeatures.add(this::featureDefenderHP);
        stateFeatures.add(this::featureHPDifference);
        stateFeatures.add(this::featureDistanceToEnemy);
        stateFeatures.add(this::featureNextToEnemy);
        stateFeatures.add(this::featureContinueAttacking);
        stateFeatures.add(this::featureGangUp);
        stateFeatures.add(this::featureGoLow);
        stateFeatures.add(this::featureGettingAttacked);
        stateFeatures.add(this::featureUnobstructedPath);

        if (args.length >= 1) {
            numEpisodes = Integer.parseInt(args[0]);
            System.out.println("Running " + numEpisodes + " episodes.");
        } else {
            numEpisodes = 10;
            System.out.println("Warning! Number of episodes not specified. Defaulting to 10 episodes.");
        }

        boolean loadWeights = false;
        if (args.length >= 2) {
            loadWeights = Boolean.parseBoolean(args[1]);
        } else {
            System.out.println("Warning! Load weights argument not specified. Defaulting to not loading.");
        }

        if (loadWeights) {
            weights = loadWeights();
        } else {
            weights = new Double[stateFeatures.size()];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = random.nextDouble();
            }
        }
    }

    /**
     * initial step function to start when running game
     */
    @Override
    public Map<Integer, Action> initialStep(State.StateView stateView, History.HistoryView historyView) {
        myFootmen = getFootmenForPlayer(stateView, playernum);
        enemyFootmen = getFootmenForPlayer(stateView, ENEMY_PLAYERNUM);

        for (int id : myFootmen) {
            rewards.put(id, new ArrayList<>());
        }

        return middleStep(stateView, historyView);
    }

    private List<Integer> getFootmenForPlayer(State.StateView stateView, int id) {
        List<Integer> footmen = new LinkedList<>();

        for (Integer unitId : stateView.getUnitIds(id)) {
            Unit.UnitView unit = stateView.getUnit(unitId);
            String unitName = unit.getTemplateView().getName().toLowerCase();
            if (unitName.equals("footman")) {
                footmen.add(unitId);
            } else {
                System.err.println("Unknown unit type: " + unitName);
            }
        }

        return footmen;
    }

    /**
     * You will need to calculate the reward at each step and update your totals. You will also need to
     * check if an event has occurred. If it has then you will need to update your weights and select a new action.
     *
     * Some useful API calls here are:
	 *
     * If you are using the footmen vectors you will also need to remove killed enemies and your units which being killed. To do so use the historyView
     * to get a DeathLog. Each DeathLog tells you which player's unit died and the unit ID of the dead unit. To get
     * the deaths from the last turn do something similar to the following snippet. Please be aware that on the first
     * turn you should not call this as you will get nothing back.
     *
     ** 
     *for(DeathLog deathLog : historyView.getDeathLogs(stateView.getTurnNumber() -1)) {
     *     System.out.println("Player: " + deathLog.getController() + " unit: " + deathLog.getDeadUnitID());
     * }
     **
     * You should also check for completed actions using the history view. Obviously you never want a footman just
     * sitting around doing nothing (the enemy certainly isn't going to stop attacking). So at the minimum you will
     * have an event whenever one your footmen's targets is killed or an action fails. Actions may fail if the target
     * is surrounded or the unit cannot find a path to the unit. To get the action results from the previous turn
     * you can do something similar to the following. Please be aware that on the first turn you should not call this
     **
     * Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(playernum, stateView.getTurnNumber() - 1);
     * for(ActionResult result : actionResults.values()) {
     *     System.out.println(result.toString());
     * }
     **
     *
     * Remember that you can use result.getFeedback() on an ActionResult, and compare the result to an ActionFeedback enum.
     * Useful ActionFeedback values include COMPLETED, FAILED, and INCOMPLETE.
     * 
     * You can also get the ID of the unit executing an action from an ActionResult. For example,
     * result.getAction().getUnitID()
     * 
     * For this assignment it will be most useful to create compound attack actions. These will move your unit
     * within range of the enemy and then attack them once. You can create one using the static method in Action:
     * Action.createCompoundAttack(attackerID, targetID)
     *
     * You will then need to add the actions you create to a Map that will be returned. This creates a mapping
     * between the ID of the unit performing the action and the Action object.
     *
     * @param historyView
     * @param stateView
     * @return New actions to execute or nothing if an event has not occurred.
     */
    @Override
    public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
        killPointsAwarded = false;
        int previousTurnNumber = stateView.getTurnNumber() - 1;

        if (previousTurnNumber >= 0) {
            executeTurn(stateView, historyView, previousTurnNumber);
            previousStateView = stateView;
        }

        return generateAttackerActions(stateView, historyView);
    }
    /**
     * Function to generate actions for the array of current footman attackerws
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @return New actions to execute or nothing if an event has not occurred.
     */
    private Map<Integer, Action> generateAttackerActions(State.StateView stateView, History.HistoryView historyView) {
        Map<Integer, Action> possibleActions = new HashMap<>();

        for (int attackerId : myFootmen) {
            int defenderId = selectAction(stateView, historyView, attackerId);
            possibleActions.put(attackerId, Action.createCompoundAttack(attackerId, defenderId));
        }

        return possibleActions;
    }

    /**
     * Given a footman and the current state and history of the game select the enemy that this unit should
     * attack. This is where you would do the epsilon-greedy action selection.
     *
     * @param stateView Current state of the game
     * @param historyView The entire history of this episode
     * @param attackerId The footman that will be attacking
     * @return The enemy footman ID this unit should attack
     */
    public int selectAction(State.StateView stateView, History.HistoryView historyView, int attackerId) {
        if (currentlyEvaluating || random.nextDouble() < 1 - epsilon) {
            return calcBestTarget(stateView, historyView, attackerId);
        } else {
            return enemyFootmen.get(random.nextInt(enemyFootmen.size()));
        }
    }

    /**
     *
     * Here you will calculate the cumulative average rewards for your testing episodes. If you have just
     * finished a set of test episodes you will call out testEpisode.
     *
     * It is also a good idea to save your weights with the saveWeights function.
     */
    @Override
    public void terminalStep(State.StateView stateView, History.HistoryView historyView) {
        executeTurn(stateView, historyView, stateView.getTurnNumber() - 1);

        currentEpisode++;
        numEpisodesThisSeason++;

        if (currentlyEvaluating) {
            if (numEpisodesThisSeason == NUM_EVALUATION_EPISODES) {
                averageRewards.add(cumulativeReward/NUM_EVALUATION_EPISODES);
                printTestData(averageRewards);

                numEpisodesThisSeason = 0;
                cumulativeReward = 0.0;
                currentlyEvaluating = false;
            }
        } else if (numEpisodesThisSeason == NUM_LEARNING_EPISODES) {
            numEpisodesThisSeason = 0;
            cumulativeReward = 0.0;
            currentlyEvaluating = true;
        }

        saveWeights(weights);

        if(currentEpisode > numEpisodes) {
            System.out.println("Season completed.");
            System.exit(0);
        }
    }

    /**
     * Remove dead agents from list and update rewards.
     * For the remaining agents, discount rewards and update weights.
     *
     * @param stateView Current state of the SEPIA game
     * @param historyView History of the game up until this turn
     * @param previousTurnNumber the previous turn number
     */
    private void executeTurn(State.StateView stateView, History.HistoryView historyView, int previousTurnNumber) {
        for (DeathLog deathLog : historyView.getDeathLogs(previousTurnNumber)) {
            int unitId = deathLog.getDeadUnitID();
            if (deathLog.getController() == ENEMY_PLAYERNUM) {
                enemyFootmen.remove((Integer) unitId);
            } else {
                myFootmen.remove((Integer) unitId);
                double deathReward = calculateReward(stateView, historyView, unitId);
                cumulativeReward += deathReward;
                this.rewards.get(unitId).add(deathReward);
            }
        }

        Map<Integer, ActionResult> actionLog = historyView.getCommandFeedback(playernum, previousTurnNumber);

        for (int attackerId : myFootmen) {
            List<Double> rewardList = this.rewards.get(attackerId);

            double actionReward = calculateReward(stateView, historyView, attackerId);
            cumulativeReward += actionReward;
            rewardList.add(actionReward);

            double discount = 1.0;
            double cumulativeDiscountedReward = 0.0;
            for (int i = rewardList.size() - 1; i >= 0; i--) {
                discount *= gamma;
                cumulativeDiscountedReward += discount*rewardList.get(i);
            }

            int defenderId = ((TargetedAction) actionLog.get(attackerId).getAction()).getTargetId();
            if (!currentlyEvaluating) {
                weights = wrapDoubles(updateFeatureWeights(
                        unwrapDoubles(weights),
                        calculateFeatureVector(previousStateView, historyView, attackerId, defenderId),
                        cumulativeDiscountedReward,
                        stateView, historyView, attackerId
                ));
            }
        }
    }

    /**
     * helper function to wrap array of double
     *
     * @param array input array
     * @return wrapper array of doubles
     */
    public Double[] wrapDoubles(double[] array) {
        Double[] wrapped = new Double[array.length];
        for (int i = 0; i < array.length; i++) {
            wrapped[i] = array[i];
        }
        return wrapped;
    }

    /**
     * helper function to unwrap array of double
     *
     * @param array input wrap array
     * @return unwrapped array of doubles
     */
    public double[] unwrapDoubles(Double[] array) {
        double[] unwrapped = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            unwrapped[i] = array[i];
        }
        return unwrapped;
    }

    /**
     * Calculate the updated weights for this agent.
     * @param oldWeights Weights prior to update
     * @param oldFeatures Features from (s,a)
     * @param cdReward double to track rewards
     * @param stateView Current state of the game.
     * @param historyView History of the game up until this point
     * @param attackerId The id of attacker to update weights
     * @return The updated weight vector
     */
    public double[] updateFeatureWeights(double[] oldWeights, double[] oldFeatures, double cdReward,
                                         State.StateView stateView, History.HistoryView historyView, int attackerId) {
        int defenderId = calcBestTarget(stateView, historyView, attackerId);

        double[] newWeights = new double[stateFeatures.size()];
        double[] newFeatures = calculateFeatureVector(stateView, historyView, attackerId, defenderId);

        double maxQValue = calcQValue(stateView, historyView, attackerId, defenderId);
        double oldQValue = calcQValueOfFeatures(oldFeatures);

        for (int i = 0; i < stateFeatures.size(); i++) {
            newWeights[i] = oldWeights[i] + learningRate*(cdReward + gamma*maxQValue - oldQValue)*newFeatures[i];
        }

        return newWeights;
    }

    /**
     * Given the current state and the footman in question calculate the reward received on the last turn.
     * This is where you will check for things like Did this footman take or give damage? Did this footman die
     * or kill its enemy. Did this footman start an action on the last turn? See the assignment description
     * for the full list of rewards.
     *
     * Remember that you will need to discount this reward based on the timestep it is received on. See
     * the assignment description for more details.
     *
     * As part of the reward you will need to calculate if any of the units have taken damage. You can use
     * the history view to get a list of damages dealt in the previous turn. Use something like the following.
     *
     * for(DamageLog damageLogs : historyView.getDamageLogs(lastTurnNumber)) {
     *     System.out.println("Defending player: " + damageLog.getDefenderController() + " defending unit: " + \
     *     damageLog.getDefenderID() + " attacking player: " + damageLog.getAttackerController() + \
     *     "attacking unit: " + damageLog.getAttackerID() + "damage: " + damageLog.getDamage());
     * }
     *
     * You will do something similar for the deaths. See the middle step documentation for a snippet
     * showing how to use the deathLogs.
     *
     * To see if a command was issued you can check the commands issued log.
     *
     * Map<Integer, Action> commandsIssued = historyView.getCommandsIssued(playernum, lastTurnNumber);
     * for (Map.Entry<Integer, Action> commandEntry : commandsIssued.entrySet()) {
     *     System.out.println("Unit " + commandEntry.getKey() + " was command to " + commandEntry.getValue().toString);
     * }
     *
     * @param stateView The current state of the game.
     * @param historyView History of the episode up until this turn.
     * @param footmanId The footman ID you are looking for the reward from.
     * @return The current reward
     */
    public double calculateReward(State.StateView stateView, History.HistoryView historyView, int footmanId) {
        double reward = -0.1;
        int previousTurnNumber = stateView.getTurnNumber() - 1;

        for (DamageLog damageLog : historyView.getDamageLogs(previousTurnNumber)) {
            if (damageLog.getAttackerController() == playernum && damageLog.getAttackerID() == footmanId) {
                reward += damageLog.getDamage();
            } else if (damageLog.getAttackerController() == ENEMY_PLAYERNUM && damageLog.getDefenderID() == footmanId) {
                reward -= damageLog.getDamage();
            }
        }

        for (DeathLog deathLog : historyView.getDeathLogs(previousTurnNumber)) {
            if (deathLog.getController() == ENEMY_PLAYERNUM
                    && hasKilledFootman(deathLog, historyView, footmanId, previousTurnNumber)) {
                if (!killPointsAwarded) {
                    reward += KILL_REWARD;
                    killPointsAwarded = true;
                }
            } else if (deathLog.getDeadUnitID() == footmanId) {
                reward -= KILL_REWARD;
            }
        }

        return reward;
    }

    /**
     * A function to check whether the attacking unit was responsible for the death of a unit.
     *
     * @param footmanId the attacking unit
     * @param deathLog a record of deaths in the game's history
     * @param historyView History of the game up until this turn
     * @param previousTurnNumber the previous turn number
     * @return a boolean output corresponding to whether the footman was attacking a dead unit.
     */
    private boolean hasKilledFootman(DeathLog deathLog, History.HistoryView historyView,
                                     int footmanId, int previousTurnNumber) {
        Map<Integer, ActionResult> commandLog = historyView.getCommandFeedback(playernum, previousTurnNumber);
        if (commandLog.containsKey(footmanId)
                && commandLog.get(footmanId).getFeedback().equals(ActionFeedback.COMPLETED)) {
            return ((TargetedAction) commandLog.get(footmanId).getAction()).getTargetId() == deathLog.getDeadUnitID();
        }
        return false;
    }

    /**
     * Calculates our Arg Max value to determine the optimal target.
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @return An enemy footman that your footman would be attacking
     */
    private int calcBestTarget(State.StateView stateView, History.HistoryView historyView, int attackerId) {
        int defenderId = -1;
        double highestQ = Double.NEGATIVE_INFINITY;

        for (Integer enemyId : enemyFootmen) {
            double qValue = calcQValue(stateView, historyView, attackerId, enemyId);
            if (qValue > highestQ) {
                defenderId = enemyId;
                highestQ = qValue;
            }
        }

        return defenderId;
    }

    /**
     * Calculate the Q-Value for a given state action pair. The state in this scenario is the current
     * state view and the history of this episode. The action is the attacker and the enemy pair for the
     * SEPIA attack action.
     *
     * This returns the Q-value according to your feature approximation. This is where you will calculate
     * your features and multiply them by your current weights to get the approximate Q-value.
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return The approximate Q-value
     */
    public double calcQValue(State.StateView stateView, History.HistoryView historyView,
                             int attackerId, int defenderId) {
        return calcQValueOfFeatures(calculateFeatureVector(stateView, historyView, attackerId, defenderId));
    }

    private double calcQValueOfFeatures(double[] featureValues) {
        double qValue = 0.0;
        for (int i = 0; i < stateFeatures.size(); i++) {
            qValue += weights[i]*featureValues[i];
        }
        return qValue;
    }

    /**
     * Given a state and action calculate your features here. Please include a comment explaining what features
     * you chose and why you chose them.
     *
     * for example: HP
     * UnitView attacker = stateView.getUnit(attackerId);
     * attacker.getHP()
     *
     * All of your feature functions should evaluate to a double. Collect all of these into an array. You will
     * take a dot product of this array with the weights array to get a Q-value for a given state action.
     *
     * It is a good idea to make the first value in your array a constant. This just helps remove any offset
     * from 0 in the Q-function. The other features are up to you. Many are suggested in the assignment
     * description.
     *
     * @param stateView Current state of the SEPIA game
     * @param historyView History of the game up until this turn
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman. The one you are considering attacking.
     * @return The array of feature function outputs.
     */
    public double[] calculateFeatureVector(State.StateView stateView, History.HistoryView historyView,
                                           int attackerId, int defenderId) {
        double[] featureVector = new double[stateFeatures.size()];
        for (int i = 0; i < stateFeatures.size(); i++) {
            featureVector[i] = stateFeatures.get(i).evaluate(stateView, historyView, attackerId, defenderId);
        }
        return featureVector;
    }


    /**
     * utility to turn all the state feature methods into runnable objects
     */
    private interface StateFeature{
        double evaluate(State.StateView stateView, History.HistoryView historyView,
                        int attackerId, int defenderId);
    }



    /**
     * Calculates our Arg Max value to determine the optimal target.
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureAttackerHP(State.StateView stateView, History.HistoryView historyView,
                                     int attackerId, int defenderId) {
        Unit.UnitView attacker = stateView.getUnit(attackerId);
        if(attacker == null){
            return 0;
        }
        return (double) attacker.getHP() / (double) attacker.getTemplateView().getBaseHealth();
    }

    /**
     * Feature to account for HP of defender in senario
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureDefenderHP(State.StateView stateView, History.HistoryView historyView,
                                     int attackerId, int defenderId) {
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if(defender == null || defender.getHP() == 0){
            return 0;
        }
        return 1 - (double) defender.getHP() / (double) defender.getTemplateView().getBaseHealth();
    }

    /**
     * Feature to account for HP difference between defender and attacker
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureHPDifference(State.StateView stateView, History.HistoryView historyView,
                                  int attackerId, int defenderId) {
        Unit.UnitView attacker = stateView.getUnit(attackerId);
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if (attacker == null || defender == null || defender.getHP() == 0) {
            return 0;
        }
        return 0.5 + (double)(attacker.getHP() - defender.getHP())
                / (double) attacker.getTemplateView().getBaseHealth();
    }

    /**
     * Feature to account for distance to enemy from attacker in senario
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureDistanceToEnemy(State.StateView stateView, History.HistoryView historyView,
                                          int attackerId, int defenderId) {
        Unit.UnitView attacker = stateView.getUnit(attackerId);
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if (attacker == null || defender == null) {
            return 0;
        }
        return 1/manhattanDistance(attacker, defender);
    }

    /**
     * Feature to promote proximity to enemy from attacker
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureNextToEnemy(State.StateView stateView, History.HistoryView historyView,
                                      int attackerId, int defenderId) {
        Unit.UnitView attacker = stateView.getUnit(attackerId);
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if(attacker == null || defender == null){
            return 0;
        }
        return (Math.abs(defender.getXPosition() - attacker.getXPosition()) <= 1 &&
                Math.abs(defender.getYPosition() - attacker.getYPosition()) <= 1) ? 1 : 0;
    }

    /**
     * Feature regarding the continuation of an attack from an attacker unit on a defender
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureContinueAttacking(State.StateView stateView, History.HistoryView historyView,
                                            int attackerId, int defenderId) {
        Map<Integer, Action> actionLog = historyView.getCommandsIssued(playernum, stateView.getTurnNumber() - 1);
        TargetedAction lastAction = (TargetedAction) actionLog.get(attackerId);
        return lastAction != null && lastAction.getTargetId() == defenderId ? 1 : 0;
    }


    /**
     * Gang up atackers against a defender
     *
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman. The one you are considering attacking.
     * @return whether the attacker was recently under attack.
     */
    private double featureGangUp(State.StateView stateView, History.HistoryView historyView,
                                 int attackerId, int defenderId) {
        int previousTurnNumber = stateView.getTurnNumber() - 1;
        if (previousTurnNumber < 0) {
            return 0;
        }

        Map<Integer, Action> actionLog = historyView.getCommandsIssued(playernum, previousTurnNumber);

        double result = 0.0;
        for (int otherAttackerId : myFootmen){
            if(otherAttackerId != attackerId) {
                TargetedAction lastAction = ((TargetedAction) actionLog.get(otherAttackerId));
                if (lastAction != null && lastAction.getTargetId() == defenderId){
                    result += 0.25;
                }
            }
        }

        return result;
    }

    /**
     * Feature to target defender units with lower HP points
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureGoLow(State.StateView stateView, History.HistoryView historyView,
                                int attackerId, int defenderId) {
        int lowestDefenderY = -1;
        for(int otherDefenderId : enemyFootmen){
            Unit.UnitView otherDefender = stateView.getUnit(otherDefenderId);
            if(otherDefender != null && otherDefender.getYPosition() > lowestDefenderY){
                lowestDefenderY = otherDefender.getYPosition();
            }
        }
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if(defender != null){
            return (double) defender.getYPosition() / (double) Math.max(lowestDefenderY, 1);
        }
        return 0;
    }


    /**
     * Feature to account for attacker being attacked from the enemy
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureGettingAttacked(State.StateView stateView, History.HistoryView historyView,
                                          int attackerId, int defenderId) {
        int previousTurnNumber = stateView.getTurnNumber() - 1;
        if (previousTurnNumber < 0) {
            return 0;
        }

        Map<Integer, Action> actionLog = historyView.getCommandsIssued(ENEMY_PLAYERNUM, previousTurnNumber);
        int numTargetedBy = 0;
        for(int otherDefenderId : enemyFootmen) {
            TargetedAction lastAction = ((TargetedAction) actionLog.get(otherDefenderId));
            if(lastAction != null && lastAction.getTargetId() == attackerId){
                numTargetedBy++;
            }
        }

        return 1 - (double) numTargetedBy / (double) Math.max(enemyFootmen.size(), 1);
    }

    /**
     * Feature to find a clear path from attacker to defender
     *
     * @param stateView Current SEPIA state
     * @param historyView Episode history up to this point in the game
     * @param attackerId Your footman. The one doing the attacking.
     * @param defenderId An enemy footman that your footman would be attacking
     * @return An enemy footman that your footman would be attacking
     */
    private double featureUnobstructedPath(State.StateView stateView, History.HistoryView historyView,
                                           int attackerId, int defenderId) {
        Unit.UnitView attacker = stateView.getUnit(attackerId);
        Unit.UnitView defender = stateView.getUnit(defenderId);
        if (attacker == null || defender == null) {
            return 0;
        }

        double result = 1;
        double decrement = 1/manhattanDistance(attacker, defender);

        for(int otherAttackerId : myFootmen){
            if(otherAttackerId != attackerId) {
                Unit.UnitView otherAttacker = stateView.getUnit(otherAttackerId);
                if (otherAttacker != null) {
                    if (inTheWay(attacker, defender, otherAttacker)) {
                        result -= decrement;
                    }
                }
            }
        }

        for(int otherDefenderId : enemyFootmen){
            if(otherDefenderId != defenderId) {
                Unit.UnitView otherDefender = stateView.getUnit(otherDefenderId);
                if (otherDefender != null) {
                    if (inTheWay(attacker, defender, otherDefender)) {
                        result -= decrement;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Helper function to calculate manhattan distance- a distance metric between two points in vector space
     *
     * @param unit1 starting location
     * @param unit2 destination location
     * @return the calculated manhattan distance
     */
    private double manhattanDistance(Unit.UnitView unit1, Unit.UnitView unit2) {
        return Math.abs(unit2.getXPosition() - unit1.getXPosition())
                + Math.abs(unit2.getYPosition() - unit1.getYPosition());
    }


    /**
     * Helper function to check for a clear path from a start to a destination
     *
     * @param from starting location
     * @param to destination
     * @param obstacle possible obstacle blocking unit
     * @return boolean value if object is in the way of path
     */
    private boolean inTheWay(Unit.UnitView from, Unit.UnitView to, Unit.UnitView obstacle){
        double angle1 = Math.atan2(
                to.getYPosition() - from.getYPosition(),
                to.getXPosition() - from.getXPosition()
        );
        double angle2 = Math.atan2(
                obstacle.getYPosition() - from.getYPosition(),
                obstacle.getXPosition() - from.getXPosition()
        );
        return angle1 == angle2;
    }

    /**
     * DO NOT CHANGE THIS!
     *
     * Prints the learning rate data described in the assignment. Do not modify this method.
     *
     * @param averageRewards List of cumulative average rewards from test episodes.
     */
    public void printTestData (List<Double> averageRewards) {
        System.out.println();
        System.out.println("Games Played      Average Cumulative Reward");
        System.out.println("-------------     -------------------------");
        for (int i = 0; i < averageRewards.size(); i++) {
            String gamesPlayed = Integer.toString(10*i);
            String averageReward = String.format("%.2f", averageRewards.get(i));

            int numSpaces = "-------------     ".length() - gamesPlayed.length();
            StringBuilder spaceBuffer = new StringBuilder(numSpaces);
            for (int j = 0; j < numSpaces; j++) {
                spaceBuffer.append(" ");
            }
            System.out.println(gamesPlayed + spaceBuffer + averageReward);
        }
        System.out.println();
    }

    /**
     * DO NOT CHANGE THIS!
     *
     * This function will take your set of weights and save them to a file. Overwriting whatever file is
     * currently there. You will use this when training your agents. You will include th output of this function
     * from your trained agent with your submission.
     *
     * Look in the agent_weights folder for the output.
     *
     * @param weights Array of weights
     */
    public void saveWeights(Double[] weights) {
        File path = new File("agent_weights/weights.txt");
        // create the directories if they do not already exist
        path.getAbsoluteFile().getParentFile().mkdirs();

        try {
            // open a new file writer. Set append to false
            BufferedWriter writer = new BufferedWriter(new FileWriter(path, false));

            for (double weight : weights) {
                writer.write(String.format("%f\n", weight));
            }
            writer.flush();
            writer.close();
        } catch(IOException ex) {
            System.err.println("Failed to write weights to file. Reason: " + ex.getMessage());
        }
    }

    /**
     * DO NOT CHANGE THIS!
     *
     * This function will load the weights stored at agent_weights/weights.txt. The contents of this file
     * can be created using the saveWeights function. You will use this function if the load weights argument
     * of the agent is set to 1.
     *
     * @return The array of weights
     */
    public Double[] loadWeights() {
        File path = new File("agent_weights/weights.txt");
        if (!path.exists()) {
            System.err.println("Failed to load weights. File does not exist");
            return null;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line;
            List<Double> weights = new LinkedList<>();
            while((line = reader.readLine()) != null) {
                weights.add(Double.parseDouble(line));
            }
            reader.close();

            return weights.toArray(new Double[0]);
        } catch(IOException ex) {
            System.err.println("Failed to load weights from file. Reason: " + ex.getMessage());
        }
        return null;
    }

    @Override
    public void savePlayerData(OutputStream outputStream) {

    }

    @Override
    public void loadPlayerData(InputStream inputStream) {

    }

}
