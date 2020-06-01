package games.uno;

import core.AbstractGameParameters;
import core.components.Deck;
import core.AbstractGameState;
import core.interfaces.IPrintable;
import core.observations.VectorObservation;
import core.turnorders.TurnOrder;
import games.uno.cards.*;
import utilities.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static games.uno.cards.UnoCard.UnoCardType.Wild;

public class UnoGameState extends AbstractGameState implements IPrintable {
    List<Deck<UnoCard>>  playerDecks;
    Deck<UnoCard>        drawDeck;
    Deck<UnoCard>        discardDeck;
    UnoCard              currentCard;
    String currentColor;

    /**
     * Constructor. Initialises some generic game state variables.
     *
     * @param gameParameters - game parameters.
     * @param turnOrder      - turn order for this game.
     */
    public UnoGameState(AbstractGameParameters gameParameters, TurnOrder turnOrder) {
        super(gameParameters, turnOrder);
    }

    public void addAllComponents()
    {
        allComponents.putComponents(playerDecks);
        allComponents.putComponent(drawDeck);
        allComponents.putComponent(discardDeck);
        allComponents.putComponent(currentCard);
    }

    boolean isWildCard(UnoCard card) {
        return card.type == Wild;
    }

    boolean isNumberCard(UnoCard card) {
        return card.type == UnoCard.UnoCardType.Number;
    }

    public void updateCurrentCard(UnoCard card) {
        currentCard  = card;
        currentColor = card.color;
    }

    public void updateCurrentCard(UnoCard card, String color) {
        currentCard  = card;
        currentColor = color;
    }

    public Deck<UnoCard> getDrawDeck() {
        return drawDeck;
    }

    public Deck<UnoCard> getDiscardDeck() {
        return discardDeck;
    }

    public List<Deck<UnoCard>> getPlayerDecks() {
        return playerDecks;
    }

    public UnoCard getCurrentCard() {
        return currentCard;
    }

    public String getCurrentColor() {
        return currentColor;
    }

    @Override
    protected AbstractGameState copy(int playerId) {
        // TODO: partial observability
        UnoGameState copy = new UnoGameState(gameParameters.copy(), turnOrder.copy());
        copy.playerDecks = new ArrayList<>();
        for (Deck<UnoCard> d: playerDecks) {
            copy.playerDecks.add(d.copy());
        }
        copy.drawDeck = drawDeck.copy();
        copy.discardDeck = discardDeck.copy();
        copy.currentCard = (UnoCard) currentCard.copy();
        copy.currentColor = currentColor;
        return copy;
    }

    @Override
    public VectorObservation getVectorObservation() {
        // TODO
        return null;
    }

    @Override
    public double[] getDistanceFeatures(int playerId) {
        // TODO
        return new double[0];
    }

    @Override
    public HashMap<HashMap<Integer, Double>, Utils.GameResult> getTerminalFeatures(int playerId) {
        // TODO
        return null;
    }

    @Override
    public double getScore(int playerId) {
        // TODO: heuristic
        return 0;
    }

    @Override
    public void printToConsole() {

        String[] strings = new String[6];

        strings[0] = "----------------------------------------------------";
        strings[1] = "Current Card: " + currentCard.toString() + " [" + currentColor + "]";
        strings[2] = "----------------------------------------------------";

        strings[3] = "Player      : " + getCurrentPlayer();
        StringBuilder sb = new StringBuilder();
        sb.append("Player Hand : ");

        for (UnoCard card : playerDecks.get(getCurrentPlayer()).getComponents()) {
            sb.append(card.toString());
            sb.append(" ");
        }
        strings[4] = sb.toString();
        strings[5] = "----------------------------------------------------";

        for (String s : strings){
            System.out.println(s);
        }
    }
}

