package players.mcts;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.sushigo.SGGameState;
import games.sushigo.actions.ChopSticksAction;
import games.sushigo.actions.PlayCardAction;
import games.sushigo.cards.SGCard;
import players.PlayerConstants;
import players.simple.RandomPlayer;
import utilities.ElapsedCpuTimer;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static players.PlayerConstants.*;
import static utilities.Utils.noise;

class BasicPruningTreeNode {
    // Root node of tree
    BasicPruningTreeNode root;
    // Parent of this node
    BasicPruningTreeNode parent;
    // Children of this node
    Map<AbstractAction, BasicPruningTreeNode> children = new HashMap<>();
    // Depth of this node
    final int depth;

    // Total value of this node
    private double totValue;
    // Number of visits
    private int nVisits;
    // Number of FM calls and State copies up until this node
    private int fmCallsCount;
    // Parameters guiding the search
    private BasicPruningMCTSPlayer player;
    private Random rnd;
    private RandomPlayer randomPlayer = new RandomPlayer();

    // State in this node (closed loop)
    private AbstractGameState state;

    protected BasicPruningTreeNode(BasicPruningMCTSPlayer player, BasicPruningTreeNode parent, AbstractGameState state, Random rnd) {
        this.player = player;
        this.fmCallsCount = 0;
        this.parent = parent;
        this.root = parent == null ? this : parent.root;
        totValue = 0.0;
        setState(state);
        if (parent != null) {
            depth = parent.depth + 1;
        } else {
            depth = 0;
        }
        this.rnd = rnd;
    }

    /**
     * Performs full MCTS search, using the defined budget limits.
     */
    void mctsSearch() {

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = player.params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        if (player.params.budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(player.params.budget);
        }

        // Tracking number of iterations for iteration budget
        int numIters = 0;

        boolean stop = false;

        while (!stop) {
            // New timer for this iteration
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // Selection + expansion: navigate tree until a node not fully expanded is found, add a new node to the tree
            BasicPruningTreeNode selected = treePolicy();
            // Monte carlo rollout: return value of MC rollout from the newly added node
            double delta = selected.rollOut();
            // Back up the value of the rollout through the tree
            selected.backUp(delta);
            // Finished iteration
            numIters++;

            // Check stopping condition
            PlayerConstants budgetType = player.params.budgetType;
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = numIters >= player.params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = fmCallsCount > player.params.budget;
            }
        }
    }

    /**
     * Selection + expansion steps.
     * - Tree is traversed until a node not fully expanded is found.
     * - A new child of this node is added to the tree.
     *
     * @return - new node added to the tree.
     */
    private BasicPruningTreeNode treePolicy() {

        BasicPruningTreeNode cur = this;

        // Keep iterating while the state reached is not terminal and the depth of the tree is not exceeded
        while (cur.state.isNotTerminal() && cur.depth < player.params.maxTreeDepth) {
            if (!cur.unexpandedActions().isEmpty()) {
                // We have an unexpanded action
                cur = cur.expand();
                return cur;
            } else {
                // Move to next child given by UCT function
                AbstractAction actionChosen = cur.ucb();
                cur = cur.children.get(actionChosen);
            }
        }

        return cur;
    }


    private void setState(AbstractGameState newState) {
        state = newState;
        for (AbstractAction action : player.getForwardModel().computeAvailableActions(state)) {
            children.put(action, null); // mark a new node to be expanded
        }
    }

    /**
     * @return A list of the unexpanded Actions from this State
     */
    private List<AbstractAction> unexpandedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).collect(toList());
    }

    /**
     * Expands the node by creating a new random child node and adding to the tree.
     *
     * @return - new child node.
     */
    private BasicPruningTreeNode expand() {
        // Find random child not already created
        Random r = new Random(player.params.getRandomSeed());
        // pick a random unchosen action
        List<AbstractAction> notChosen = unexpandedActions();
        AbstractAction chosen = notChosen.get(r.nextInt(notChosen.size()));

        // copy the current state and advance it using the chosen action
        // we first copy the action so that the one stored in the node will not have any state changes
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // then instantiate a new node
        BasicPruningTreeNode tn = new BasicPruningTreeNode(player, this, nextState, rnd);
        children.put(chosen, tn);
        return tn;
    }

    /**
     * Advance the current game state with the given action, count the FM call and compute the next available actions.
     *
     * @param gs  - current game state
     * @param act - action to apply
     */
    private void advance(AbstractGameState gs, AbstractAction act) {
        player.getForwardModel().next(gs, act);
        root.fmCallsCount++;
    }

    private AbstractAction ucb() {
        // Find child with highest UCB value, maximising for ourselves and minimizing for opponent
        AbstractAction bestAction = null;
        double bestValue = -Double.MAX_VALUE;

        for (AbstractAction action : children.keySet()) {
            BasicPruningTreeNode child = children.get(action);
            if (child == null)
                throw new AssertionError("Should not be here");

            // Find child value
            double hvVal = child.totValue;
            double childValue = hvVal / (child.nVisits + player.params.epsilon);

            // default to standard UCB
            double explorationTerm = player.params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + player.params.epsilon));
            // unless we are using a variant

            // Find 'UCB' value
            // If 'we' are taking a turn we use classic UCB
            // If it is an opponent's turn, then we assume they are trying to minimise our score (with exploration)
            boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
            double uctValue = iAmMoving ? childValue : -childValue;
            uctValue += explorationTerm;

            // Apply small noise to break ties randomly
            uctValue = noise(uctValue, player.params.epsilon, player.rnd.nextDouble());

            SGGameState sggs = (SGGameState) state;
            int currentPlayerId = sggs.getCurrentPlayer();
            int playerCount = sggs.getNPlayers();
            boolean isFirstRound = false;
            for (int i = 0; i < playerCount; i++) {
                if (i == currentPlayerId) {
                    continue;
                } else {
                    if (!sggs.hasSeenHand(currentPlayerId, i)) {
                        isFirstRound = true;
                    }
                }
            }
            if (!isFirstRound) {
                try {
                    PlayCardAction playCardAction = (PlayCardAction)action;
                    SGCard.SGCardType cardType = playCardAction.cardType;

                    ArrayList<SGCard> availableCards = new ArrayList<>();
                    for (int i = 0; i < playerCount; i++) {
                        availableCards.addAll(sggs.getPlayerDeck(i).getComponents());
                    }

                    if (cardType == SGCard.SGCardType.Maki_1 || cardType == SGCard.SGCardType.Maki_2 || cardType == SGCard.SGCardType.Maki_3) {
                        if (!canWinMakiRace(sggs, currentPlayerId, playerCount)) {
                            uctValue = -Double.MAX_VALUE + 1;
                        }
                    } else if (cardType == SGCard.SGCardType.Wasabi) {
                        if (availableCards.stream().noneMatch(c -> c.type == SGCard.SGCardType.EggNigiri || c.type == SGCard.SGCardType.SalmonNigiri || c.type == SGCard.SGCardType.SquidNigiri)) {
                            uctValue = -Double.MAX_VALUE + 1;
                        }
                    } else if (cardType == SGCard.SGCardType.Tempura) {
                        if (availableCards.stream().filter(c -> c.type == SGCard.SGCardType.Tempura).count() == 1) {
                            boolean noOneCanMakeTempuraSet = true;
                            for (int i = 0; i < playerCount; i++) {
                                if (sggs.getPlayerTempuraAmount(i) % 2 == 1) {
                                    noOneCanMakeTempuraSet = false;
                                }
                            }
                            if (noOneCanMakeTempuraSet) {
                                uctValue = -Double.MAX_VALUE + 1;
                            }
                        }
                    } else if (cardType == SGCard.SGCardType.Sashimi) {
                        int sashimiCount = (int) availableCards.stream().filter(c -> c.type == SGCard.SGCardType.Sashimi).count();
                        if (sashimiCount == 1) {
                            boolean noOneCanMakeSashimiSet = true;
                            for (int i = 0; i < playerCount; i++) {
                                if (sggs.getPlayerSashimiAmount(i) % 3 == 2) {
                                    noOneCanMakeSashimiSet = false;
                                }
                            }
                            if (noOneCanMakeSashimiSet) {
                                uctValue = -Double.MAX_VALUE + 1;
                            }
                        } else if (sashimiCount == 2) {
                            boolean noOneCanMakeSashimiSet = true;
                            for (int i = 0; i < playerCount; i++) {
                                if (sggs.getPlayerSashimiAmount(i) % 3 == 2 || sggs.getPlayerSashimiAmount(i) % 3 == 1) {
                                    noOneCanMakeSashimiSet = false;
                                }
                            }
                            if (noOneCanMakeSashimiSet) {
                                uctValue = -Double.MAX_VALUE + 1;
                            }
                        }
                    } else if (cardType == SGCard.SGCardType.Chopsticks) {
                        int cardsLeft = sggs.getPlayerDeck(currentPlayerId).getSize();
                        if (cardsLeft == 2) {
                            uctValue = -Double.MAX_VALUE + 1;
                        }
                    }
                } catch (ClassCastException e) {

                }
            }

            // Assign value
            if (bestAction == null || uctValue > bestValue) {
                bestAction = action;
                bestValue = uctValue;
            }
        }

        if (bestAction == null)
            throw new AssertionError("We have a null value in UCT : shouldn't really happen!");

        root.fmCallsCount++;  // log one iteration complete
        return bestAction;
    }

    private boolean canWinMakiRace(SGGameState sggs, int currentPlayerId, int playerCount) {
        ArrayList<Integer> playerMakiScores = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            playerMakiScores.add(0);
        }
        for (int i = 0; i < playerCount; i++) {
            List<SGCard> playerPlayedCards = sggs.getPlayerField(i).getComponents();
            for (SGCard card : playerPlayedCards) {
                int makiScore = playerMakiScores.get(i);
                switch (card.type) {
                    case Maki_1:
                        playerMakiScores.set(i, makiScore + 1);
                        break;
                    case Maki_2:
                        playerMakiScores.set(i, makiScore + 2);
                        break;
                    case Maki_3:
                        playerMakiScores.set(i, makiScore + 3);
                        break;
                }
            }
        }
        ArrayList<SGCard> availableMakis = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            availableMakis.addAll(sggs.getPlayerDeck(i).getComponents());
            availableMakis.removeIf(sgCard -> !(sgCard.type == SGCard.SGCardType.Maki_1 || sgCard.type == SGCard.SGCardType.Maki_2 || sgCard.type == SGCard.SGCardType.Maki_3));
        }
        int makiScore = playerMakiScores.get(currentPlayerId);
        for (SGCard card : availableMakis) {
            switch (card.type) {
                case Maki_1:
                    playerMakiScores.set(currentPlayerId, makiScore + 1);
                    break;
                case Maki_2:
                    playerMakiScores.set(currentPlayerId, makiScore + 2);
                    break;
                case Maki_3:
                    playerMakiScores.set(currentPlayerId, makiScore + 3);
                    break;
            }
        }
        int playersAhead = 0;
        for (int i = 0; i < playerCount; i++) {
            if (i == currentPlayerId) {
                continue;
            } else {
                if (playerMakiScores.get(i) > playerMakiScores.get(currentPlayerId)) {
                    playersAhead++;
                }
            }
        }
        return playersAhead < 2;
    }

    /**
     * Perform a Monte Carlo rollout from this node.
     *
     * @return - value of rollout.
     */
    private double rollOut() {
        int rolloutDepth = 0; // counting from end of tree

        // If rollouts are enabled, select actions for the rollout in line with the rollout policy
        AbstractGameState rolloutState = state.copy();
        if (player.params.rolloutLength > 0) {
            while (!finishRollout(rolloutState, rolloutDepth)) {
                List<AbstractAction> availableActions = player.getForwardModel().computeAvailableActions(rolloutState);
                AbstractAction next = randomPlayer.getAction(rolloutState, availableActions);
                advance(rolloutState, next);
                rolloutDepth++;
            }
        }
        // Evaluate final state and return normalised score
        double value = player.params.getHeuristic().evaluateState(rolloutState, player.getPlayerID());
        if (Double.isNaN(value))
            throw new AssertionError("Illegal heuristic value - should be a number");
        return value;
    }

    /**
     * Checks if rollout is finished. Rollouts end on maximum length, or if game ended.
     *
     * @param rollerState - current state
     * @param depth       - current depth
     * @return - true if rollout finished, false otherwise
     */
    private boolean finishRollout(AbstractGameState rollerState, int depth) {
        if (depth >= player.params.rolloutLength)
            return true;

        // End of game
        return !rollerState.isNotTerminal();
    }

    /**
     * Back up the value of the child through all parents. Increase number of visits and total value.
     *
     * @param result - value of rollout to backup
     */
    private void backUp(double result) {
        BasicPruningTreeNode n = this;
        while (n != null) {
            n.nVisits++;
            n.totValue += result;
            n = n.parent;
        }
    }

    /**
     * Calculates the best action from the root according to the most visited node
     *
     * @return - the best AbstractAction
     */
    AbstractAction bestAction() {

        double bestValue = -Double.MAX_VALUE;
        AbstractAction bestAction = null;

        for (AbstractAction action : children.keySet()) {
            if (children.get(action) != null) {
                BasicPruningTreeNode node = children.get(action);
                double childValue = node.nVisits;

                // Apply small noise to break ties randomly
                childValue = noise(childValue, player.params.epsilon, player.rnd.nextDouble());

                // Save best value (highest visit count)
                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = action;
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no selection made.");
        }

        return bestAction;
    }

}
