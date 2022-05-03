package edu.cwru.sepia.agent;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class AstarAgent extends Agent {

    class MapLocation
    {
        public int x, y;

        public MapLocation(int x, int y, MapLocation cameFrom, float cost)
        {
            this.x = x;
            this.y = y;
        }
    }

    Stack<MapLocation> path;
    int footmanID, townhallID, enemyFootmanID;
    MapLocation nextLoc;

    private long totalPlanTime = 0; // nsecs
    private long totalExecutionTime = 0; //nsecs

    public AstarAgent(int playernum)
    {
        super(playernum);

        System.out.println("Constructed AstarAgent");
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        // get the footman location
        List<Integer> unitIDs = newstate.getUnitIds(playernum);

        if(unitIDs.size() == 0)
        {
            System.err.println("No units found!");
            return null;
        }

        footmanID = unitIDs.get(0);

        // double check that this is a footman
        if(!newstate.getUnit(footmanID).getTemplateView().getName().equals("Footman"))
        {
            System.err.println("Footman unit not found");
            return null;
        }

        // find the enemy playernum
        Integer[] playerNums = newstate.getPlayerNumbers();
        int enemyPlayerNum = -1;
        for(Integer playerNum : playerNums)
        {
            if(playerNum != playernum) {
                enemyPlayerNum = playerNum;
                break;
            }
        }

        if(enemyPlayerNum == -1)
        {
            System.err.println("Failed to get enemy playernumber");
            return null;
        }

        // find the townhall ID
        List<Integer> enemyUnitIDs = newstate.getUnitIds(enemyPlayerNum);

        if(enemyUnitIDs.size() == 0)
        {
            System.err.println("Failed to find enemy units");
            return null;
        }

        townhallID = -1;
        enemyFootmanID = -1;
        for(Integer unitID : enemyUnitIDs)
        {
            Unit.UnitView tempUnit = newstate.getUnit(unitID);
            String unitType = tempUnit.getTemplateView().getName().toLowerCase();
            if(unitType.equals("townhall"))
            {
                townhallID = unitID;
            }
            else if(unitType.equals("footman"))
            {
                enemyFootmanID = unitID;
            }
            else
            {
                System.err.println("Unknown unit type");
            }
        }

        if(townhallID == -1) {
            System.err.println("Error: Couldn't find townhall");
            return null;
        }

        long startTime = System.nanoTime();
        path = findPath(newstate);
        totalPlanTime += System.nanoTime() - startTime;

        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        long startTime = System.nanoTime();
        long planTime = 0;

        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        if(shouldReplanPath(newstate, statehistory, path)) {
            long planStartTime = System.nanoTime();
            path = findPath(newstate);
            planTime = System.nanoTime() - planStartTime;
            totalPlanTime += planTime;
        }

        Unit.UnitView footmanUnit = newstate.getUnit(footmanID);

        int footmanX = footmanUnit.getXPosition();
        int footmanY = footmanUnit.getYPosition();

        if(!path.empty() && (nextLoc == null || (footmanX == nextLoc.x && footmanY == nextLoc.y))) {

            // stat moving to the next step in the path
            nextLoc = path.pop();

            System.out.println("Moving to (" + nextLoc.x + ", " + nextLoc.y + ")");
        }

        if(nextLoc != null && (footmanX != nextLoc.x || footmanY != nextLoc.y))
        {
            int xDiff = nextLoc.x - footmanX;
            int yDiff = nextLoc.y - footmanY;

            // figure out the direction the footman needs to move in
            Direction nextDirection = getNextDirection(xDiff, yDiff);

            actions.put(footmanID, Action.createPrimitiveMove(footmanID, nextDirection));
        } else {
            Unit.UnitView townhallUnit = newstate.getUnit(townhallID);

            // if townhall was destroyed on the last turn
            if(townhallUnit == null) {
                terminalStep(newstate, statehistory);
                return actions;
            }

            if(Math.abs(footmanX - townhallUnit.getXPosition()) > 1 ||
                    Math.abs(footmanY - townhallUnit.getYPosition()) > 1)
            {
                System.err.println("Invalid plan. Cannot attack townhall");
                totalExecutionTime += System.nanoTime() - startTime - planTime;
                return actions;
            }
            else {
                System.out.println("Attacking TownHall");
                // if no more movements in the planned path then attack
                actions.put(footmanID, Action.createPrimitiveAttack(footmanID, townhallID));
            }
        }

        totalExecutionTime += System.nanoTime() - startTime - planTime;
        return actions;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {
        System.out.println("Total turns: " + newstate.getTurnNumber());
        System.out.println("Total planning time: " + totalPlanTime/1e9);
        System.out.println("Total execution time: " + totalExecutionTime/1e9);
        System.out.println("Total time: " + (totalExecutionTime + totalPlanTime)/1e9);
    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this method.
     *
     * This method should return true when the path needs to be replanned
     * and false otherwise. This will be necessary on the dynamic map where the
     * footman will move to block your unit.
     *
     * You can check the position of the enemy footman with the following code:
     * state.getUnit(enemyFootmanID).getXPosition() or .getYPosition().
     *
     * There are more examples of getting the positions of objects in SEPIA in the findPath method.
     *
     * @param state
     * @param history
     * @param currentPath
     * @return a boolean value to give the red/green light on whether a path should be replanned.
     */
    private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath)
    {
        Unit.UnitView enemy = state.getUnit(enemyFootmanID);
        if(enemy == null) return false;

        // make sure the agent is within two tiles of the enemy
        if(!currentPath.empty()) {
            MapLocation currentPos = currentPath.lastElement();
            if(
                    Math.abs(enemy.getXPosition() - currentPos.x) > 2
                            || Math.abs(enemy.getYPosition() - currentPos.y) > 2
            ) return false;
        }

        // check to see if the enemy lies on any part of the current planned path
        ListIterator<MapLocation> iterator = currentPath.listIterator();
        while(iterator.hasNext()) {
            MapLocation location = iterator.next();

            if(location.x == enemy.getXPosition() && location.y == enemy.getYPosition()) {
                return true;
            }
        }

        return false;
    }

    /**
     * This method is implemented for you. You should look at it to see examples of
     * how to find units and resources in Sepia.
     *
     * @param state
     * @return
     */
    private Stack<MapLocation> findPath(State.StateView state)
    {
        Unit.UnitView townhallUnit = state.getUnit(townhallID);
        Unit.UnitView footmanUnit = state.getUnit(footmanID);

        MapLocation startLoc = new MapLocation(footmanUnit.getXPosition(), footmanUnit.getYPosition(), null, 0);

        MapLocation goalLoc = new MapLocation(townhallUnit.getXPosition(), townhallUnit.getYPosition(), null, 0);

        MapLocation footmanLoc = null;
        if(enemyFootmanID != -1) {
            Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
            footmanLoc = new MapLocation(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition(), null, 0);
        }

        // get resource locations
        List<Integer> resourceIDs = state.getAllResourceIds();
        Set<MapLocation> resourceLocations = new HashSet<MapLocation>();
        for(Integer resourceID : resourceIDs)
        {
            ResourceNode.ResourceView resource = state.getResourceNode(resourceID);

            resourceLocations.add(new MapLocation(resource.getXPosition(), resource.getYPosition(), null, 0));
        }

        return AstarSearch(startLoc, goalLoc, state.getXExtent(), state.getYExtent(), footmanLoc, resourceLocations);
    }
    /**
     * This is the method you will implement for the assignment. Your implementation
     * will use the A* algorithm to compute the optimum path from the start position to
     * a position adjacent to the goal position.
     *
     * Therefore your you need to find some possible adjacent steps which are in range
     * and are not trees or the enemy footman.
     * Hint: Set<MapLocation> resourceLocations contains the locations of trees
     *
     * You will return a Stack of positions with the top of the stack being the first space to move to
     * and the bottom of the stack being the last space to move to. If there is no path to the townhall
     * then return null from the method and the agent will print a message and do nothing.
     * The code to execute the plan is provided for you in the middleStep method.
     *
     * As an example consider the following simple map
     *
     * F - - - -
     * x x x - x
     * H - - - -
     *
     * F is the footman
     * H is the townhall
     * x's are occupied spaces
     *
     * xExtent would be 5 for this map with valid X coordinates in the range of [0, 4]
     * x=0 is the left most column and x=4 is the right most column
     *
     * yExtent would be 3 for this map with valid Y coordinates in the range of [0, 2]
     * y=0 is the top most row and y=2 is the bottom most row
     *
     * resourceLocations would be {(0,1), (1,1), (2,1), (4,1)}
     *
     * The path would be
     *
     * (1,0)
     * (2,0)
     * (3,1)
     * (2,2)
     * (1,2)
     *
     * Notice how the initial footman position and the townhall position are not included in the path stack
     *
     * @param start Starting position of the footman
     * @param goal MapLocation of the townhall
     * @param xExtent Width of the map
     * @param yExtent Height of the map
     * @param resourceLocations Set of positions occupied by resources
     * @return Stack of positions with top of stack representing the first move in plan.
     */
    private Stack<MapLocation> AstarSearch(MapLocation start, MapLocation goal, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations)
    {
        // initialize stacks to track moves and path
        Stack<MapLocation> bestPath = new Stack<>();
        Stack<MapLocation> openList = new Stack<>();
        Stack<MapLocation> closedList = new Stack<>();

        boolean foundGoal = false;
        MapLocation agentLoc = start;

        // start the algorithm
        openList.add(agentLoc);
        do{
            // add current position to closed list
            closedList.add(agentLoc);
            // all the adjacent squares to the player's current position.
            MapLocation[] possibleMoves = getPossibleMoves(agentLoc);

            MapLocation bestMove = null;

            while(bestMove == null) {
                // initialize the best heuristic value
                float bestHeuristic = Float.MAX_VALUE;

                // iterate through each possible move
                for (MapLocation moveLoc : possibleMoves) {
                    // check if the move is legal
                    if(isLegal(moveLoc, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
                        // check if move is in closedList (already explored)
                        if(!containsLocation(closedList, moveLoc)) {
                            openList.add(moveLoc);
                            // check for a better heuristic value and record it.
                            if(heuristic(moveLoc, goal) < bestHeuristic) {
                                bestHeuristic = heuristic(moveLoc, goal);
                                bestMove = moveLoc;
                            }
                        }
                    }
                }

                // check if backtracking is necessary
                if(bestMove == null) {
                    // remove the most recent step from the path
                    if(!bestPath.isEmpty()) {
                        bestPath.pop();
                    }

                    // move the explorer back a step
                    if(!bestPath.isEmpty()) {
                        agentLoc = bestPath.peek();
                    } else {
                        if(agentLoc != start) {
                            agentLoc = start;
                        } else {
                            // if it tries to backtrack from the starting location, there can be no solution
                            System.out.println("No valid path.");
                            System.exit(0);
                        }
                    }
                    // update the set of possible moves for the new explorer location
                    possibleMoves = getPossibleMoves(agentLoc);
                }
            }

            // add to closed list
            closedList.add(bestMove);

            // set agent's location to the best possible move at hand
            agentLoc = bestMove;

            // clear open list except for the action taken
            openList.clear();
            openList.add(agentLoc);

            // check if goal state was found or add action taken to path
            if(agentLoc.x == goal.x && agentLoc.y == goal.y){
                foundGoal = true;
            }else{
                bestPath.add(agentLoc);
            }
        } while(!foundGoal && !openList.empty());

        // reverse the stack to better display the order of moves
        Stack<MapLocation> result = new Stack<MapLocation>();
        while (!bestPath.isEmpty()) {
            result.push(bestPath.pop());
        }

        // return discovered path
        return result;
    }

    /**
     * Helper function to get new possible moves.
     *
     * @param agentLoc
     * @return all possible moves on the map.
     */
    private MapLocation[] getPossibleMoves(MapLocation agentLoc) {
        MapLocation[] possibleMoves = new MapLocation[8];
        possibleMoves[0] = new MapLocation(agentLoc.x + 1, agentLoc.y + 1, agentLoc, 0);
        possibleMoves[1] = new MapLocation(agentLoc.x + 1, agentLoc.y, agentLoc, 0);
        possibleMoves[2] = new MapLocation(agentLoc.x + 1, agentLoc.y - 1, agentLoc, 0);
        possibleMoves[3] = new MapLocation(agentLoc.x, agentLoc.y + 1, agentLoc, 0);
        possibleMoves[4] = new MapLocation(agentLoc.x, agentLoc.y - 1, agentLoc, 0);
        possibleMoves[5] = new MapLocation(agentLoc.x - 1, agentLoc.y + 1, agentLoc, 0);
        possibleMoves[6] = new MapLocation(agentLoc.x - 1, agentLoc.y, agentLoc, 0);
        possibleMoves[7] = new MapLocation(agentLoc.x - 1, agentLoc.y - 1, agentLoc, 0);
        return possibleMoves;
    }

    /**
     * Helper function to check if a certain location is in the closedList
     *
     * @param closedList
     * @param moveLoc
     * @return a boolean value representing whether a move is possible.
     */
    private boolean containsLocation(Stack<MapLocation> closedList, MapLocation moveLoc) {
        ListIterator<MapLocation> iterator = closedList.listIterator();
        while(iterator.hasNext()){
            MapLocation loc = iterator.next();
            if(loc.x == moveLoc.x && loc.y == moveLoc.y){
                return true;
            }
        }
        return false;
    }

    /**
     * Search heuristic is the shortest straight line path to the goal.
     *
     * @param agentLoc
     * @param goal
     * @return a float distance representing the shortest straight line path.
     */
    private float heuristic(MapLocation agentLoc, MapLocation goal){
        float xPath = (float) Math.abs(agentLoc.x - goal.x);
        float yPath = (float) Math.abs(agentLoc.y - goal.y);
        return Math.max(xPath, yPath);
    }

    /**
     * A helper function is created to check whether a particular move can be made.
     *
     * @param moveLoc
     * @param xExtent
     * @param yExtent
     * @param enemyFootmanLoc
     * @param resourceLocations
     * @return a boolean value representing the legality of a possible move.
     */
    private boolean isLegal(MapLocation moveLoc, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations){
        if(moveLoc == null) return false;
        if(moveLoc.x < 0 || moveLoc.x > xExtent) return false;
        if(moveLoc.y < 0 || moveLoc.y > yExtent) return false;
        if(enemyFootmanLoc != null && moveLoc.x == enemyFootmanLoc.x && moveLoc.y == enemyFootmanLoc.y) return false;
        for (MapLocation resLoc : resourceLocations) {
            if (moveLoc.x == resLoc.x && moveLoc.y == resLoc.y) return false;
        }
        return true;
    }

    /**
     * Primitive actions take a direction (e.g. Direction.NORTH, Direction.NORTHEAST, etc)
     * This converts the difference between the current position and the
     * desired position to a direction.
     *
     * @param xDiff Integer equal to 1, 0 or -1
     * @param yDiff Integer equal to 1, 0 or -1
     * @return A Direction instance (e.g. SOUTHWEST) or null in the case of error
     */
    private Direction getNextDirection(int xDiff, int yDiff) {

        // figure out the direction the footman needs to move in
        if(xDiff == 1 && yDiff == 1)
        {
            return Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            return Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            return Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            return Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            return Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            return Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            return Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            return Direction.NORTHWEST;
        }

        System.err.println("Invalid path. Could not determine direction");
        return null;
    }
}