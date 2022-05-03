package edu.cwru.sepia.agent.minimax;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.LocatedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;

/**
 * This class stores all of the information the agent
 * needs to know about the state of the game. For example this
 * might include things like footmen HP and positions.
 *
 * Add any information or methods you would like to this class,
 * but do not delete or change the signatures of the provided methods.
 */
public class GameState {

    final static double DISTANCE_WEIGHT =  -1500; //16
    final static double FOOTMAN_HEALTH_WEIGHT = 0.0000000000003; //0.03
    final static double ENEMY_HEALTH_WEIGHT = -1500; //-1.5
    final static double MEATSHIELDING_WEIGHT = 70; //70
    final static double WITHIN_ARCHER_RANGE_WEIGHT = 500; //500

    final static int AVERAGE_DAMAGE = 8;
    final static int ARCHER_MIN_RANGE = 4;
    final static int ARCHER_MAX_RANGE = 10;

    private final GameState parentState;
    private final boolean ourTurn;
    private boolean utilityCalculated = false;
    private double utility = 0.0;

    private HashMap<ProxyAgent, Stack<AStarState>> aStarPaths;

    private final int mapDimX;
    private final int mapDimY;

    public boolean[][] obstacles;
    private boolean noObstacles = true;

    private final HashMap<Integer, ProxyAgent> proxyAgentsById = new HashMap<>();
    private final ArrayList<ProxyAgent> proxyFootmenUnits = new ArrayList<>();
    private final ArrayList<ProxyAgent> proxyArcherUnits = new ArrayList<>();

    private static class ProxyAgent{
        int id;
        int xPos;
        int yPos;
        int hp;

        ProxyAgent(int id, int xPos, int yPos, int hp) {
            this.id = id;
            this.xPos = xPos;
            this.yPos = yPos;
            this.hp = hp;
        }
    }

    /**
     * You will implement this constructor. It will
     * extract all of the needed state information from the built in
     * SEPIA state view.
     *
     * You may find the following state methods useful:
     *
     * state.getXExtent() and state.getYExtent(): get the map dimensions
     * state.getAllResourceIDs(): returns the IDs of all of the obstacles in the map
     * state.getResourceNode(int resourceID): Return a ResourceView for the given ID
     *
     * For a given ResourceView you can query the position using
     * resource.getXPosition() and resource.getYPosition()
     *
     * You can get a list of all the units belonging to a player with the following command:
     * state.getUnitIds(int playerNum): gives a list of all unit IDs beloning to the player.
     * You control player 0, the enemy controls player 1.
     *
     * In order to see information about a specific unit, you must first get the UnitView
     * corresponding to that unit.
     * state.getUnit(int id): gives the UnitView for a specific unit
     *
     * With a UnitView you can find information about a given unit
     * unitView.getXPosition() and unitView.getYPosition(): get the current location of this unit
     * unitView.getHP(): get the current health of this unit
     *
     * SEPIA stores information about unit types inside TemplateView objects.
     * For a given unit type you will need to find statistics from its Template View.
     * unitView.getTemplateView().getRange(): This gives you the attack range
     * unitView.getTemplateView().getBasicAttack(): The amount of damage this unit type deals
     * unitView.getTemplateView().getBaseHealth(): The initial amount of health of this unit type
     *
     * @param state Current state of the episode
     */
    public GameState(StateView state) {
        this.parentState = null;
        this.ourTurn = true;

        for (UnitView agent : state.getAllUnits()) {
            ProxyAgent proxy = new ProxyAgent(agent.getID(), agent.getXPosition(), agent.getYPosition(), agent.getHP());
            this.proxyAgentsById.put(agent.getID(), proxy);
            if(agent.getTemplateView().getCharacter() == 'f'){
                this.proxyFootmenUnits.add(proxy);
            }else{
                this.proxyArcherUnits.add(proxy);
            }
        }

        mapDimX = state.getXExtent();
        mapDimY = state.getYExtent();

        obstacles = new boolean[mapDimX][mapDimY];
        for(ResourceView resource : state.getAllResourceNodes()){
            obstacles[resource.getXPosition()][resource.getYPosition()] = true;
            noObstacles = false;
        }
    }

    /**
     * This constructor uses the non-SEPIA representation of the game and is called for all
     * creation of a GameState, except the first time.
     *
     * @param parentState
     */
    public GameState(GameState parentState, Map<Integer, Action> actionMap) {
        this.parentState = parentState;
        this.ourTurn = !parentState.ourTurn;
        this.mapDimX = parentState.mapDimX;
        this.mapDimY = parentState.mapDimY;
        this.obstacles = parentState.obstacles;
        this.noObstacles = parentState.noObstacles;

        parentState.proxyAgentsById.forEach((id, agent) -> {
            ProxyAgent agentClone = new ProxyAgent(agent.id, agent.xPos, agent.yPos, agent.hp);
            this.proxyAgentsById.put(id, agentClone);
            if(parentState.proxyFootmenUnits.contains(agent)){
                this.proxyFootmenUnits.add(agentClone);
            }else{
                this.proxyArcherUnits.add(agentClone);
            }
        });

        actionMap.forEach((id, action) -> {
            ProxyAgent proxy = proxyAgentsById.get(action.getUnitId());
            if(action instanceof DirectedAction){
                DirectedAction dAction = (DirectedAction)action;
                proxy.xPos += dAction.getDirection().xComponent();
                proxy.yPos += dAction.getDirection().yComponent();
            }else if(action instanceof TargetedAction){
                TargetedAction tAction = (TargetedAction)action;
                proxyAgentsById.get(tAction.getTargetId()).hp -= AVERAGE_DAMAGE;
            }else{
                LocatedAction lAction = (LocatedAction)action;
                proxy.xPos = lAction.getX();
                proxy.yPos = lAction.getY();
            }
        });
    }


    /**
     * Our utility function implements multiple heuristics that the agent seeks to optimize
     * distance - the footman agent seek to minimize the distance between them and archers
     * enemyHealth - the agent prioritizes states that lead to archers loosing health
     * meatsheilding - the agents prefer states where each footman is attacking a different archer to prevent clumping
     * withinArcherRange- states where the agent is within ARCHER_MIN_RANGE are greatly preferred and weighted highly
     *
     * The weight values associated with each utility value parameter are declared as final static variables
     * We iterated through different values through trial and error to find what weights the agent reacted best to
     *
     * @return The weighted linear combination of the features
     */
    public double getUtility() {
        if(this.utilityCalculated){
            return this.utility;
        }

        int footmenHealth = 0;
        int enemyHealth = 0;
        int meatshielding = 0;
        int withinArcherRange = 0;

        HashMap<ProxyAgent, ProxyAgent> closestArchers = new HashMap<>();

        for (ProxyAgent footman : proxyFootmenUnits) {
            ProxyAgent closestArcher = null;
            double closestArcherDistance = Double.MAX_VALUE;

            for (ProxyAgent archer : proxyArcherUnits) {
                double archerDistance = rawDistanceBetween(footman, archer);
                enemyHealth += archer.hp;

                if(archerDistance < closestArcherDistance){
                    closestArcher = archer;
                    closestArcherDistance = archerDistance;
                }
            }

            footmenHealth += footman.hp;

            if(!closestArchers.containsValue(closestArcher)){
                meatshielding++;
            }
            closestArchers.put(footman, closestArcher);

            if(closestArcherDistance < ARCHER_MIN_RANGE){
                withinArcherRange++;
            }
        }

        int distanceUtility = getDistanceUtility(closestArchers);

        this.utility += DISTANCE_WEIGHT * distanceUtility;
        this.utility += FOOTMAN_HEALTH_WEIGHT * footmenHealth;
        this.utility += ENEMY_HEALTH_WEIGHT * enemyHealth;
        this.utility += MEATSHIELDING_WEIGHT * meatshielding;
        this.utility += WITHIN_ARCHER_RANGE_WEIGHT * withinArcherRange;

        this.utilityCalculated = true;
        return this.utility;
    }

    /**
     * @param agent1 first agent to get location for distance
     * @param agent2 second agent to find distance to from fist
     *
     * @return calculated distance between agents
     */
    private double rawDistanceBetween(ProxyAgent agent1, ProxyAgent agent2){
        return Math.sqrt(Math.pow(agent2.xPos - agent1.xPos, 2) + Math.pow(agent2.yPos - agent1.yPos, 2));
    }

    /**
     * @param fromX starting x location
     * @param fromY starting y location
     * @param toX ending x location
     * @param toY ending y location
     *
     * @return calculated number of steps from fi
     */
    private int rawNumStepsBetween(int fromX, int fromY, int toX, int toY){
        return Math.abs(toX - fromX) + Math.abs(toY - fromY);
    }

    /**
     * @param agent1 agent to get starting location from
     * @param agent2 agent to get destination location
     *
     * @return calculated number of steps from agent1 to agent 2
     */
    private int rawNumStepsBetween(ProxyAgent agent1, ProxyAgent agent2){
        return rawNumStepsBetween(agent1.xPos, agent1.yPos, agent2.xPos, agent2.yPos);
    }

    /**
     * @param closestArchers hashmap hold proxyAgent archers on board
     *
     * @return calculated utility of gameState based on distance to archers
     */
    private int getDistanceUtility(HashMap<ProxyAgent, ProxyAgent> closestArchers){
        AtomicInteger totalDistance = new AtomicInteger();

        if(this.noObstacles){
            closestArchers.forEach(
                    (footman, archer) -> totalDistance.addAndGet(rawNumStepsBetween(footman, archer))
            );
        }else {
            this.aStarPaths = new HashMap<>();

            if(this.parentState == null || this.parentState.aStarPaths == null
                    || archersMovedTooMuch(this, this.parentState)) {
                closestArchers.forEach((footman, archer) -> {
                    Stack<AStarState> path = aStar(footman.xPos, footman.yPos, archer.xPos, archer.yPos);
                    this.aStarPaths.put(footman, path);
                    totalDistance.addAndGet(path.size());
                });
            }else{
                parentState.aStarPaths.forEach((parentFootman, path) -> {
                    Stack<AStarState> newPath = new Stack<>();
                    ProxyAgent footman = proxyAgentsById.get(parentFootman.id);
                    newPath.addAll(path);
                    newPath.pop();
                    this.aStarPaths.put(footman, newPath);
                    totalDistance.addAndGet(path.size() + rawNumStepsBetween(
                            footman.xPos, footman.yPos, path.peek().x, path.peek().y)
                    );
                });
            }
        }

        return totalDistance.get();
    }

    /**
     * @param state1 initial game state for check
     * @param state2 second game state to check against initial
     *
     * @return return true if detected too much archer movement between states
     */
    private boolean archersMovedTooMuch(GameState state1, GameState state2){
        for(ProxyAgent archer1 : state1.proxyArcherUnits){
            ProxyAgent archer2 = state2.proxyAgentsById.get(archer1.id);
            if(Math.abs(archer2.xPos - archer1.xPos) > 2 || Math.abs(archer2.yPos - archer1.yPos) > 2){
                return true;
            }
        }
        return false;
    }

    /**
     * @param fromX starting x location
     * @param fromY starting y location
     * @param toX destination x location
     * @param toY destination y location
     *
     * @return stack of A* search calculated states from (X1,Y1) to (X2,Y2)
     */
    private Stack<AStarState> aStar(int fromX, int fromY, int toX, int toY){
        PriorityQueue<AStarState> openList = new PriorityQueue<>(
                Comparator.comparingInt(state -> state.f)
        );
        LinkedList<AStarState> closedList = new LinkedList<>();
        openList.add(new AStarState(null, fromX, fromY, toX, toY));

        while(!openList.isEmpty()){
            AStarState currentState = openList.poll();

            successors: for(Direction direction : getPossibleDirections()){
                int newX = currentState.x + direction.xComponent();
                int newY = currentState.y + direction.yComponent();

                if(notOutOfBounds(newX, newY) && !obstacles[newX][newY]){
                    AStarState newState = new AStarState(currentState, newX, newY, toX, toY);

                    if(newX == toX && newY == toY){
                        Stack<AStarState> path = new Stack<>();
                        AStarState pathState = newState;

                        do{
                            path.push(pathState);
                            pathState = pathState.parent;
                        }while(pathState.parent != null);
                        path.pop();

                        return path;
                    }

                    for(AStarState state : openList) {
                        if(state.x == newX && state.y == newY && state.f < newState.f){
                            continue successors;
                        }
                    }

                    for(AStarState state : closedList) {
                        if(state.x == newX && state.y == newY && state.f < newState.f){
                            continue successors;
                        }
                    }

                    openList.add(newState);
                }
            }

            closedList.add(currentState);
        }

        System.out.println("Error: A* unable to reach target position");
        System.exit(1);
        return null;
    }

    /**
     * Used for testing.
     * @param state
     * @param sb
     * @return
     */
    private String pathToString(AStarState state, StringBuilder sb){
        if(sb == null){
            sb = new StringBuilder("Path:");
        }
        if(state.parent != null){
            pathToString(state.parent, sb);
        }

        sb.append(" (");
        sb.append(state.x);
        sb.append(", ");
        sb.append(state.y);
        sb.append(")");

        return sb.toString();
    }

    /**
     *
     * A class to hold states calculated in our A* search algorithm
     */
    private class AStarState{
        AStarState parent;
        int x;
        int y;
        int g;
        int h;
        int f;

        AStarState(AStarState parent, int x, int y, int toX, int toY){
            this.parent = parent;
            this.x = x;
            this.y = y;
            if(parent != null){
                this.g = parent.g + 1;
            }else{
                this.g = 0;
            }
            this.h = rawNumStepsBetween(x, y, toX, toY);
            this.f = this.g + this.h;
        }
    }

    /**
     * @param agentList list of all agents
     *
     * @return list of agents still alive
     */
    private ArrayList<ProxyAgent> getLivingAgents(ArrayList<ProxyAgent> agentList) {
        ArrayList<ProxyAgent> livingAgents = new ArrayList<>();
        for(ProxyAgent agent : agentList) {
            if(agent.hp > 0){
                livingAgents.add(agent);
            }
        }
        return livingAgents;
    }

    /**
     * @return directions availible to an agent for movement
     */
    private List<Direction> getPossibleDirections() {
        List<Direction> directions = new ArrayList<>();

        directions.add(Direction.NORTH);
        directions.add(Direction.EAST);
        directions.add(Direction.SOUTH);
        directions.add(Direction.WEST);

        return directions;
    }

    /**
     * @param x x location
     * @param y y location
     *
     * @return true if (x,y) is in bounds of game board
     */
    private boolean notOutOfBounds(int x, int y) {
        return x >= 0 && x < mapDimX && y >= 0 && y < mapDimY;
    }

    /**
     * @param actions list of actions to check
     * @param xPos agent x position
     * @param yPos agent y position
     * @param id agent id
     *
     * @return list of possible actions to add
     */
    private List<Action> addActionsToList(List<Action> actions, int xPos, int yPos, int id) {
        for (Direction direction : getPossibleDirections()) {
            int newXPos = xPos + direction.xComponent();
            int newYPos = yPos + direction.yComponent();

            // check if move is possible
            if (notOutOfBounds(newXPos, newYPos) && !obstacles[newXPos][newYPos]) {
                actions.add(Action.createCompoundMove(id, newXPos, newYPos));
            }
        }
        return actions;
    }

    /**
     * @param footman agent footman
     *
     * @return list of agent's possible actions to check
     */
    private List<Action> getFootmanActions(ProxyAgent footman) {
        List<Action> actions = new ArrayList<>();

        // check for archers within range
        for (ProxyAgent archer : proxyArcherUnits) {
            if (Math.abs(archer.xPos - footman.xPos) + Math.abs(archer.yPos - footman.yPos) <= 1) {
                actions.add(new TargetedAction(
                        footman.id,
                        (Action.createCompoundAttack(footman.id, archer.id).getType()),
                        archer.id)
                );
            }
        }

        return addActionsToList(actions, footman.xPos, footman.yPos, footman.id);
    }

    /**
     * @param archer agent archer
     *
     * @return list of agent's possible actions to check
     */
    private List<Action> getArcherActions(ProxyAgent archer) {
        List<Action> actions = new ArrayList<>();

        // check for footmen within range
        for (ProxyAgent footman : proxyFootmenUnits) {
            if (Math.abs(footman.xPos - archer.xPos) + Math.abs(footman.yPos - archer.yPos) <= ARCHER_MAX_RANGE) {
                actions.add(Action.createCompoundAttack(archer.id, footman.id));
            }
        }

        return addActionsToList(actions, archer.xPos, archer.yPos, archer.id);
    }

    /**
     * Takes into account the current turn (good or bad) and generates children for
     * the current ply.
     *
     * @return all of the possible children of this GameState
     */
    public List<GameStateChild> getChildren() {
        List<List<Action>> actionsThisTurn = new ArrayList<>();
        if(ourTurn){ // footmans move
            for(ProxyAgent footman : getLivingAgents(proxyFootmenUnits)){
                actionsThisTurn.add(getFootmanActions(footman));
            }
        }else{ // archers move
            for(ProxyAgent archer : getLivingAgents(proxyArcherUnits)){
                actionsThisTurn.add(getArcherActions(archer));
            }
        }

        List<Map<Integer, Action>> actionMaps = generateActionCombinations(actionsThisTurn);
        return generateChildrenFromActionMaps(actionMaps);
    }

    /**
     * Give a list of actions for every agent returns Maps from unitId to Action for each
     * possible combination of actions for a pair of footmen or archers
     */
    private List<Map<Integer, Action>> generateActionCombinations(List<List<Action>> allActions){
        List<Map<Integer, Action>> actionMaps = new ArrayList<>();
        if(allActions.isEmpty()){
            return actionMaps;
        }
        List<Action> actionsForFirstAgent = allActions.get(0);
        for(Action actionForAgent : actionsForFirstAgent){
            if(allActions.size() == 1){
                Map<Integer, Action> actionMap = new HashMap<>();
                actionMap.put(actionForAgent.getUnitId(), actionForAgent);
                actionMaps.add(actionMap);
            } else {
                for(Action actionForOtherAgent : allActions.get(1)){
                    Map<Integer, Action> actionMap = new HashMap<>();
                    actionMap.put(actionForAgent.getUnitId(), actionForAgent);
                    actionMap.put(actionForOtherAgent.getUnitId(), actionForOtherAgent);
                    actionMaps.add(actionMap);
                }
            }
        }
        return actionMaps;
    }

    /**
     * Given all Maps from unitId to Action that are possible for the current ply generate
     * the GameStateChild for each Map
     */
    private List<GameStateChild> generateChildrenFromActionMaps(List<Map<Integer, Action>> actionMaps){
        List<GameStateChild> children = new ArrayList<>(25);
        for(Map<Integer, Action> actionMap : actionMaps){
            GameState child = new GameState(this, actionMap);
            children.add(new GameStateChild(actionMap, child));
        }
        return children;
    }
}
