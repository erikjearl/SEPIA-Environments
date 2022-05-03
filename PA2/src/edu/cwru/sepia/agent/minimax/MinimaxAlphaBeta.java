package edu.cwru.sepia.agent.minimax;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MinimaxAlphaBeta extends Agent {

    private final int numPlys;

    public MinimaxAlphaBeta(int playernum, String[] args) {
        super(playernum);

        if (args.length < 1) {
            System.err.println("You must specify the number of plys");
            System.exit(1);
        }

        numPlys = Integer.parseInt(args[0]);
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        GameStateChild bestChild = alphaBetaSearch(new GameStateChild(newstate),
                numPlys,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY);

        return bestChild.action;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {

    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this.
     *
     * This is the main entry point to the alpha beta search. Refer to the slides, assignment description
     * and book for more information.
     *
     * Try to keep the logic in this function as abstract as possible (i.e. move as much SEPIA specific
     * code into other functions and methods)
     *
     * @param node The action and state to search from
     * @param depth The remaining number of plys under this node
     * @param alpha The current best value for the maximizing node from this node to the root
     * @param beta The current best value for the minimizing node from this node to the root
     * @return The best child of this node with updated values
     */
    public GameStateChild alphaBetaSearch(GameStateChild node, int depth, double alpha, double beta) {
        double value = maxValue(node, depth, alpha, beta);
        return valueToState(value, node);
    }


    /**
     * Max Node helper function
     * Implements alpha-beta logic for maximizing state value
     *
     * @param node
     * @param depth
     * @param alpha
     * @param beta
     * @return maximum value of the best reachable state
     */
    public double maxValue(GameStateChild node, int depth, double alpha, double beta) {
        List<GameStateChild> children = node.state.getChildren();
        if (depth <= 0 || children.size() == 0) {
            return node.state.getUtility();
        }

        double bestValue = -Double.MAX_VALUE; //temp value at negative infinity
        for (GameStateChild child : orderChildrenWithHeuristics(children)) { //for each child of node (possible actions)
            double testValue = minValue(child, decrement(depth), alpha, beta);
            bestValue = Math.max(bestValue, testValue);
            alpha = Math.max(alpha, bestValue);
        }
        return bestValue;
    }

    /**
     * Min Node helper function
     * Implements alpha-beta logic for minimizing state value
     *
     * @param node
     * @param depth
     * @param alpha
     * @param beta
     * @return minimum value of the best reachable state
     */
    public double minValue(GameStateChild node, int depth, double alpha, double beta) {
        List<GameStateChild> children = node.state.getChildren();
        if (depth <= 0 || children.size() == 0) {
            return node.state.getUtility();
        }

        double bestValue = Double.MAX_VALUE; //temp value at infinity
        for (GameStateChild child : orderChildrenWithHeuristics(children)) {
            double testValue = maxValue(child, decrement(depth), alpha, beta);
            bestValue = Math.min(bestValue, testValue);
            beta = Math.min(beta, bestValue);
        }
        return bestValue;
    }

    /**
     * Helper function to find the state with the optimized value amount
     *
     * @param node starting node for the alphaBetaSearch
     * @param value the bestValue found in alphaBetaSearch
     * @return node that has the value found in alphaBetaSearch
     */
    private GameStateChild valueToState(double value, GameStateChild node) {
        List<GameStateChild> children = node.state.getChildren();
        if (children.isEmpty()) {
            return node;
        }
        for (GameStateChild child : children) {
            if (child.state.getUtility() == value) {
                return child;
            }
        }
        return children.get(0);
    }


    /**
     * You will implement this.
     *
     * Given a list of children you will order them according to heuristics you make up.
     * See the assignment description for suggestions on heuristics to use when sorting.
     *
     * Use this function inside your alphaBetaSearch method.
     *
     * Include a good comment about what your heuristics are and why you chose them.
     *
     * Function to order child states based on heuristic of state utility
     *
     * @param children a list of GameStateChild
     * @return The list of children sorted by our heuristic.
     */
    public List<GameStateChild> orderChildrenWithHeuristics(List<GameStateChild> children) {
        Collections.sort(children, new GameStateChildComparator());
        return children;
    }

    /**
     * Implements Comparator<GameStateChild> to compare the two states.
     * This is useful as we can call Collections.sort() on a GameStateChild to order heuristics.
     */
    public class GameStateChildComparator implements Comparator<GameStateChild> {
        /**
         * A function override to force the Comparator to use the GameStateChild's state utility.
         *
         * @param o1 one GameStateChild
         * @param o2 another GameStateChild
         * @return an int value for easily ordering a heuristic
         */
        @Override
        public int compare(GameStateChild o1, GameStateChild o2) {
            if (o1.state.getUtility() < o2.state.getUtility()) {
                return 1;
            } else if (o1.state.getUtility() > o2.state.getUtility()) {
                return -1;
            }
            return 0;
        }
    }

    private int decrement(int n){
        return Math.min(n, 1) - 1;
    }

}
