package edu.cwru.sepia.agent.planner;

import edu.cwru.sepia.agent.planner.actions.*;
import edu.cwru.sepia.agent.planner.resources.*;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;

import java.util.*;

import edu.cwru.sepia.agent.planner.actions.StripsAction;

/**
 * This class is used to represent the state of the game after applying one of the avaiable actions. It will also
 * track the A* specific information such as the parent pointer and the cost and heuristic function. Remember that
 * unlike the path planning A* from the first assignment the cost of an action may be more than 1. Specifically the cost
 * of executing a compound action such as move can be more than 1. You will need to account for this in your heuristic
 * and your cost function.
 *
 * The first instance is constructed from the StateView object (like in PA2). Implement the methods provided and
 * add any other methods and member variables you need.
 *
 * Some useful API calls for the state view are
 *
 * state.getXExtent() and state.getYExtent() to get the map size
 *
  * Note that SEPIA saves the townhall as a unit. Therefore when you create a GameState instance,
 * you must be able to distinguish the townhall from a peasant. This can be done by getting
 * the name of the unit type from that unit's TemplateView:
 * state.getUnit(id).getTemplateView().getName().toLowerCase(): returns "townhall" or "peasant"
 * 
 * You will also need to distinguish between gold mines and trees.
 * state.getResourceNode(id).getType(): returns the type of the given resource
 * 
 * You can compare these types to values in the ResourceNode.Type enum:
 * ResourceNode.Type.GOLD_MINE and ResourceNode.Type.TREE
 * 
 * You can check how much of a resource is remaining with the following:
 * state.getResourceNode(id).getAmountRemaining()
 *
 * I recommend storing the actions that generated the instance of the GameState in this class using whatever
 * class/structure you use to represent actions.
 */
public class GameState implements Comparable<GameState> {

    private static final double RESOURCES_LEFT_WEIGHT = 2.0;
    private static final double PEASANT_CAPACITY_WEIGHT = 0.5;
    private static final double NUM_PEASANTS_WEIGHT = -10000.0;
    private static final int GOLD_TO_BUILD = 400;

    private final State.StateView state;
    private final int playerNum;
    private final int requiredGold;
    private final int requiredWood;

    private Position townhallPosition;
    private int townhallID;
    private Peasant peasant;
    private HashMap<Integer, Peasant> peasants = new HashMap<>(8);
    private HashMap<Integer, Resource> resources = new HashMap<>(8);
    private Set<Position> resourcePositions = new HashSet<>();

    private double cost;
    private int amountGold;
    private int amountWood;
    private int amountFood;

    private ArrayList<StripsAction> plan = new ArrayList<>();

    /**
     * Construct a GameState from a stateview object. This is used to construct the initial search node. All other
     * nodes should be constructed from the another constructor you create or by factory functions that you create.
     *
     * @param state         The current stateview at the time the plan is being created
     * @param playernum     The player number of agent that is planning
     * @param requiredGold  The goal amount of gold (e.g. 200 for the small scenario)
     * @param requiredWood  The goal amount of wood (e.g. 200 for the small scenario)
     */
    public GameState(State.StateView state, int playernum, int requiredGold, int requiredWood) {
        this.state = state;
        this.playerNum = playernum;

        this.amountFood = state.getSupplyCap(playernum);

        for (ResourceNode.ResourceView resource : state.getAllResourceNodes()) {
            this.resourcePositions.add(new Position(resource.getXPosition(), resource.getYPosition()));
            if (resource.getType().equals(ResourceNode.Type.TREE)){
                Wood wood = new Wood(resource.getID(), resource.getAmountRemaining(), new Position(resource.getXPosition(), resource.getYPosition()));
                resources.put(wood.getId(), wood);
            }
            if (resource.getType().equals(ResourceNode.Type.GOLD_MINE)) {
                Gold gold = new Gold(resource.getID(), resource.getAmountRemaining(), new Position(resource.getXPosition(), resource.getYPosition()));
                resources.put(gold.getId(), gold);
            }
        }
        for (Unit.UnitView unit : state.getAllUnits()) {
            if(unit.getTemplateView().getName().equalsIgnoreCase("townhall")) {
                this.townhallID = unit.getID();
                this.townhallPosition = new Position(unit.getXPosition(), unit.getYPosition());
            }
            if(unit.getTemplateView().getName().equalsIgnoreCase("peasant")) {
                this.peasant = new Peasant(unit.getID(), townhallPosition);
                peasants.put(this.peasant.getId(), this.peasant);
                amountFood--;
            }
        }

        this.requiredGold = requiredGold;
        this.requiredWood = requiredWood;

        this.cost = 0.0;
        this.amountGold = 0;
        this.amountWood = 0;
    }

    /** Construct a GameState from another GameState.
     * Useful for cloning an existing GameState and updating state variables.
     *
     * @param parent the state being cloned/updated.
     */
    public GameState(GameState parent) {
        this.state = parent.state;
        this.playerNum = parent.playerNum;
        this.requiredGold = parent.requiredGold;
        this.requiredWood = parent.requiredWood;

        this.townhallPosition = parent.townhallPosition;

        this.peasants = copyPeasants(parent.peasants);
        this.peasant = this.peasants.get(parent.peasant.getId()); // FIXME- PA3 single peasant legacy code (can delete when peasant var is totaly gone)

        this.resources = parent.resources;
        this.resourcePositions = parent.resourcePositions;

        this.cost = parent.cost;
        this.amountGold = parent.amountGold;
        this.amountWood = parent.amountWood;
        this.amountFood = parent.amountFood;

        this.plan = new ArrayList<>(parent.plan);
    }

    /**
     * @param oldPeasants parent GameState list of peasants
     * @return refreshed list of peasants for new GameState
     */
    private HashMap<Integer, Peasant> copyPeasants(HashMap<Integer, Peasant> oldPeasants) {
        HashMap<Integer, Peasant> peasants = new HashMap<>(8);
        for(Peasant old : oldPeasants.values()) {
            Peasant newPeasant = new Peasant(old);
            peasants.put(newPeasant.getId(), newPeasant);
        }
        return peasants;
    }

    /**
     * Write the function that computes the current cost to get to this node. This is combined with your heuristic to
     * determine which actions/states are better to explore.
     *
     * @return The current cost to reach this goal
     */
    public double getCost() {
        return cost; //cost variable is incremented on each action call
    }

    public int getRequiredGold(){
        return requiredGold;
    }

    public int getRequiredWood(){
        return requiredWood;
    }

    public int getAmountGold(){
        return amountGold;
    }

    public int getAmountWood(){
        return amountWood;
    }

    public int getPeasantId(){
        return peasant.getId();
    }

    public Position getTownhallPosition(){
        return townhallPosition;
    }

    public  ArrayList<StripsAction> getPlan(){
        return plan;
    }

    public int getRemainingFood(){
        return amountFood;
    }

    /**
     * Gives the ID that you need to create a new peasant, even though the new peasant will actually have a
     * different ID ¯\_(ツ)_/¯
     * @return the number 26
     */
    public int getNewPeasantCreationID(){
        return state.getTemplate(playerNum, "Peasant").getID();
    }

    /**
     * Unlike in the first A* assignment there are many possible goal states. As long as the wood and gold requirements
     * are met the peasants can be at any location and the capacities of the resource locations can be anything. Use
     * this function to check if the goal conditions are met and return true if they are.
     *
     * @return true if the goal conditions are met in this instance of game state.
     */
    public boolean isGoal() {
        return amountGold == requiredGold && amountWood == requiredWood;
    }

    /**
     * The branching factor of this search graph are much higher than the planning. Generate all of the possible
     * successor states and their associated actions in this method.
     *
     * @return A list of the possible successor states and their associated actions
     */
    public List<GameState> generateChildren() {
        List<GameState> children = new ArrayList<>();
        GameState childState = new GameState(this);

        BuildPeasant buildPeasant = new BuildPeasant(townhallID, this);
        if (buildPeasant.preconditionsMet(childState)) {
            GameState buildChildState = new GameState(this);
            buildPeasant.apply(buildChildState);
            children.add(buildChildState);
        }

        for(Peasant peasant : this.peasants.values()) {
            if (peasant.hasResource()) {
                if (townhallPosition.equals(peasant.getPosition())) {
                    DepositAction action = new DepositAction(childState.peasant, this);
                    if (action.preconditionsMet(childState)) {
                        action.apply(childState);
                        children.add(childState);
                    }
                } else {
                    MoveAction action = new MoveAction(childState.peasant, townhallPosition, this);
                    if (action.preconditionsMet(childState)) {
                        action.apply(childState);
                        children.add(childState);
                    }
                }
            } else if (peasantCanHarvest(peasant)) {
                for (Resource resource : this.resources.values()) {
                    HarvestAction action = new HarvestAction(childState.peasant, resource, this);
                    if (action.preconditionsMet(childState)) {
                        action.apply(childState);
                        children.add(childState);
                    }
                }
            } else {
                for (Resource resource : this.resources.values()) {
                    GameState childOfChildState = new GameState(this);
                    MoveAction action = new MoveAction(childOfChildState.peasant, resource.getPosition(), this);
                    if (action.preconditionsMet(childState, resource)) {
                        action.apply(childOfChildState);
                        children.add(childOfChildState);
                    }
                }
            }
        }

        return children;
    }

    /**
     * Write your heuristic function here. Remember this must be admissible for the properties of A* to hold. If you
     * can come up with an easy way of computing a consistent heuristic that is even better, but not strictly necessary.
     * <p>
     * Add a description here in your submission explaining your heuristic.
     *
     * This heuristic accounts for the remaining resources to be mined as well as the peasant's carrying capacity.
     *
     * @return The value estimated remaining cost to reach a goal state from this state.
     */
    public double heuristic() {
        double goldLeft = (requiredGold - amountGold)*RESOURCES_LEFT_WEIGHT;
        double woodLeft = (requiredWood - amountWood)*RESOURCES_LEFT_WEIGHT;
        double peasantCapacity = (100 - peasant.getGold() - peasant.getWood())*PEASANT_CAPACITY_WEIGHT;
        double numPeasants = peasants.size()*NUM_PEASANTS_WEIGHT;
        return goldLeft + woodLeft + peasantCapacity + numPeasants;
    }

    /**
     * This is necessary to use your state in the Java priority queue. See the official priority queue and Comparable
     * interface documentation to learn how this function should work.
     *
     * @param o The other game state to compare
     * @return 1 if this state costs more than the other, 0 if equal, -1 otherwise
     */
    @Override
    public int compareTo(GameState o) {
        return (int)(this.heuristic() - o.heuristic());
    }

    /**
     * This will be necessary to use the GameState as a key in a Set or Map.
     *
     * @param o The game state to compare
     * @return True if this state equals the other state, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }

        if (o instanceof GameState) {
            return this.amountGold == ((GameState) o).amountGold
                    && this.amountWood == ((GameState) o).amountWood
                    && this.peasant.equals(((GameState) o).peasant)
                    && this.heuristic() == ((GameState) o).heuristic()
                    && this.resources.equals(((GameState) o).resources)
                    && this.peasants.equals(((GameState) o).peasants); //FIXME idk if works
        }

        return false;
    }

    /**
     * This is necessary to use the GameState as a key in a HashSet or HashMap. Remember that if two objects are
     * equal they should hash to the same value.
     *
     * @return An integer hashcode that is equal for equal states.
     */
    @Override
    public int hashCode() {
        int result = 1;
        result = result*31 + peasant.getPosition().x;
        result = result*31 + peasant.getPosition().y;
        result = result*31 + amountGold;
        result = result*31 + amountWood;
        result = result*31 + peasant.getGold() + peasant.getWood();
        result = result*31 + peasants.size(); //FIXME idk if works
        return result;
    }

    public void addPlan(StripsAction action) {
        this.cost += action.getCost();
        plan.add(action);
    }

    /**
     * @return a list of Actions to be implemented.
     */
    public Stack<StripsAction> getGamePlan() {
        Stack<StripsAction> plan = new Stack<>();
        for (int i = getPlan().size() - 1; i > -1; i--) {
            plan.push(this.plan.get(i));
        }
        return plan;
    }

    /**
     * Updates the amount remaining in the mine/forest
     * Increases the peasant's inventory.
     *
     * @param resource the target resource to be harvested.
     */
    public void applyHarvestAction(Resource resource) {
        int amountCollected = Math.min(resource.getAmount(), 100);
        if (resource.isWood()) {
            peasant.addWood(amountCollected);
        }
        if (resource.isGold()) {
            peasant.addGold(amountCollected);
        }
        resource.collect(amountCollected);
    }

    /**
     * Moves the peasant to a new location.
     *
     * @param position the destination coordinates.
     */
    public void applyMoveAction(Position position){
        peasant.setPosition(position);
    }

    /**
     * Updates the peasant unit's inventory and the game's ledger.
     */
    public void applyDepositAction(){
        if(peasant.getWood() > 0){
            this.amountWood += peasant.getWood();
            peasant.addWood(peasant.getWood() * -1);
        }
        if(peasant.getGold() > 0){
            this.amountGold += peasant.getGold();
            peasant.addGold(peasant.getGold() * -1);
        }
    }

    public void applyBuildAction(){
        this.amountGold -= GOLD_TO_BUILD;
        this.amountFood--;
        int peasantID = peasants.size() + 8; //it is what it is because it is :)
        Peasant peasant = new Peasant(peasantID, new Position(townhallPosition));
        this.peasants.put(peasantID, peasant);
    }


    public void applyMoveKAction(ArrayList<Peasant> peasants, Position position){
        for(Peasant p : peasants) { //FIXME we might need to keep track of IDs instead of peasant objects for all the K actions
            p.setPosition(position);
        }
    }
    public void applyHarvestKAction(ArrayList<Peasant> peasants, Resource resource) {
        for(Peasant p : peasants) {
            int amountCollected = Math.min(resource.getAmount(), 100);
            if (resource.isWood()) {
                peasant.addWood(amountCollected);
            }
            if (resource.isGold()) {
                peasant.addGold(amountCollected);
            }
            resource.collect(amountCollected);
        }
    }
    public void applyDepositKAction(ArrayList<Peasant> peasants) {
        for(Peasant p : peasants) {
            if (p.getWood() > 0) {
                this.amountWood += p.getWood();
                p.addWood(p.getWood() * -1);
            }
            if (p.getGold() > 0) {
                this.amountGold += p.getGold();
                p.addGold(p.getGold() * -1);
            }
        }
    }

    /**
     * @param peasant the peasant in question.
     * @return the ability of a peasant unit to harvest resources.
     */
    private boolean peasantCanHarvest(Peasant peasant) {
        if (resourcePositions.contains(peasant.getPosition())){
            return this.resources.values()
                    .stream()
                    .filter(resource -> resource.getPosition().equals(peasant.getPosition()))
                    .findFirst()
                    .get().getAmount() > 0;
        }
        return false;
    }

}