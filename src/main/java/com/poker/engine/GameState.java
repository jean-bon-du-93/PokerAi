package com.poker.engine;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Représente l'état complet d'une main de poker à un instant T.
 * Cette classe vise à être immuable ou quasi-immuable. Les modifications d'état
 * devraient idéalement produire une nouvelle instance de GameState.
 *
 * Contient des informations sur les joueurs, le pot, les cartes communes,
 * le tour de parole, l'historique des actions, etc.
 */
public class GameState {

    private final List<Player> players; // Liste des joueurs dans la partie, incluant leur état (stack, cartes, etc.)
    private final List<Card> communityCards; // Cartes communes sur le board (flop, turn, river)
    private final double potSize;
    private final List<Double> sidePots; // TODO: Implémenter la gestion des side pots
    private final int currentPlayerIndex; // Index du joueur dont c'est le tour d'agir
    private final int dealerButtonPosition; // Position du bouton du dealer
    private final BettingRound currentBettingRound; // Preflop, Flop, Turn, River
    private final double currentBetToCall; // Montant à suivre pour rester dans la main
    private final double minRaiseAmount; // Montant minimum pour une relance valide
    private final List<Action> legalActions; // Actions légales pour le joueur actuel
    private final List<String> actionHistoryThisRound; // Historique des actions pour le tour de mise actuel
    private final List<String> actionHistoryThisHand; // Historique des actions pour la main entière
    private final boolean handOver; // Indique si la main est terminée
    private final List<Player> winners; // Joueurs ayant gagné la main (peut être multiple en cas de split pot)
    private final Hand winningHand; // La main gagnante (si applicable et unique)


    /**
     * Énumération pour les phases de mise (tours).
     */
    public enum BettingRound {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_OVER
    }

    // Constructeur privé pour être utilisé par un Builder ou des méthodes de transition d'état
    private GameState(Builder builder) {
        this.players = Collections.unmodifiableList(new ArrayList<>(builder.players.stream()
                .map(p -> new Player(p.getId(), p.getName(), p.getStack())) // Crée des copies pour l'immutabilité
                .peek(pCopy -> { // Copie les autres états pertinents
                    Player original = builder.players.stream().filter(op -> op.getId().equals(pCopy.getId())).findFirst().orElse(null);
                    if (original != null) {
                        pCopy.setHoleCards(original.getHoleCards());
                        pCopy.setPosition(original.getPosition());
                        pCopy.setStatus(original.getStatus());
                        pCopy.resetAmountBetThisRound(); // Le GameState stocke amountBetThisRound via currentBetToCall et l'historique
                        pCopy.addToBetThisRound(original.getAmountBetThisRound());// Copie amountBetThisRound
                        pCopy.resetAmountBetThisHand();
                        // amountBetThisHand est plus complexe, il est le cumul des amountBetThisRound précédents.
                        // Pour une vraie immutabilité, GameState devrait stocker cela, ou Player devrait être cloné plus profondément.
                        // Pour l'instant, cette copie est superficielle pour amountBetThisHand.
                        // Le GameEngine devra s'assurer de la cohérence.
                    }
                })
                .collect(Collectors.toList())));
        this.communityCards = Collections.unmodifiableList(new ArrayList<>(builder.communityCards));
        this.potSize = builder.potSize;
        this.sidePots = Collections.unmodifiableList(new ArrayList<>(builder.sidePots));
        this.currentPlayerIndex = builder.currentPlayerIndex;
        this.dealerButtonPosition = builder.dealerButtonPosition;
        this.currentBettingRound = builder.currentBettingRound;
        this.currentBetToCall = builder.currentBetToCall;
        this.minRaiseAmount = builder.minRaiseAmount;
        this.legalActions = builder.legalActions != null ? Collections.unmodifiableList(new ArrayList<>(builder.legalActions)) : Collections.emptyList();
        this.actionHistoryThisRound = Collections.unmodifiableList(new ArrayList<>(builder.actionHistoryThisRound));
        this.actionHistoryThisHand = Collections.unmodifiableList(new ArrayList<>(builder.actionHistoryThisHand));
        this.handOver = builder.handOver;
        this.winners = builder.winners != null ? Collections.unmodifiableList(new ArrayList<>(builder.winners)) : Collections.emptyList();
        this.winningHand = builder.winningHand; // Hand est déjà immuable
    }

    // Getters pour tous les champs
    public List<Player> getPlayers() {
        // Retourne des copies des joueurs pour maintenir l'immutabilité de l'état interne du GameState
        // Si Player lui-même est mutable, cela nécessite une copie profonde ou que Player devienne immuable.
        // Pour l'instant, on retourne la liste immuable de joueurs (qui sont des copies faites à la construction).
        return players;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public double getPotSize() {
        return potSize;
    }

    public List<Double> getSidePots() {
        return sidePots;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public Player getCurrentPlayer() {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size() || players.get(currentPlayerIndex).getStatus() == Player.PlayerStatus.FOLDED) {
            return null; // Aucun joueur actuel ou joueur couché
        }
        return players.get(currentPlayerIndex);
    }

    public int getDealerButtonPosition() {
        return dealerButtonPosition;
    }

    public BettingRound getCurrentBettingRound() {
        return currentBettingRound;
    }

    public double getCurrentBetToCall() {
        return currentBetToCall;
    }

    public double getAmountToCallForPlayer(Player player) {
        if (player == null) return 0;
        return Math.max(0, currentBetToCall - player.getAmountBetThisRound());
    }

    public double getMinRaiseAmount() {
        return minRaiseAmount;
    }

    public List<Action> getLegalActions() {
        // TODO: Cette logique devrait être dans GameEngine et passée au constructeur de GameState.
        // Pour l'instant, on la retourne telle quelle. Le GameEngine sera responsable de la peupler.
        // Si getCurrentPlayer() est null ou non actif, la liste devrait être vide.
        if (getCurrentPlayer() == null || !getCurrentPlayer().isActive()) return Collections.emptyList();
        return legalActions;
    }

    public List<String> getActionHistoryThisRound() {
        return actionHistoryThisRound;
    }

    public List<String> getActionHistoryThisHand() {
        return actionHistoryThisHand;
    }

    public boolean isHandOver() {
        return handOver;
    }

    public List<Player> getWinners() {
        return winners;
    }

    public Hand getWinningHand() {
        return winningHand;
    }

    // Builder Statique
    public static class Builder {
        private List<Player> players = new ArrayList<>();
        private List<Card> communityCards = new ArrayList<>();
        private double potSize = 0;
        private List<Double> sidePots = new ArrayList<>();
        private int currentPlayerIndex = -1;
        private int dealerButtonPosition = 0;
        private BettingRound currentBettingRound = BettingRound.PREFLOP;
        private double currentBetToCall = 0;
        private double minRaiseAmount = 0; // Sera typiquement la grosse blinde au début
        private List<Action> legalActions = new ArrayList<>();
        private List<String> actionHistoryThisRound = new ArrayList<>();
        private List<String> actionHistoryThisHand = new ArrayList<>();
        private boolean handOver = false;
        private List<Player> winners = null;
        private Hand winningHand = null;


        public Builder setPlayers(List<Player> players) {
            this.players.clear();
            // Crée des copies des joueurs pour éviter les modifications externes après la construction du GameState
            for (Player p : players) {
                Player playerCopy = new Player(p.getId(), p.getName(), p.getStack());
                playerCopy.setHoleCards(p.getHoleCards()); // Card est immuable
                playerCopy.setPosition(p.getPosition());
                playerCopy.setStatus(p.getStatus());
                playerCopy.addToBetThisRound(p.getAmountBetThisRound());
                // amountBetThisHand n'est pas copié ici car il est dérivé.
                this.players.add(playerCopy);
            }
            return this;
        }

        public Builder setCommunityCards(List<Card> communityCards) {
            this.communityCards = new ArrayList<>(communityCards);
            return this;
        }

        public Builder setPotSize(double potSize) {
            this.potSize = potSize;
            return this;
        }

        public Builder addSidePot(double sidePotAmount) {
            this.sidePots.add(sidePotAmount);
            return this;
        }

        public Builder clearSidePots() {
            this.sidePots.clear();
            return this;
        }

        public Builder setCurrentPlayerIndex(int currentPlayerIndex) {
            this.currentPlayerIndex = currentPlayerIndex;
            return this;
        }

        public Builder setDealerButtonPosition(int dealerButtonPosition) {
            this.dealerButtonPosition = dealerButtonPosition;
            return this;
        }

        public Builder setCurrentBettingRound(BettingRound currentBettingRound) {
            this.currentBettingRound = currentBettingRound;
            return this;
        }

        public Builder setCurrentBetToCall(double currentBetToCall) {
            this.currentBetToCall = currentBetToCall;
            return this;
        }

        public Builder setMinRaiseAmount(double minRaiseAmount) {
            this.minRaiseAmount = minRaiseAmount;
            return this;
        }

        public Builder setLegalActions(List<Action> legalActions) {
            this.legalActions = new ArrayList<>(legalActions);
            return this;
        }

        public Builder addActionToHistory(Action action, Player player) {
            String historyEntry = player.getName() + " " + action.toString();
            this.actionHistoryThisRound.add(historyEntry);
            this.actionHistoryThisHand.add(historyEntry);
            return this;
        }

        public Builder clearActionHistoryThisRound() {
            this.actionHistoryThisRound.clear();
            return this;
        }

        public Builder setActionHistoryThisHand(List<String> history) {
            this.actionHistoryThisHand = new ArrayList<>(history);
            return this;
        }
        public Builder setActionHistoryThisRound(List<String> history) {
            this.actionHistoryThisRound = new ArrayList<>(history);
            return this;
        }


        public Builder setHandOver(boolean handOver) {
            this.handOver = handOver;
            return this;
        }

        public Builder setWinners(List<Player> winners) {
            if (winners != null) {
                this.winners = new ArrayList<>(winners); // Copie pour immutabilité
            } else {
                this.winners = null;
            }
            return this;
        }

        public Builder setWinningHand(Hand winningHand) {
            this.winningHand = winningHand; // Hand est immuable
            return this;
        }

        public GameState build() {
            // Valider l'état avant de construire peut être une bonne idée ici
            // Par exemple, s'assurer que currentPlayerIndex est valide.
            return new GameState(this);
        }
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("GameState:\n");
        sb.append("  Round: ").append(currentBettingRound).append("\n");
        sb.append("  Pot: ").append(String.format("%.2f", potSize)).append("\n");
        if (!sidePots.isEmpty()) {
            sb.append("  Side Pots: ").append(sidePots.stream().map(p -> String.format("%.2f", p)).collect(Collectors.joining(", "))).append("\n");
        }
        sb.append("  Community Cards: ").append(communityCards).append("\n");
        sb.append("  Dealer Button: Position ").append(dealerButtonPosition).append("\n");

        Player currentP = getCurrentPlayer();
        if (currentP != null) {
             sb.append("  Current Player: ").append(currentP.getName())
               .append(" (Idx: ").append(currentPlayerIndex).append(")\n");
             sb.append("  Bet to Call: ").append(String.format("%.2f", getAmountToCallForPlayer(currentP))).append("\n");
             sb.append("  Min Raise: ").append(String.format("%.2f", minRaiseAmount)).append("\n");
             sb.append("  Legal Actions: ").append(getLegalActions()).append("\n");
        } else {
            sb.append("  No current player to act.\n");
        }

        sb.append("  Players:\n");
        for (Player player : players) {
            sb.append("    - ").append(player.getName())
              .append(" (Pos: ").append(player.getPosition())
              .append(", Stack: ").append(String.format("%.2f",player.getStack()))
              .append(", Status: ").append(player.getStatus())
              .append(", Bet This Round: ").append(String.format("%.2f",player.getAmountBetThisRound()));
            if (!player.getHoleCards().isEmpty()) {
                sb.append(", Cards: ").append(player.getHoleCards());
            }
            sb.append(")\n");
        }
        sb.append("  Action History (Round): ").append(actionHistoryThisRound).append("\n");
        if (handOver) {
            sb.append("  HAND OVER.\n");
            if (winners != null && !winners.isEmpty()) {
                sb.append("  Winners: ").append(winners.stream().map(Player::getName).collect(Collectors.joining(", "))).append("\n");
                if (winningHand != null) {
                    sb.append("  Winning Hand: ").append(winningHand).append("\n");
                }
            } else {
                 sb.append("  No winners (e.g. all folded except one who takes pot by default).\n");
            }
        }
        return sb.toString();
    }
}
