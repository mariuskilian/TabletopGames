package players.groupJ;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.interfaces.IStateHeuristic;
import players.PlayerConstants;
import players.mcts.MCTSEnums;
import players.mcts.MCTSParams;

import java.util.List;
import java.util.Random;

import static players.mcts.MCTSEnums.OpponentTreePolicy.Paranoid;
import static players.mcts.MCTSEnums.SelectionPolicy.ROBUST;
import static players.mcts.MCTSEnums.Strategies.RANDOM;
import static players.mcts.MCTSEnums.TreePolicy.UCB;

/**
 * This is a simple version of MCTS that may be useful for newcomers to TAG and MCTS-like algorithms
 * It strips out some of the additional configuration of MCTSPlayer. It uses BasicTreeNode in place of
 * SingleTreeNode.
 */
public class GroupJ_BasicMCTSPlayer extends AbstractPlayer {

    Random rnd;
    MCTSParams params;

    public GroupJ_BasicMCTSPlayer() {
        this(System.currentTimeMillis());
    }

    public GroupJ_BasicMCTSPlayer(long seed) {
        this.params = new MCTSParams(seed);
        rnd = new Random(seed);
        setName("Group J BasicMCTS");

        // These parameters can be changed, and will impact the Basic MCTS algorithm
        this.params.K = 1;
        this.params.rolloutLength = 20;
        this.params.maxTreeDepth = 25;
        this.params.epsilon = 1e-6;
        this.params.heuristic = new GroupJ_SGHeuristic();
        this.params.budgetType = PlayerConstants.BUDGET_TIME;
        this.params.budget = 1000;

        // These parameters are ignored by BasicMCTS - if you want to play with these, you'll
        // need to upgrade to MCTSPlayer
        this.params.information = MCTSEnums.Information.Closed_Loop;
        this.params.rolloutType = RANDOM;
        this.params.selectionPolicy = ROBUST;
        this.params.opponentTreePolicy = Paranoid;
        this.params.treePolicy = UCB;
    }

    public GroupJ_BasicMCTSPlayer(MCTSParams params) {
        this.params = params;
        rnd = new Random(params.getRandomSeed());
        setName("Group J BasicMCTS");
    }

    @Override
    public AbstractAction getAction(AbstractGameState gameState, List<AbstractAction> allActions) {
        // Search for best action from the root
        GroupJ_BasicTreeNode root = new GroupJ_BasicTreeNode(this, null, gameState, rnd);

        // mctsSearch does all of the hard work
        root.mctsSearch();

        // Return best action
        return root.bestAction();
    }

    public void setStateHeuristic(IStateHeuristic heuristic) {
        this.params.heuristic = heuristic;
    }


    @Override
    public String toString() {
        return "Group J BasicMCTS";
    }

    @Override
    public GroupJ_BasicMCTSPlayer copy() {
        return this;
    }
}