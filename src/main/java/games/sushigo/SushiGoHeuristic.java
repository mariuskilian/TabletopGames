package games.sushigo;

import core.AbstractGameState;
import core.AbstractParameters;
import core.components.Deck;
import core.interfaces.IStateHeuristic;
import evaluation.TunableParameters;
import games.sushigo.cards.SGCard;

import java.util.*;

import static games.sushigo.cards.SGCard.SGCardType.*;


public class SushiGoHeuristic extends TunableParameters implements IStateHeuristic {

    double singleTempuraValue = 2.5;
    double singleSashimiValue = 10.0/3.0;
    double doubleSashimiValue = 20.0/3.0;
    double wasabiSquidValue = 6;
    double wasabiSalmonValue = 4;
    double wasabiEggValue = 2;
    double winningMakiValue = 6;
    double runnerupMakiValue = 3;
    double mostPuddingsValue = 6;
    double leastPuddingsValue = -6;

    // values for cards that don't have inherent values that are in the game score
    double[] roundModifiers = new double[]{};
    ArrayList<SGCard> availableCards = new ArrayList<SGCard>();
    int nPlayers = 0;

    public SushiGoHeuristic() {
        addTunableParameter("singleTempuraValue", singleTempuraValue);
        addTunableParameter("singleSashimiValue", singleSashimiValue);
        addTunableParameter("doubleSashimiValue", doubleSashimiValue);
        addTunableParameter("wasabiSquidValue", wasabiSquidValue);
        addTunableParameter("wasabiSalmonValue", wasabiSalmonValue);
        addTunableParameter("wasabiEggValue", wasabiEggValue);
        addTunableParameter("winningMakiValue", winningMakiValue);
        addTunableParameter("runnerupMakiValue", runnerupMakiValue);
        addTunableParameter("mostPuddingsValue", mostPuddingsValue);
        addTunableParameter("leastPuddingsValue", leastPuddingsValue);
    }

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        if (!gs.isNotTerminal()) return gs.getPlayerResults()[playerId].value;

        SGGameState sggs = (SGGameState) gs;
        nPlayers = sggs.getNPlayers();
        // Get player game scores
        roundModifiers = new double[nPlayers];
        determineAvailableCards(sggs);
        evaluateUncompletedCards(sggs);
        return evaluateDiff2BestPlayer(sggs, playerId);
    }

    void determineAvailableCards(SGGameState sggs) {
        ArrayList<SGCard> availableCards = new ArrayList<SGCard>();
        for (int i = 0; i < nPlayers; i++) {
            availableCards.addAll(sggs.getPlayerDeck(i).getComponents());
        }
        this.availableCards = availableCards;
    }

    double getModifiedScore(SGGameState sggs, int playerId) {
        double modifierFactor = 0.21;
        return sggs.getGameScore(playerId) + (modifierFactor * roundModifiers[playerId]);
    }

    double evaluateDiff2BestPlayer(SGGameState sggs, int playerId) {
        // Find the best player score (besides current player)
        double bestOtherPlayerScore = -1;
        double currentPlayerScore = getModifiedScore(sggs, playerId);
        double maxScore = currentPlayerScore;
        double minScore = currentPlayerScore;
        for (int i = 0; i < nPlayers; i++) {
            if (i == playerId) continue;
            double playerScore = getModifiedScore(sggs, i);
            if (playerScore > maxScore) maxScore = playerScore;
            if (playerScore < minScore) minScore = playerScore;
            if (playerScore > bestOtherPlayerScore) bestOtherPlayerScore = playerScore;
        }
        // Maximize difference to best other player
        double bestPlayerDif = currentPlayerScore - bestOtherPlayerScore;
        // Dividing by max score gives bigger leads more value, while keeping it relative to current board state
        return bestPlayerDif / Math.max(maxScore, 1);
    }

    void evaluateUncompletedCards(SGGameState sggs) {
        List<Deck<SGCard>> decks = sggs.getPlayerDecks();
        // Receive amount of different card types of each player
        CardCount[] ccs = new CardCount[nPlayers];
        for (int i = 0; i < nPlayers; i++) {
            // Evaluate uncompleted Cards
            CardCount cc = getPlayerCardCount(sggs, i);
            ccs[i] = cc;
            // Get hand of player i
            ArrayList<SGCard> hand = (ArrayList<SGCard>) decks.get(i).getComponents();
            // Evaluate Tempura
            // ... if player has an unpaired tempura on the field
            if (cc.nTempura == 1) {
                // If player has another tempura in hand, tempura modifier can be ignored
                if (hand.stream().anyMatch(c -> c.type == Tempura)) cc.nTempura = 0;
                // If no other tempuras are in hand of any player, tempura modifier can be ignored
                else if (availableCards.stream().noneMatch(c -> c.type == Tempura)) cc.nTempura = 0;
                if (cc.nTempura == 1) roundModifiers[i] += singleTempuraValue;
            }
            // Evaluate Sashimi
            // ... if player has a single sashimi on board (or a 4th)
            if (cc.nSashimi == 1) {
                // If there are still > 2 sashimi available, give modifier
                int availableSashimi = (int) availableCards.stream().filter(c -> c.type == Sashimi).count();
                if (availableSashimi > 2) roundModifiers[i] += singleSashimiValue;
            }
            // ... if player has 2 sashimi on board (or 5, etc.)
            if (cc.nSashimi == 2) {
                // If player has another sashimi in hand, modifier can be ignored
                if (hand.stream().anyMatch(c -> c.type == Sashimi)) cc.nSashimi = 0;
                // If no other sashimi are in play, modifier can be ignored
                else if (availableCards.stream().noneMatch(c -> c.type == Sashimi)) cc.nSashimi = 0;
                if (cc.nSashimi == 2) roundModifiers[i] += doubleSashimiValue;
            }
            // Evaluate Wasabi
            if (cc.nWasabi > 0) {
                // If player has squid nigiri in hand, modifier can be ignored
                if (hand.stream().anyMatch(c -> c.type == SquidNigiri)) ;
                // Otherwise, as long as there are squid nigiri in play, add modifier for squid nigiri
                else if (availableCards.stream().anyMatch(c -> c.type == SquidNigiri))
                    roundModifiers[i] += wasabiSquidValue;
                // Continue the same steps in descending order with salmon nigiri and egg nigiri
                else if (hand.stream().anyMatch(c -> c.type == SalmonNigiri)) ;
                else if (availableCards.stream().anyMatch(c -> c.type == SalmonNigiri))
                    roundModifiers[i] += wasabiSalmonValue;
                else if (hand.stream().anyMatch(c -> c.type == EggNigiri)) ;
                else if (availableCards.stream().anyMatch(c -> c.type == EggNigiri)) roundModifiers[i] += wasabiEggValue;
            }
            // Evaluate Chopsticks
            // ... chopsticks are worth as many rounds as there are left - 1 (as with 1 round left you cannot use them)
            if (cc.nChopsticks > 0) roundModifiers[i] += hand.size() - 1;
            // Evaluate Dumplings
            if (cc.nDumplings > 0 && cc.nDumplings < 5) {
                // Dumplings become worth the highest average amount they can be worth
                // e.g.: 2 dumplings on field, 2 in any hands, means player can have max 4 dumplings, which would be
                //       worth 10 points. Therefore, each dumpling on board is worth 2.5 points, so the 2 dumplings on
                //       board are worth 5 points. Subtract from that the existing 3 points the dumplings give to get
                //       a 2 point modifier.
                int totalNDumplings = cc.nDumplings + (int) availableCards.stream().filter(c -> c.type == Dumpling).count();
                if (totalNDumplings > 5) totalNDumplings = 5;
                int[] score = new int[]{1, 3, 6, 10, 15};
                double pointsToAdd = cc.nDumplings * (score[totalNDumplings - 1] - score[cc.nDumplings - 1]);
                roundModifiers[i] += pointsToAdd;
            }
        }

        // Evaluate Maki
        // Filter ccs list into arrays of pairs [id, nMaki] and sort by number of maki in descending order
        int[][] sortedMaki = Arrays.stream(ccs).map(cc -> new int[]{cc.playerId, cc.nMaki}).sorted(Comparator.comparingInt(pair -> pair[1])).toArray(int[][]::new);
        Collections.reverse(Arrays.asList(sortedMaki));
        // Receive important information to distribute points
        int winningMakiScore = sortedMaki[0][1];
        int nWinnersMaki = (int) Arrays.stream(sortedMaki).filter(pair -> pair[1] == winningMakiScore).count();
        int runnerupMakiScore = (nWinnersMaki == nPlayers) ? 0 : sortedMaki[nWinnersMaki][1];
        int nRunnersupMaki = (nWinnersMaki == nPlayers) ? 0 : (int) Arrays.stream(sortedMaki).filter(pair -> pair[1] == runnerupMakiScore).count();
        // Calculate actual modifier points based on number of players splitting it
        int winningMakiPoints = winningMakiScore > 0 ? (int) winningMakiValue / nWinnersMaki : 0;
        int runnerupMakiPoints = runnerupMakiScore > 0 ? (int) runnerupMakiValue / nRunnersupMaki : 0;
        // Add points to respective players
        for (int i = 0; i < nWinnersMaki; i++) roundModifiers[sortedMaki[i][0]] += winningMakiPoints;
        for (int i = nWinnersMaki; i < nWinnersMaki + nRunnersupMaki; i++) roundModifiers[sortedMaki[i][0]] += runnerupMakiPoints;

        // Filter ccs list into arrays of pairs [id, nPudding] and sort by number of puddings in descending order
        int[][] sortedPuddings = Arrays.stream(ccs).map(cc -> new int[]{cc.playerId, cc.nPudding}).sorted(Comparator.comparingInt(pair -> pair[1])).toArray(int[][]::new);
        Collections.reverse(Arrays.asList(sortedPuddings));
        // Receive important information to distribute points
        int mostPuddingsScore = sortedPuddings[0][1];
        int nMostPuddings = (int) Arrays.stream(sortedPuddings).filter(pair -> pair[1] == mostPuddingsScore).count();
        int leastPuddingsScore = sortedPuddings[nPlayers - 1][1];
        int nLeastPuddings = (int) Arrays.stream(sortedPuddings).filter(pair -> pair[1] == leastPuddingsScore).count();
        // Calculate actual modifier points based on number of players splitting it
        int mostPuddingsPoints = mostPuddingsScore > 0 ? (int) mostPuddingsValue / nMostPuddings : 0;
        int leastPuddingPoints = (int) leastPuddingsValue / nLeastPuddings;
        // Add points to respective players
        for (int i = 0; i < nMostPuddings; i++) roundModifiers[sortedPuddings[i][0]] += mostPuddingsPoints;
        for (int i = nPlayers - 1; i >= nPlayers - nLeastPuddings; i--) roundModifiers[sortedPuddings[i][0]] += leastPuddingPoints;
    }

    @Override
    public void _reset() {
        singleTempuraValue = (double) getParameterValue("singleTempuraValue");
        singleSashimiValue = (double) getParameterValue("singleSashimiValue");
        doubleSashimiValue = (double) getParameterValue("doubleSashimiValue");
        wasabiSquidValue = (double) getParameterValue("wasabiSquidValue");
        wasabiSalmonValue = (double) getParameterValue("wasabiSalmonValue");
        wasabiEggValue = (double) getParameterValue("wasabiEggValue");
        winningMakiValue = (double) getParameterValue("winningMakiValue");
        runnerupMakiValue = (double) getParameterValue("runnerupMakiValue");
        mostPuddingsValue = (double) getParameterValue("mostPuddingsValue");
        leastPuddingsValue = (double) getParameterValue("leastPuddingsValue");
        roundModifiers = new double[nPlayers];
        availableCards = new ArrayList<SGCard>();
    }

    @Override
    protected AbstractParameters _copy() {
        SushiGoHeuristic retValue = new SushiGoHeuristic();
        retValue.singleTempuraValue = singleTempuraValue;
        retValue.singleSashimiValue = singleSashimiValue;
        retValue.doubleSashimiValue = doubleSashimiValue;
        retValue.wasabiSquidValue = wasabiSquidValue;
        retValue.wasabiSalmonValue = wasabiSalmonValue;
        retValue.wasabiEggValue = wasabiEggValue;
        retValue.winningMakiValue = winningMakiValue;
        retValue.runnerupMakiValue = runnerupMakiValue;
        retValue.mostPuddingsValue = mostPuddingsValue;
        retValue.leastPuddingsValue = leastPuddingsValue;
        retValue.roundModifiers = roundModifiers.clone();
        retValue.availableCards = (ArrayList<SGCard>) availableCards.clone();
        return retValue;
    }

    @Override
    protected boolean _equals(Object o) {
        if (o instanceof SushiGoHeuristic) {
            SushiGoHeuristic other = (SushiGoHeuristic) o;
            return other.singleTempuraValue == singleTempuraValue &&
                    other.singleSashimiValue == singleSashimiValue &&
                    other.doubleSashimiValue == doubleSashimiValue &&
                    other.wasabiSquidValue == wasabiSquidValue &&
                    other.wasabiSalmonValue == wasabiSalmonValue &&
                    other.wasabiEggValue == wasabiEggValue &&
                    other.winningMakiValue == winningMakiValue &&
                    other.runnerupMakiValue == runnerupMakiValue &&
                    other.mostPuddingsValue == mostPuddingsValue &&
                    other.leastPuddingsValue == leastPuddingsValue;
        }
        return false;
    }

    @Override
    public Object instantiate() {
        return this._copy();
    }

    class CardCount {
        int playerId;
        int nTempura;
        int nSashimi;
        int nWasabi;
        int nChopsticks;
        int nDumplings;
        int nMaki;
        int nPudding;

        CardCount(int playerId, int nTempura, int nSashimi, int nWasabi, int nChopsticks, int nDumplings, int nMaki, int nPudding) {
            this.playerId = playerId;
            this.nTempura = nTempura;
            this.nSashimi = nSashimi;
            this.nWasabi = nWasabi;
            this.nChopsticks = nChopsticks;
            this.nDumplings = nDumplings;
            this.nMaki = nMaki;
            this.nPudding = nPudding;
        }
    }

    CardCount getPlayerCardCount(SGGameState sggs, int playerId) {
        int nTempura = sggs.getPlayerTempuraAmount(playerId) % 2;
        int nSashimi = sggs.getPlayerSashimiAmount(playerId) % 3;
        int nWasabi = sggs.getPlayerWasabiAvailable(playerId);
        int nChopsticks = sggs.getPlayerChopSticksAmount(playerId);
        int nDumplings = sggs.getPlayerDumplingAmount(playerId);
        int nMaki = 0;
        int nPudding = 0;
        for (SGCard card : sggs.getPlayerField(playerId).getComponents()) {
            if (card.type == Maki_1) nMaki += 1;
            if (card.type == Maki_2) nMaki += 2;
            if (card.type == Maki_3) nMaki += 3;
            if (card.type == Pudding) nPudding++;
        }
        return new CardCount(playerId, nTempura, nSashimi, nWasabi, nChopsticks, nDumplings, nMaki, nPudding);
    }
}
