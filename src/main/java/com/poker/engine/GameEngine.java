package com.poker.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestre le déroulement d'une partie de poker Texas Hold'em.
 * Gère les tours de mise, la distribution des cartes, la détermination du vainqueur,
 * et la gestion du pot.
 * Prend des objets {@link Action} en entrée et produit un nouveau {@link GameState} après chaque action.
 */
public class GameEngine {

    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    private List<Player> players; // Liste des joueurs à la table
    private Deck deck;
    private GameState currentGameState;
    private final double bigBlindAmount;
    private final double smallBlindAmount;
    private int dealerButtonPosition;

    /**
     * Construit un nouveau moteur de jeu.
     *
     * @param initialPlayers   La liste des joueurs commençant la partie. Ils doivent avoir des stacks définis.
     * @param smallBlindAmount Le montant de la petite blinde.
     * @param bigBlindAmount   Le montant de la grosse blinde.
     * @throws IllegalArgumentException si initialPlayers est null, vide, ou si les montants des blindes sont invalides.
     */
    public GameEngine(List<Player> initialPlayers, double smallBlindAmount, double bigBlindAmount) {
        if (initialPlayers == null || initialPlayers.isEmpty()) {
            throw new IllegalArgumentException("Initial players list cannot be null or empty.");
        }
        if (smallBlindAmount <= 0 || bigBlindAmount <= smallBlindAmount) { // BB doit être > SB, et SB > 0
            throw new IllegalArgumentException("Invalid blind amounts. Big blind must be greater than small blind, and small blind positive.");
        }

        this.players = new ArrayList<>();
        for(Player p : initialPlayers) { // Cloner pour éviter la modification externe de la liste originale des joueurs
            Player playerCopy = new Player(p.getId(), p.getName(), p.getStack());
            // Copier d'autres attributs si nécessaire, mais pour le début de la partie, ID, nom, stack suffisent.
            this.players.add(playerCopy);
        }

        this.deck = new Deck();
        this.smallBlindAmount = smallBlindAmount;
        this.bigBlindAmount = bigBlindAmount;
        this.dealerButtonPosition = new Random().nextInt(this.players.size()); // Position aléatoire au début

        // L'état initial de la partie n'est pas encore une main commencée. startNewHand() le fera.
        // On peut initialiser un GameState "vide" ou attendre startNewHand.
        // Pour l'instant, currentGameState sera initialisé dans startNewHand.
        this.currentGameState = null;
    }

    /**
     * Démarre une nouvelle main de poker.
     * Mélange le paquet, déplace le bouton du dealer, assigne les positions,
     * collecte les blindes, et distribue les cartes aux joueurs.
     *
     * @return Le GameState initial de la nouvelle main.
     */
    public GameState startNewHand() {
        deck.reset(); // Mélange le paquet

        // Prépare les joueurs pour la nouvelle main (statut, mises précédentes, etc.)
        for (Player player : players) {
            player.prepareForNewHand();
        }

        // Filtrer les joueurs qui sont OUT
        this.players = this.players.stream().filter(p -> p.getStatus() != Player.PlayerStatus.OUT && p.getStack() > 0).collect(Collectors.toList());
        if (this.players.size() < 2) {
            logger.warn("Not enough active players to start a new hand ({}).", this.players.size());
            // Gérer la fin de la partie ici si nécessaire
            this.currentGameState = new GameState.Builder()
                                        .setPlayers(this.players)
                                        .setHandOver(true)
                                        .setCurrentBettingRound(GameState.BettingRound.HAND_OVER)
                                        .build();
            return this.currentGameState;
        }


        // Déplacer le bouton du dealer
        dealerButtonPosition = (dealerButtonPosition + 1) % players.size();
        Player dealer = players.get(dealerButtonPosition);
        logger.info("Starting new hand. Dealer is {}", dealer.getName());

        // Assigner les positions (SB, BB, UTG, etc.)
        assignPositions();

        // Initialiser le pot et les mises
        double currentPot = 0;
        double currentBet = 0;

        // Réinitialiser les montants misés par les joueurs pour cette main
        for (Player player : players) {
            player.resetAmountBetThisHand();
        }

        // Gérer les blindes
        // Small Blind
        Player smallBlindPlayer = players.get((dealerButtonPosition + 1) % players.size());
        double sbPosted = postBlind(smallBlindPlayer, smallBlindAmount, "Small Blind");
        currentPot += sbPosted;

        // Big Blind
        // En heads-up, le dealer (bouton) est SB et l'autre joueur est BB.
        int bbPositionIndex;
        if (players.size() == 2) {
            bbPositionIndex = dealerButtonPosition; // Le dealer est SB, l'autre est BB.
            // Correction : en HU, le bouton est SB et paie SB. L'autre joueur est BB et paie BB.
            // Donc, SB est à dealerButtonPosition, BB est à (dealerButtonPosition + 1) % 2.
            // Notre logique de position assigne 0 au SB, 1 au BB.
            // SB = (dealer + 1) % N. BB = (dealer + 2) % N
            // En HU: Dealer = 0. SB = (0+1)%2 = 1. BB = (0+2)%2 = 0.
            // Si dealerButtonPosition est le joueur qui A le bouton:
            // SB est (dealerButtonPosition + 1) % size.
            // BB est (dealerButtonPosition + 2) % size.
            // En HU: Bouton = P0. SB = P1. BB = P0.  -- Ceci est une convention commune.
            // L'autre convention: Bouton = P0 (SB). P1 = BB.
            // Adoptons : Bouton = P0, SB = P0, BB = P1 pour HU. (SB agit en premier preflop, BB en dernier).
            // Non, la règle standard: en HU, le joueur avec le bouton est SB et agit en premier pré-flop. L'autre est BB.
            // Donc, SB = players.get(dealerButtonPosition). BB = players.get((dealerButtonPosition + 1) % players.size()).
            // Notre assignPositions() et postBlinds() devrait gérer cela.
            // smallBlindPlayer a déjà été déterminé.
            bbPositionIndex = (dealerButtonPosition + (players.size() > 2 ? 2 : 1)) % players.size();
        } else {
            bbPositionIndex = (dealerButtonPosition + 2) % players.size();
        }
        Player bigBlindPlayer = players.get(bbPositionIndex);
        double bbPosted = postBlind(bigBlindPlayer, bigBlindAmount, "Big Blind");
        currentPot += bbPosted;
        currentBet = bigBlindAmount; // La mise à suivre est la BB

        // Distribuer les cartes privées (hole cards)
        for (Player player : players) {
            if (player.getStatus() != Player.PlayerStatus.OUT) {
                player.setHoleCards(deck.dealCards(2));
                logger.debug("{} receives cards: {}", player.getName(), player.getHoleCards());
            }
        }

        // Déterminer qui parle en premier pré-flop (UTG, ou SB en HU)
        int currentPlayerIndex;
        if (players.size() == 2) { // Heads-up
            currentPlayerIndex = dealerButtonPosition; // SB (qui a le bouton) parle en premier
        } else {
            currentPlayerIndex = (dealerButtonPosition + 3) % players.size(); // Joueur après BB (UTG)
        }

        // Assurer que le joueur actuel peut jouer
        currentPlayerIndex = findNextActivePlayer(currentPlayerIndex, true);


        GameState.Builder stateBuilder = new GameState.Builder()
                .setPlayers(new ArrayList<>(this.players)) // Passer une copie
                .setCommunityCards(Collections.emptyList())
                .setPotSize(currentPot)
                .setCurrentPlayerIndex(currentPlayerIndex)
                .setDealerButtonPosition(dealerButtonPosition)
                .setCurrentBettingRound(GameState.BettingRound.PREFLOP)
                .setCurrentBetToCall(currentBet)
                .setMinRaiseAmount(bigBlindAmount * 2) // Min raise est généralement le double de la BB ou le montant de la dernière relance
                .setHandOver(false);

        // Ajouter les actions de blindes à l'historique
        stateBuilder.addActionToHistory(new Action(Action.ActionType.BET, sbPosted), smallBlindPlayer); // SB est une mise
        stateBuilder.addActionToHistory(new Action(Action.ActionType.BET, bbPosted), bigBlindPlayer); // BB est une mise

        this.currentGameState = stateBuilder.setLegalActions(determineLegalActions(this.players.get(currentPlayerIndex), currentGameState)).build();
        logger.info("New hand started. Initial state: {}", this.currentGameState);
        return this.currentGameState;
    }

    private void assignPositions() {
        for (int i = 0; i < players.size(); i++) {
            // La position est relative au bouton du dealer.
            // Joueur au bouton = 0, SB = 1, BB = 2, UTG = 3, etc. (dans l'ordre de parole post-flop)
            // Ou plus simple : index dans la liste après rotation pour que le dealer soit à la fin.
            // Ou encore : 0 = dealer, 1 = SB, 2 = BB, ...
            // Utilisons l'index dans la liste `players` comme position fixe pour la main.
            // Le `dealerButtonPosition` indique qui est le dealer.
            players.get(i).setPosition(i);
        }
    }

    private double postBlind(Player player, double blindAmount, String blindName) {
        logger.info("{} posts {} of {}", player.getName(), blindName, blindAmount);
        double amountToPost = Math.min(player.getStack(), blindAmount);
        player.removeFromStack(amountToPost);
        player.addToBetThisRound(amountToPost);
        // Ne pas changer le statut à ALL_IN ici, cela sera géré par handleAction si la mise est all-in.
        if (player.getStack() == 0) {
            player.setStatus(Player.PlayerStatus.ALL_IN);
            logger.info("{} is ALL-IN after posting {}", player.getName(), blindName);
        }
        return amountToPost;
    }

    /**
     * Traite une action d'un joueur et met à jour l'état du jeu.
     *
     * @param action L'action effectuée par le joueur actuel.
     * @return Le nouveau GameState après l'action.
     * @throws IllegalStateException si ce n'est pas le tour du joueur, ou si l'action est illégale.
     */
    public GameState handleAction(Action action) {
        if (currentGameState.isHandOver()) {
            logger.warn("Attempted to handle action {} when hand is already over.", action);
            return currentGameState;
        }

        Player actingPlayer = currentGameState.getCurrentPlayer();
        if (actingPlayer == null) {
            throw new IllegalStateException("No current player to act in GameState.");
        }

        // Valider l'action (ceci devrait être plus robuste)
        List<Action> legalActions = determineLegalActions(actingPlayer, currentGameState);
        if (!isActionLegal(action, legalActions, actingPlayer, currentGameState)) {
             logger.error("Illegal action {} attempted by {}. Legal actions: {}. Current Bet: {}, Player Bet: {}, Stack: {}",
                action, actingPlayer.getName(), legalActions, currentGameState.getCurrentBetToCall(), actingPlayer.getAmountBetThisRound(), actingPlayer.getStack());
            // Pour le moment, on va essayer de forcer un FOLD si l'action est illégale et que FOLD est possible.
            // Ou alors on lance une exception. Pour l'IA, il faut être strict.
            if (legalActions.stream().anyMatch(a -> a.getType() == Action.ActionType.FOLD)) {
                logger.warn("Forcing FOLD for player {} due to illegal action {}.", actingPlayer.getName(), action);
                action = new Action(Action.ActionType.FOLD);
            } else {
                 // Si FOLD n'est pas possible (ex: joueur est all-in et doit checker), alors c'est un bug.
                 throw new IllegalStateException("Illegal action " + action + " by " + actingPlayer.getName() +
                                                ". Legal actions: " + legalActions +
                                                ". Player stack: " + actingPlayer.getStack() +
                                                ". Bet to call: " + currentGameState.getAmountToCallForPlayer(actingPlayer));
            }
        }


        GameState.Builder nextStateBuilder = new GameState.Builder()
                .setPlayers(new ArrayList<>(this.players)) // Copie pour modification potentielle
                .setCommunityCards(currentGameState.getCommunityCards())
                .setPotSize(currentGameState.getPotSize())
                .setDealerButtonPosition(currentGameState.getDealerButtonPosition())
                .setCurrentBettingRound(currentGameState.getCurrentBettingRound())
                .setCurrentBetToCall(currentGameState.getCurrentBetToCall())
                .setMinRaiseAmount(currentGameState.getMinRaiseAmount())
                .setActionHistoryThisHand(currentGameState.getActionHistoryThisHand()) // Copier l'historique complet
                .setActionHistoryThisRound(currentGameState.getActionHistoryThisRound()); // Copier l'historique du tour

        nextStateBuilder.addActionToHistory(action, actingPlayer);
        logger.info("Player {} performs action: {}", actingPlayer.getName(), action);

        double pot = currentGameState.getPotSize();
        double currentBetForRound = currentGameState.getCurrentBetToCall();
        double newMinRaise = currentGameState.getMinRaiseAmount();


        switch (action.getType()) {
            case FOLD:
                actingPlayer.setStatus(Player.PlayerStatus.FOLDED);
                break;
            case CHECK:
                // Rien à faire sur le pot ou les mises, juste avancer.
                // Le statut du joueur reste ACTIVE.
                break;
            case CALL:
                double amountToCall = currentGameState.getAmountToCallForPlayer(actingPlayer);
                double actualCallAmount = Math.min(amountToCall, actingPlayer.getStack());

                actingPlayer.removeFromStack(actualCallAmount);
                actingPlayer.addToBetThisRound(actualCallAmount);
                pot += actualCallAmount;
                if (actingPlayer.getStack() == 0) {
                    actingPlayer.setStatus(Player.PlayerStatus.ALL_IN);
                }
                break;
            case BET:
                // BET est possible seulement si currentBetForRound est 0 (personne n'a misé avant)
                double betAmount = Math.min(action.getAmount(), actingPlayer.getStack());
                actingPlayer.removeFromStack(betAmount);
                actingPlayer.addToBetThisRound(betAmount);
                pot += betAmount;
                currentBetForRound = betAmount; // Nouvelle mise de référence pour ce tour
                newMinRaise = betAmount * 2; // Le prochain raise doit être au moins le double de cette mise
                                             // Ou plus précisément: currentBetForRound + (currentBetForRound - previousBet)
                                             // Si c'est le premier bet, c'est juste betAmount.
                if (actingPlayer.getStack() == 0) {
                    actingPlayer.setStatus(Player.PlayerStatus.ALL_IN);
                }
                break;
            case RAISE:
                // RAISE est possible si currentBetForRound > 0
                double raiseAmount = Math.min(action.getAmount(), actingPlayer.getStack()); // Le montant total de la relance du joueur

                // Le joueur doit d'abord payer la mise actuelle, puis ajouter le montant de la relance.
                // L'action.getAmount() est le montant TOTAL que le joueur met (pas juste le supplément).
                // Exemple: BB 10. Joueur A mise 20 (raise to 20). Joueur B veut relancer à 50.
                // action.getAmount() = 50.
                // amountAlreadyBetThisRound = joueur B a peut-être déjà misé (ex: SB).
                // betIncrease = raiseAmount - actingPlayer.getAmountBetThisRound();

                double amountAddedToPot = raiseAmount - actingPlayer.getAmountBetThisRound();
                actingPlayer.removeFromStack(amountAddedToPot); // On retire seulement ce qui est ajouté au pot
                actingPlayer.addToBetThisRound(amountAddedToPot); // S'ajoute à ce qui a déjà été misé ce tour

                pot += amountAddedToPot;

                double previousBetOrRaise = currentBetForRound; // La mise/relance précédente à battre
                currentBetForRound = actingPlayer.getAmountBetThisRound(); // Nouvelle mise de référence
                newMinRaise = currentBetForRound + (currentBetForRound - previousBetOrRaise); // Règle standard du min-raise

                if (actingPlayer.getStack() == 0) {
                    actingPlayer.setStatus(Player.PlayerStatus.ALL_IN);
                }
                break;
        }

        nextStateBuilder.setPotSize(pot);
        nextStateBuilder.setCurrentBetToCall(currentBetForRound);
        nextStateBuilder.setMinRaiseAmount(newMinRaise);
        nextStateBuilder.setPlayers(new ArrayList<>(this.players)); // Assurer que les modifs sur les joueurs sont prises

        // Vérifier si le tour de mise est terminé
        if (isBettingRoundOver(nextStateBuilder, actingPlayer)) {
            logger.info("Betting round {} is over.", currentGameState.getCurrentBettingRound());
            nextStateBuilder.clearActionHistoryThisRound(); // Prépare pour le prochain tour
            // Réinitialiser les amountBetThisRound pour tous les joueurs actifs pour le prochain tour
            for (Player p : this.players) {
                 if(p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT) {
                    p.resetAmountBetThisRound();
                 }
            }
            nextStateBuilder.setCurrentBetToCall(0); // Pas de mise à suivre au début du prochain tour
            nextStateBuilder.setMinRaiseAmount(this.bigBlindAmount); // Reset min raise pour le prochain tour (ou BB)

            // Passer au prochain tour (Flop, Turn, River, Showdown)
            advanceToNextRound(nextStateBuilder);
        } else {
            // Tour de mise non terminé, trouver le prochain joueur
            int nextPlayerIndex = findNextActivePlayer((currentGameState.getCurrentPlayerIndex() + 1) % players.size(), false);
            nextStateBuilder.setCurrentPlayerIndex(nextPlayerIndex);
            if (nextPlayerIndex != -1) {
                 nextStateBuilder.setLegalActions(determineLegalActions(this.players.get(nextPlayerIndex), nextStateBuilder.build()));
            } else {
                // Cela ne devrait pas arriver si isBettingRoundOver est false, sauf si tous les autres ont fold.
                // Ce cas devrait être géré par isBettingRoundOver ou advanceToNextRound
                 logger.error("No next active player found, but betting round not over. This is a potential bug.");
                 // Forcer la fin de la main si un seul joueur reste
                 if (countActivePlayers() <= 1) {
                     concludeHand(nextStateBuilder);
                 }
            }
        }

        // Si la main est terminée (par ex. après showdown ou si tous sauf un se couchent)
        if (countActivePlayersNotFoldedOrOut() <= 1 && !currentGameState.isHandOver()) {
             if (nextStateBuilder.build().getCurrentBettingRound() != GameState.BettingRound.HAND_OVER) {
                concludeHand(nextStateBuilder);
             }
        }


        this.currentGameState = nextStateBuilder.build();
        logger.debug("New GameState after action: {}", this.currentGameState);
        return this.currentGameState;
    }

    private boolean isActionLegal(Action action, List<Action> legalActions, Player player, GameState gs) {
        // Vérification basique. Pourrait être plus approfondie (montants exacts).
        for (Action legal : legalActions) {
            if (legal.getType() == action.getType()) {
                if (action.getType() == Action.ActionType.BET || action.getType() == Action.ActionType.RAISE) {
                    // Ici, on devrait vérifier si le montant de l'action est dans la plage des montants légaux.
                    // Pour l'instant, on suppose que si le type correspond, et que le montant est "raisonnable", c'est bon.
                    // Une IA bien éduquée devrait choisir parmi les actions proposées par determineLegalActions.
                    // La validation stricte des montants est importante.
                    if (action.getAmount() < 0) return false; // Mise négative illégale

                    if (action.getType() == Action.ActionType.RAISE) {
                        // Le montant total de la relance doit être au moins gs.getMinRaiseAmount()
                        // Et le montant total de la relance doit être au moins gs.getCurrentBetToCall() + (gs.getCurrentBetToCall() - previousBet)
                        // L'action.getAmount() est la mise totale du joueur pour ce tour.
                        // Elle doit être >= gs.getMinRaiseAmount() si gs.getMinRaiseAmount() est pertinent (c.a.d. une relance valide)
                        // Elle doit aussi être >= gs.getCurrentBetToCall()
                        if (action.getAmount() < gs.getCurrentBetToCall()) return false; // On ne peut pas relancer à moins que la mise actuelle

                        // Si c'est un all-in, c'est légal même si < minRaise, si c'est tout le stack.
                        if (action.getAmount() == player.getStack() + player.getAmountBetThisRound()) return true;

                        // Le montant de la relance (la partie ajoutée) doit être au moins la taille de la mise/relance précédente.
                        // gs.getMinRaiseAmount() représente le montant TOTAL de la prochaine relance.
                        if (action.getAmount() < gs.getMinRaiseAmount() && player.getStack() + player.getAmountBetThisRound() > action.getAmount()) {
                             // Sauf si le joueur est all-in pour moins.
                            return false; // Relance trop petite
                        }

                    } else { // BET
                         if (action.getAmount() < this.bigBlindAmount && player.getStack() > action.getAmount()) {
                             // Ne peut pas miser moins que la BB sauf si all-in.
                             return false;
                         }
                    }
                }
                return true; // Type correspond, et pour Bet/Raise, on suppose que le montant est valide s'il est positif.
            }
        }
        return false;
    }


    private boolean isBettingRoundOver(GameState.Builder currentStateBuilderSnapshot, Player lastActor) {
        // Le tour de mise se termine si :
        // 1. Tous les joueurs actifs (pas FOLDED, pas ALL_IN avec moins que la mise actuelle) ont agi au moins une fois ce tour.
        // 2. Et tous les joueurs actifs qui ne sont pas ALL_IN ont misé le même montant ce tour.
        // 3. Ou si un seul joueur (ou aucun) n'est pas FOLDED.

        List<Player> playersInHand = currentStateBuilderSnapshot.build().getPlayers().stream()
            .filter(p -> p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT)
            .collect(Collectors.toList());

        if (playersInHand.size() <= 1) {
            return true; // Fin du tour (et de la main) si un seul ou zéro joueur restant.
        }

        // Tous les joueurs encore dans la main (non-FOLDED, non-OUT) et qui ne sont pas ALL_IN
        // doivent avoir misé le même montant dans ce tour de mise.
        // Et tout le monde doit avoir eu la chance d'agir sur la dernière mise/relance.

        double currentMaxBetThisRound = currentStateBuilderSnapshot.build().getCurrentBetToCall();

        // Vérifier si tous les joueurs actifs ont eu la chance d'agir depuis la dernière relance/mise.
        // Un joueur qui a misé currentMaxBetThisRound a "fermé" l'action pour lui-même, sauf si quelqu'un relance après lui.
        // Le joueur qui a fait la dernière mise/relance (currentMaxBetThisRound) est celui qui a "ouvert" l'action.
        // Le tour se termine lorsque l'action revient à ce joueur et qu'il checke ou calle,
        // ou si tous les autres joueurs se couchent.

        // Le joueur qui a fait la dernière mise significative est celui qui a misé `currentBetToCall`.
        // L'action doit faire un tour complet de table et revenir à ce joueur, ou à celui qui a ouvert l'action
        // si personne n'a relancé.

        boolean allMatchedOrAllIn = true;
        boolean everyoneActedAtLeastOnce = true; // Ou plutôt, tout le monde a eu la chance d'agir sur la mise actuelle.

        // Qui a initié la dernière mise/relance ?
        // L'action se termine quand elle revient au relanceur initial et qu'il n'y a pas de nouvelle relance.
        // Ou, plus simplement, quand tous les joueurs actifs ont misé le même montant (sauf ceux qui sont all-in pour moins)
        // ET que le joueur dont c'est le tour a déjà agi sur cette mise.

        // Le nombre de joueurs actifs (non-folded, non-out)
        long activePlayersCount = players.stream().filter(p -> p.getStatus() == Player.PlayerStatus.ACTIVE).count();
        if (activePlayersCount == 0 && playersInHand.stream().allMatch(Player::isAllIn)) { // Tous les joueurs restants sont all-in
            return true;
        }
        if (activePlayersCount == 0 && playersInHand.size() > 0) { // Tous all-in ou folded
             // Si tous les joueurs restants sont all-in, le tour est terminé.
            if (playersInHand.stream().allMatch(p -> p.isAllIn() || p.getStatus() == Player.PlayerStatus.FOLDED)) return true;
        }
        if (activePlayersCount == 1) {
            // Si un seul joueur est 'ACTIVE', il doit correspondre à la mise, ou le tour est terminé.
            // Si le joueur 'ACTIVE' est celui qui vient d'agir.
            Player singleActivePlayer = players.stream().filter(p -> p.getStatus() == Player.PlayerStatus.ACTIVE).findFirst().orElse(null);
            if (singleActivePlayer != null && singleActivePlayer.equals(lastActor) &&
                singleActivePlayer.getAmountBetThisRound() == currentMaxBetThisRound) {
                 // Il a agi et égalisé la mise, et il n'y a personne d'autre pour relancer.
                 // Ou il a misé et tout le monde s'est couché.
                 // Ce cas est plus complexe, car il faut s'assurer que tout le monde a eu la parole.
            }
        }


        // Scénario simple: tout le monde a misé le même montant ou est all-in/folded.
        // Et le joueur actuel est celui qui a fait la dernière mise/relance (ou checké une option).
        // Le tour de mise se termine quand l'action revient au joueur qui a fait la dernière mise agressive
        // et que ce joueur checke (si option) ou que tous les joueurs ont misé le même montant.

        // Un flag pour savoir si une mise/relance a eu lieu dans ce "sous-tour"
        boolean actionClosed = true; // Supposons que l'action est fermée

        // Le joueur qui a misé le plus (et qui n'est pas le joueur actuel s'il vient de relancer)
        // doit voir l'action lui revenir.

        // Approche plus simple:
        // 1. Au moins un joueur doit avoir agi dans ce tour (après les blindes pour preflop). L'historique du tour le dit.
        if (currentStateBuilderSnapshot.build().getActionHistoryThisRound().size() <= (currentStateBuilderSnapshot.build().getCurrentBettingRound() == GameState.BettingRound.PREFLOP ? 2 : 0) ) {
            // Moins de 2 actions pour preflop (SB, BB) ou 0 pour les autres tours signifie que le tour ne peut pas être terminé.
            // Sauf si un seul joueur est actif.
             if (playersInHand.size() > 1) return false;
        }


        for (Player p : playersInHand) {
            if (!p.isAllIn() && p.getAmountBetThisRound() < currentMaxBetThisRound) {
                allMatchedOrAllIn = false; // Quelqu'un doit encore agir (caller, raiser, ou folder)
                break;
            }
        }

        if (!allMatchedOrAllIn) return false;

        // Si tous ont égalisé ou sont all-in, il faut vérifier que le joueur qui a fait la dernière relance
        // n'est pas celui qui doit parler maintenant (sauf s'il check son option, ex: BB preflop).
        // Le tour se termine si le joueur qui doit parler a déjà misé le montant `currentMaxBetThisRound`
        // (signifiant que l'action lui est revenue et qu'il a déjà contribué ce montant, donc il peut checker si option, ou l'action passe).

        // Qui est le dernier relanceur ?
        // Si `lastActor` a misé ou relancé à `currentMaxBetThisRound`, alors l'action continue jusqu'à ce que
        // tous les autres aient suivi, sur-relancé ou se soient couchés.
        // Si `lastActor` a suivi (`CALL`) `currentMaxBetThisRound`, ou a checké (`CHECK`) quand `currentMaxBetThisRound` était 0,
        // et que tous les autres joueurs ont déjà misé `currentMaxBetThisRound` ou sont all-in/folded, alors le tour est terminé.

        // Si l'action revient au joueur qui a misé/relancé en dernier, et que ce joueur checke (si l'option lui est donnée)
        // ou si tous les joueurs ont misé le même montant.

        // Si on arrive ici, tous les joueurs actifs ont misé `currentMaxBetThisRound` ou sont all-in pour moins.
        // Il faut s'assurer que le joueur qui a fait la dernière "grosse" mise a vu l'action lui revenir.
        // Par exemple, UTG mise, CO relance, Bouton suit, UTG doit encore parler.
        // Si UTG suit alors, le tour est terminé.

        // Le joueur qui a fait la dernière mise (non-check) est-il celui qui vient d'agir ?
        // Et tous les autres sont-ils alignés ou couchés ?

        // Si currentBetToCall > 0 (il y a eu une mise ou une relance)
        // et que le joueur qui vient d'agir (lastActor) a misé ce montant (par call, bet ou raise)
        // et que tous les autres joueurs encore dans la main ont soit misé ce montant, soit sont all-in, soit sont folded.
        // Alors le tour est terminé.

        // Exception : Big Blind preflop peut relancer même si tout le monde a callé sa BB.
        // C'est "l'option".
        if (currentStateBuilderSnapshot.build().getCurrentBettingRound() == GameState.BettingRound.PREFLOP) {
            int bbIndex = (dealerButtonPosition + (players.size() > 2 ? 2 : 1)) % players.size();
            Player bbPlayer = players.get(bbIndex);
            // Si c'est au BB de parler, qu'il n'a pas encore relancé, et que la mise actuelle est juste la BB.
            boolean bbHasOption = lastActor.equals(players.get((bbIndex -1 + players.size()) % players.size())) && // Joueur avant BB vient d'agir
                                  actingPlayer.equals(bbPlayer) && // C'est au BB de parler
                                  !bbPlayer.isAllIn() && // BB n'est pas all-in
                                  bbPlayer.getAmountBetThisRound() == this.bigBlindAmount && // BB a juste sa blinde
                                  currentMaxBetThisRound == this.bigBlindAmount && // La mise à suivre est la BB
                                  currentStateBuilderSnapshot.build().getActionHistoryThisRound().stream() // BB n'a pas encore relancé
                                      .filter(s -> s.contains(bbPlayer.getName()) && (s.contains("RAISE") || s.contains("BET")))
                                      .count() <= 1; // (sa blinde initiale compte comme un BET)

            if (bbHasOption && action.getType() == Action.ActionType.CHECK) { // BB check son option
                return true;
            }
            if (bbHasOption && (action.getType() == Action.ActionType.RAISE || action.getType() == Action.ActionType.BET)) { // BB relance
                return false; // Le tour continue
            }
        }


        // Si le joueur qui vient d'agir (lastActor) a fait une action non-agressive (CHECK ou CALL),
        // et que tous les autres joueurs actifs ont misé le même montant ou sont all-in,
        // alors le tour est terminé.
        if ((lastActor.getStatus() == Player.PlayerStatus.ACTIVE || lastActor.isAllIn()) && // Le joueur est toujours dans le coup
            (action.getType() == Action.ActionType.CHECK || action.getType() == Action.ActionType.CALL) ) {
            // Vérifier si tous les autres joueurs actifs ont mis le même montant
            // ou sont all-in.
            boolean allOthersAligned = true;
            for (Player p : playersInHand) {
                if (p.equals(lastActor)) continue; // On se compare pas à soi-même pour cette vérif
                if (!p.isAllIn() && p.getAmountBetThisRound() < currentMaxBetThisRound) {
                    allOthersAligned = false;
                    break;
                }
            }
            if (allOthersAligned) return true;
        }

        // Si le joueur qui vient d'agir a misé ou relancé, le tour n'est pas terminé
        // sauf si tous les autres se sont couchés. (déjà géré par playersInHand.size() <= 1)
        if (action.getType() == Action.ActionType.BET || action.getType() == Action.ActionType.RAISE) {
             if (playersInHand.size() > 1) return false; // Il faut que les autres répondent
        }

        return allMatchedOrAllIn; // Si on arrive ici, c'est que tout le monde a égalisé ou est all-in.
    }


    private void advanceToNextRound(GameState.Builder nextStateBuilder) {
        GameState.BettingRound currentRound = nextStateBuilder.build().getCurrentBettingRound();
        List<Card> community = new ArrayList<>(nextStateBuilder.build().getCommunityCards());

        if (countActivePlayersNotFoldedOrOut() <= 1) {
            concludeHand(nextStateBuilder);
            return;
        }

        switch (currentRound) {
            case PREFLOP:
                logger.info("Advancing to FLOP");
                nextStateBuilder.setCurrentBettingRound(GameState.BettingRound.FLOP);
                deck.dealCard(); // Burn card
                community.addAll(deck.dealCards(3));
                nextStateBuilder.setCommunityCards(community);
                break;
            case FLOP:
                logger.info("Advancing to TURN");
                nextStateBuilder.setCurrentBettingRound(GameState.BettingRound.TURN);
                deck.dealCard(); // Burn card
                community.add(deck.dealCard());
                nextStateBuilder.setCommunityCards(community);
                break;
            case TURN:
                logger.info("Advancing to RIVER");
                nextStateBuilder.setCurrentBettingRound(GameState.BettingRound.RIVER);
                deck.dealCard(); // Burn card
                community.add(deck.dealCard());
                nextStateBuilder.setCommunityCards(community);
                break;
            case RIVER:
                logger.info("Advancing to SHOWDOWN");
                nextStateBuilder.setCurrentBettingRound(GameState.BettingRound.SHOWDOWN);
                // Pas de nouvelles cartes. La prochaine étape est de déterminer le gagnant.
                concludeHand(nextStateBuilder); // Le showdown fait partie de la conclusion
                return; // La main est terminée après cela.
            case SHOWDOWN: // Normalement, on ne devrait pas appeler advanceToNextRound depuis SHOWDOWN
            case HAND_OVER:
                logger.warn("Tried to advance round from {} or HAND_OVER state. This should not happen.", currentRound);
                nextStateBuilder.setHandOver(true);
                return;
        }

        // Si la main n'est pas encore terminée (ex: on est passé au Flop)
        if (!nextStateBuilder.build().isHandOver()) {
            // Réinitialiser les mises pour le nouveau tour
            nextStateBuilder.setCurrentBetToCall(0);
            nextStateBuilder.setMinRaiseAmount(this.bigBlindAmount); // Ou la plus petite mise permise
            for (Player p : this.players) { // Utiliser la liste de joueurs du GameEngine
                if (p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT) {
                    p.resetAmountBetThisRound(); // Important pour le nouveau tour de mise
                }
            }
             nextStateBuilder.setPlayers(new ArrayList<>(this.players)); // S'assurer que les joueurs sont à jour

            // Déterminer le premier joueur à parler dans le nouveau tour (généralement SB ou premier actif après SB)
            int nextPlayerIndex = findNextActivePlayer((dealerButtonPosition + 1) % players.size(), true);
            nextStateBuilder.setCurrentPlayerIndex(nextPlayerIndex);
            if (nextPlayerIndex != -1) {
                 nextStateBuilder.setLegalActions(determineLegalActions(this.players.get(nextPlayerIndex), nextStateBuilder.build()));
            } else {
                // Tous les joueurs sont folded ou all-in, la main devrait se terminer.
                logger.info("No active player to start the new round {}. Concluding hand.", nextStateBuilder.build().getCurrentBettingRound());
                concludeHand(nextStateBuilder);
            }
        }
    }

    private void concludeHand(GameState.Builder finalStateBuilder) {
        logger.info("Concluding hand. Current round: {}", finalStateBuilder.build().getCurrentBettingRound());
        finalStateBuilder.setHandOver(true);
        finalStateBuilder.setCurrentPlayerIndex(-1); // Plus personne ne joue
        finalStateBuilder.setLegalActions(Collections.emptyList());

        List<Player> playersInPot = this.players.stream()
                .filter(p -> p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT)
                .collect(Collectors.toList());

        if (playersInPot.isEmpty()) {
            // Cela ne devrait pas arriver si le pot a de l'argent.
            // Peut-être que tout le monde s'est couché et le dernier à miser récupère les blindes.
            // Ou si le pot est vide et tout le monde s'est couché (bug?).
            logger.warn("Hand concluded with no players in pot. This might be an issue.");
            // Chercher le dernier joueur non-OUT, même s'il est FOLDED, s'il a misé.
            // Ou attribuer le pot au dernier joueur à ne pas s'être couché.
            // Si tout le monde s'est couché sauf un, ce joueur gagne le pot.
            List<Player> notFoldedPlayers = this.players.stream()
                                .filter(p -> p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT)
                                .collect(Collectors.toList());
            if (notFoldedPlayers.size() == 1) {
                 Player winner = notFoldedPlayers.get(0);
                 winner.addToStack(finalStateBuilder.build().getPotSize());
                 logger.info("{} wins pot of {} as everyone else folded.", winner.getName(), finalStateBuilder.build().getPotSize());
                 finalStateBuilder.setWinners(List.of(winner));
                 finalStateBuilder.setWinningHand(null); // Pas de showdown
                 // Mettre à jour l'état du joueur dans la liste du builder
                 updatePlayerInBuilder(finalStateBuilder, winner);
            } else if (notFoldedPlayers.isEmpty() && countActivePlayersNotFoldedOrOut() == 0) {
                 // Cas où le dernier joueur actif s'est couché face à une mise (ex: SB se couche face à BB qui n'a pas relancé)
                 // ou si tous les joueurs se sont couchés en séquence. Le dernier à avoir misé (ou BB) gagne.
                 // C'est complexe. Pour l'instant, on va supposer que cela est géré par le flux normal.
                 // Si on arrive ici, c'est probablement que le pot doit être partagé ou retourné.
                 // Ou, plus simplement, le dernier joueur à ne pas s'être couché gagne.
                 // Le GameState devrait avoir une trace de qui a gagné.
                 // Si playersInPot est vide, mais qu'il y a un pot, c'est un problème.
                 // Le pot devrait être attribué au dernier joueur qui n'a pas fold.
                 List<Player> nonFoldedOrOut = this.players.stream()
                                 .filter(p -> p.getStatus() != Player.PlayerStatus.OUT)
                                 .sorted(Comparator.comparingInt(p -> p.getStatus() == Player.PlayerStatus.FOLDED ? 1:0)) // Ceux non folded en premier
                                 .collect(Collectors.toList());
                 if(!nonFoldedOrOut.isEmpty() && nonFoldedOrOut.stream().filter(p-> p.getStatus() != Player.PlayerStatus.FOLDED).count() <=1) {
                     Player winner = nonFoldedOrOut.stream().filter(p-> p.getStatus() != Player.PlayerStatus.FOLDED).findFirst().orElse(nonFoldedOrOut.get(0));
                     winner.addToStack(finalStateBuilder.build().getPotSize());
                     logger.info("{} wins pot of {} by default (others folded or hand ended abruptly).", winner.getName(), finalStateBuilder.build().getPotSize());
                     finalStateBuilder.setWinners(List.of(winner));
                     updatePlayerInBuilder(finalStateBuilder, winner);
                 } else {
                      logger.error("Hand concluded with no clear winner and pot > 0. Pot size: {}", finalStateBuilder.build().getPotSize());
                 }
            }

        } else if (playersInPot.size() == 1) {
            // Un seul joueur restant, il gagne le pot.
            Player winner = playersInPot.get(0);
            winner.addToStack(finalStateBuilder.build().getPotSize());
            logger.info("{} wins pot of {} as the only remaining player.", winner.getName(), finalStateBuilder.build().getPotSize());
            finalStateBuilder.setWinners(List.of(winner));
            finalStateBuilder.setWinningHand(null); // Pas de showdown
            updatePlayerInBuilder(finalStateBuilder, winner);
        } else {
            // Showdown: plusieurs joueurs sont encore dans le coup.
            logger.info("Showdown! Players: {}", playersInPot.stream().map(Player::getName).collect(Collectors.joining(", ")));
            // TODO: Gérer les side pots correctement. Pour l'instant, un seul pot principal.

            Map<Player, Hand> playerHands = new HashMap<>();
            for (Player p : playersInPot) {
                if (p.getHoleCards().size() == 2) { // Assurez-vous que le joueur a des cartes (n'est pas OUT sans cartes)
                    Hand bestHand = Hand.evaluateBestHand(p.getHoleCards(), finalStateBuilder.build().getCommunityCards());
                    playerHands.put(p, bestHand);
                    logger.info("{} has hand: {} ({})", p.getName(), bestHand, p.getHoleCards());
                } else {
                     logger.warn("Player {} is in pot but has no hole cards for showdown.", p.getName());
                }
            }

            if (playerHands.isEmpty()) {
                logger.error("Showdown with no valid hands to compare. Pot: {}", finalStateBuilder.build().getPotSize());
                // Que faire ici ? Partager le pot entre les joueurs de playersInPot ?
                // Cela indique un problème en amont.
                return;
            }

            // Trouver la meilleure main (ou les meilleures mains en cas d'égalité)
            List<Player> winners = new ArrayList<>();
            Hand bestOverallHand = null;

            for (Map.Entry<Player, Hand> entry : playerHands.entrySet()) {
                if (bestOverallHand == null || entry.getValue().compareTo(bestOverallHand) > 0) {
                    bestOverallHand = entry.getValue();
                    winners.clear();
                    winners.add(entry.getKey());
                } else if (entry.getValue().compareTo(bestOverallHand) == 0) {
                    winners.add(entry.getKey());
                }
            }

            if (winners.isEmpty() || bestOverallHand == null) {
                 logger.error("Showdown resulted in no winners. This is a bug. Pot: {}", finalStateBuilder.build().getPotSize());
                 // Fallback: peut-être que le pot reste pour la prochaine main ou est partagé ?
                 // Pour l'instant, on logue l'erreur.
                 return;
            }

            finalStateBuilder.setWinningHand(bestOverallHand);
            finalStateBuilder.setWinners(new ArrayList<>(winners)); // Copie

            // Distribuer le pot
            double potPerWinner = finalStateBuilder.build().getPotSize() / winners.size();
            logger.info("Winning hand: {}. Pot of {} split among {} winner(s):", bestOverallHand, finalStateBuilder.build().getPotSize(), winners.size());
            for (Player winner : winners) {
                winner.addToStack(potPerWinner);
                logger.info("- {} wins {}", winner.getName(), String.format("%.2f", potPerWinner));
                updatePlayerInBuilder(finalStateBuilder, winner);
            }
        }
        // Mettre à jour les stacks des joueurs dans la liste globale du GameEngine
        // La liste dans le builder est déjà à jour si updatePlayerInBuilder a été appelé.
        // Mais il faut que la liste `this.players` du GameEngine soit aussi mise à jour.
        List<Player> builderPlayers = finalStateBuilder.build().getPlayers();
        for(Player enginePlayer : this.players) {
            for(Player builderPlayer : builderPlayers) {
                if(enginePlayer.getId().equals(builderPlayer.getId())) {
                    enginePlayer.setStack(builderPlayer.getStack());
                    enginePlayer.setStatus(builderPlayer.getStatus()); // S'assurer que le statut est aussi à jour
                    break;
                }
            }
        }
        // Filtrer les joueurs OUT
        long activePlayerCount = this.players.stream().filter(p -> p.getStack() > 0 && p.getStatus() != Player.PlayerStatus.OUT).count();
        if (activePlayerCount < 2) {
             logger.info("Game over. Not enough players to continue.");
             // On pourrait mettre un état "GAME_OVER" dans GameState.
             // Pour l'instant, startNewHand gérera le cas où il n'y a pas assez de joueurs.
        }

    }

    private void updatePlayerInBuilder(GameState.Builder builder, Player playerToUpdate) {
        List<Player> currentPlayersInBuilder = new ArrayList<>(builder.build().getPlayers());
        boolean found = false;
        for (int i = 0; i < currentPlayersInBuilder.size(); i++) {
            if (currentPlayersInBuilder.get(i).getId().equals(playerToUpdate.getId())) {
                currentPlayersInBuilder.set(i, playerToUpdate); // Remplace par la version mise à jour
                found = true;
                break;
            }
        }
        if (found) {
            builder.setPlayers(currentPlayersInBuilder);
        } else {
            logger.warn("Tried to update player {} in GameState.Builder, but player not found.", playerToUpdate.getName());
        }
    }


    private int findNextActivePlayer(int startIndex, boolean allowCurrent) {
        int numPlayers = players.size();
        if (numPlayers == 0) return -1;

        for (int i = 0; i < numPlayers; i++) {
            int playerIndex = (startIndex + i) % numPlayers;
            if (!allowCurrent && playerIndex == startIndex && i > 0) {
                // On a fait un tour complet sans trouver de joueur suivant différent du start (si allowCurrent=false)
                // Cela ne devrait pas arriver si on cherche le *prochain* joueur.
                // Si allowCurrent est vrai, on peut retourner startIndex si c'est le seul joueur actif.
            }
            if (!allowCurrent && playerIndex == startIndex && i==0) { // Ne pas considérer le joueur de départ si on cherche le *prochain*
                 if (numPlayers == 1 && players.get(playerIndex).isActive()) return playerIndex; // Sauf si c'est le seul joueur
                 continue;
            }


            Player p = players.get(playerIndex);
            if (p.getStatus() == Player.PlayerStatus.ACTIVE && p.getStack() > 0) {
                // Vérifier si le joueur est all-in mais toujours actif (rare, mais possible si all-in pre-bet)
                // Non, si stack > 0 et ACTIVE, il peut jouer. Si stack == 0, il devrait être ALL_IN ou FOLDED.
                return playerIndex;
            }
        }
        // Aucun joueur actif trouvé (tous FOLDED ou ALL_IN ou OUT)
        return -1;
    }

    private long countActivePlayers() {
        return players.stream().filter(p -> p.getStatus() == Player.PlayerStatus.ACTIVE && p.getStack() > 0).count();
    }

    private long countActivePlayersNotFoldedOrOut() {
        return players.stream().filter(p -> p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT).count();
    }


    /**
     * Détermine les actions légales pour un joueur donné dans l'état actuel du jeu.
     *
     * @param player Le joueur dont c'est le tour.
     * @param gs     L'état actuel du jeu.
     * @return Une liste d'actions légales.
     */
    public List<Action> determineLegalActions(Player player, GameState gs) {
        if (player == null || gs == null || player.getStatus() == Player.PlayerStatus.FOLDED || player.getStatus() == Player.PlayerStatus.OUT || player.isAllIn()) {
            return Collections.emptyList(); // Pas d'actions si couché, out, ou déjà all-in.
        }

        List<Action> legal = new ArrayList<>();
        double amountToCall = gs.getAmountToCallForPlayer(player);

        // FOLD est toujours légal (sauf si le joueur est all-in et que c'est un showdown forcé)
        legal.add(new Action(Action.ActionType.FOLD));

        // CHECK est légal si amountToCall est 0 (aucune mise à suivre)
        if (amountToCall == 0) {
            legal.add(new Action(Action.ActionType.CHECK));
        }

        // CALL est légal si amountToCall > 0 et que le joueur a assez pour suivre ou peut aller all-in.
        if (amountToCall > 0) {
            // Le montant du call est amountToCall, ou le stack du joueur si moins.
            // L'action de CALL n'a pas de montant explicite, elle suit la mise.
            legal.add(new Action(Action.ActionType.CALL));
        }

        // BET est légal si amountToCall est 0 (personne n'a misé avant dans ce tour)
        // et que le joueur a des jetons pour miser.
        // Le montant de la mise doit être au moins la grosse blinde (ou une fraction si all-in).
        if (amountToCall == 0 && player.getStack() > 0) {
            // Montants de BET possibles: min_bet (BB), pot size, all-in.
            // Pour l'IA, on pourrait prédéfinir des tailles de mise (ex: 1/2 pot, pot, 2x pot)
            // Pour l'instant, on ajoute une action BET générique. L'IA devra choisir un montant.
            // On va ajouter des tailles de mise spécifiques pour l'IA DQNAgent.
            // Ici, on peut définir le minimum et le maximum.
            double minBet = Math.min(player.getStack(), this.bigBlindAmount);
            if (player.getStack() > 0) {
                 // L'IA choisira le montant. Ici on indique juste que BET est possible.
                 // Pour simplifier, on pourrait créer plusieurs actions BET avec des montants fixes.
                 // Exemple: BET BB, BET Pot/2, BET Pot, BET All-in

                 // Action BET (l'IA devra fournir le montant)
                 // On va plutôt ajouter des actions spécifiques pour l'IA
                 if (player.getStack() >= this.bigBlindAmount) {
                    legal.add(new Action(Action.ActionType.BET, this.bigBlindAmount)); // Miser la BB
                 }
                 // Miser la moitié du pot (si le pot > 0)
                 if (gs.getPotSize() > 0 && player.getStack() >= gs.getPotSize() / 2) {
                     if (gs.getPotSize() / 2 >= this.bigBlindAmount) { // La mise doit être au moins BB
                        legal.add(new Action(Action.ActionType.BET, gs.getPotSize() / 2));
                     }
                 }
                 // Miser le pot
                 if (gs.getPotSize() > 0 && player.getStack() >= gs.getPotSize()) {
                     if (gs.getPotSize() >= this.bigBlindAmount) {
                        legal.add(new Action(Action.ActionType.BET, gs.getPotSize()));
                     }
                 }
                 // Miser All-in (si différent des autres options)
                 if (player.getStack() > 0 ) { //  && player.getStack() > this.bigBlindAmount - si on veut éviter bet allin < BB
                     legal.add(new Action(Action.ActionType.BET, player.getStack()));
                 }
            }
        }

        // RAISE est légal si amountToCall > 0 (il y a une mise à suivre)
        // et que le joueur a assez pour relancer (au moins le double de la mise précédente, ou all-in).
        // Le montant de la relance doit être au moins gs.getMinRaiseAmount().
        if (amountToCall > 0 && player.getStack() > amountToCall) { // Doit avoir plus que le call pour relancer
            // Le montant minimum pour une relance valide (montant total de la mise du joueur)
            double minTotalRaisePlayerBet = gs.getMinRaiseAmount();

            // Si le joueur n'a pas assez pour un min-raise complet mais est all-in, c'est permis.
            if (player.getStack() + player.getAmountBetThisRound() < minTotalRaisePlayerBet) {
                 // All-in raise (under-raise)
                 legal.add(new Action(Action.ActionType.RAISE, player.getStack() + player.getAmountBetThisRound()));
            } else {
                // Tailles de relance possibles : min-raise, pot-size raise, all-in.
                // Min-raise (total)
                if (player.getStack() + player.getAmountBetThisRound() >= minTotalRaisePlayerBet) {
                    legal.add(new Action(Action.ActionType.RAISE, minTotalRaisePlayerBet));
                }

                // Raise pot (le pot après avoir suivi la mise actuelle)
                double potAfterCall = gs.getPotSize() + amountToCall; // Le pot si le joueur actuel suit
                                                                     // Non, le pot actuel + la mise à suivre par CE joueur
                                                                     // + ce que le joueur a déjà misé ce tour.
                // Le montant d'une relance à la taille du pot est:
                // Pot actuel + toutes les mises sur la table (y compris celle à suivre) + la mise à suivre elle-même.
                // Exemple: Pot=100. Joueur A mise 50. Joueur B veut relancer pot.
                // Joueur B doit suivre 50. Pot devient 100+50+50=200. Joueur B relance de 200. Mise totale de B = 50+200=250.
                double raiseToPotSizeAmount = gs.getPotSize() + (2 * amountToCall) + player.getAmountBetThisRound(); // Estimation simple
                                            // Correct: Mise totale = Call + (Pot_avant_relance + Call)
                                            // Mise totale = amountToCall + (gs.getPotSize() + amountToCall + player.getAmountBetThisRound())
                                            // Non, c'est plus simple:
                                            // Le montant à ajouter pour la relance est : (Pot actuel + mises du tour)
                                            // Mise totale du joueur = Mise actuelle à suivre + (Pot total avant sa relance)
                                            // Pot total avant sa relance = gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound()) [ce qu'il doit ajouter pour call]
                                            // Mise totale = gs.getCurrentBetToCall() + (gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound()))
                                            // Si le joueur a déjà misé X, et doit suivre Y (donc currentBet = X+Y)
                                            // Sa relance "pot" sera de (Pot + (X+Y)) en plus de son call.
                                            // Donc sa mise totale sera (X+Y) + (Pot + (X+Y)).
                                            // Pot = gs.getPotSize()
                                            // Total bet this round by others = gs.getCurrentBetToCall()
                                            // Player's current bet this round = player.getAmountBetThisRound()
                                            // Amount player needs to add to call = gs.getCurrentBetToCall() - player.getAmountBetThisRound()
                                            // Pot size if player calls = gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound())
                                            // No, pot size if player calls = gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound())
                                            // Amount of the raise = Current Pot + Sum of all bets in the current round (including the call amount)
                                            // Total Pot before raise = gs.getPotSize()
                                            // Amount to call for current player = amountToCall
                                            // Total amount to raise = Total Pot before raise + amountToCall
                                            // Total bet by player = amountToCall (to match) + (Total Pot before raise + amountToCall) (the raise itself)
                                            // Total bet = player.getAmountBetThisRound() + amountToCall + (gs.getPotSize() + amountToCall)
                                            // Total bet = gs.getCurrentBetToCall() + gs.getPotSize() + amountToCall
                                            // This is "pot-sized raise" where the raise amount equals the size of the pot *after the call*.
                                            // Pot after call = gs.getPotSize() + amountToCall
                                            // Raise amount = Pot after call
                                            // Total player bet = amountToCall (for the call) + (gs.getPotSize() + amountToCall) (for the raise)
                                            //                = gs.getCurrentBetToCall() (total amount of current bet) + gs.getPotSize() (pot before this bet) + amountToCall (the call part of this bet)
                double totalPotSizeRaiseBet = gs.getCurrentBetToCall() + gs.getPotSize() + amountToCall; // Total mise du joueur
                                                                                                        // Ce n'est pas standard.
                // Standard PLO pot raise: 3 * last_bet/raise + pot_before_last_bet/raise
                // Plus simple: mise totale = mise à suivre + (pot actuel + mise à suivre)
                // mise totale = currentBetToCall + (potSize + currentBetToCall) -> non, currentBetToCall est déjà le total.
                // mise totale = amountToCall (pour ce joueur) + (potSize + amountToCall (pour ce joueur))
                // Mise totale = player.getAmountBetThisRound() + amountToCall + (gs.getPotSize() + amountToCall)
                // Mise totale = gs.getCurrentBetToCall() + gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound())

                // Le montant d'une relance "pot" est : la taille du pot *après* avoir fait le call.
                // Donc, le joueur calle `amountToCall`. Le pot devient `gs.getPotSize() + amountToCall`.
                // Le joueur relance de ce montant. Sa mise totale est `amountToCall + (gs.getPotSize() + amountToCall)`.
                // Ceci est le montant que le joueur mettrait en plus de sa mise déjà engagée.
                // Non, `action.getAmount()` est le montant *total* de la mise du joueur pour le tour.
                // Donc, si le joueur relance "pot", son montant total sera:
                // `player.getAmountBetThisRound()` (déjà misé) + `amountToCall` (pour suivre) + `(gs.getPotSize() + amountToCall)` (la relance)
                // = `gs.getCurrentBetToCall()` (montant total pour suivre) + `gs.getPotSize() + amountToCall`
                // Exemple: Pot 100. Blinds 5/10. UTG call 10. MP call 10. CO raise to 40.
                // Bouton veut relancer pot.
                // Bouton doit payer 40 pour call. Pot avant sa relance = 100(init) + 10(SB) + 10(BB) + 10(UTG) + 10(MP) + 40(CO) = 180.
                // Non, pot actuel = 100 (ce qui est déjà collecté) + 10(SB bet) + 10(BB bet) + 10(UTG bet) + 10(MP bet) + 40(CO bet) = 180.
                // Pot = gs.getPotSize() = 180.
                // amountToCall pour Bouton = 40 (car il n'a rien misé).
                // Mise totale du Bouton = 40 (call) + (180 (pot) + 40 (son call)) = 40 + 220 = 260.
                double potSizedRaiseTotalBet = gs.getCurrentBetToCall() + (gs.getPotSize() + (gs.getCurrentBetToCall() - player.getAmountBetThisRound()));


                if (player.getStack() + player.getAmountBetThisRound() >= potSizedRaiseTotalBet && potSizedRaiseTotalBet > minTotalRaisePlayerBet) {
                    legal.add(new Action(Action.ActionType.RAISE, potSizedRaiseTotalBet));
                }

                // All-in raise
                if (player.getStack() > 0) { //  player.getStack() + player.getAmountBetThisRound() > minTotalRaisePlayerBet (si on veut que ce soit un "vrai" raise)
                    legal.add(new Action(Action.ActionType.RAISE, player.getStack() + player.getAmountBetThisRound()));
                }
            }
        }

        // Dédupliquer et s'assurer que les montants sont valides (ex: un BET all-in et un RAISE all-in peuvent être identiques)
        // Et s'assurer que les montants de BET/RAISE sont > 0.
        // Et que RAISE est > CALL.
        List<Action> uniqueLegalActions = new ArrayList<>();
        Set<String> seenActions = new HashSet<>(); // Pour dédupliquer basé sur type et montant

        for (Action act : legal) {
            String key = act.getType().toString();
            if (act.getType() == Action.ActionType.BET || act.getType() == Action.ActionType.RAISE) {
                if (act.getAmount() <= 0) continue; // Ignorer mises/relances de 0 ou moins
                if (act.getType() == Action.ActionType.RAISE && act.getAmount() <= gs.getCurrentBetToCall() && (player.getStack()+player.getAmountBetThisRound()) > act.getAmount()) {
                    // Une relance doit être supérieure à la mise actuelle, sauf si c'est un all-in pour moins.
                    continue;
                }
                 if (act.getType() == Action.ActionType.BET && act.getAmount() < this.bigBlindAmount && (player.getStack() > act.getAmount())) {
                    // Une mise doit être au moins la BB, sauf si all-in pour moins.
                    continue;
                 }

                key += String.format("%.2f", act.getAmount());
            }
            if (!seenActions.contains(key)) {
                uniqueLegalActions.add(act);
                seenActions.add(key);
            }
        }
        // Trier les actions pour la cohérence (FOLD, CHECK, CALL, BETs croissants, RAISEs croissants)
        uniqueLegalActions.sort(Comparator.comparing(Action::getType)
            .thenComparingDouble(Action::getAmount));

        return uniqueLegalActions;
    }


    public GameState getCurrentGameState() {
        return currentGameState;
    }

    // Méthode utilitaire pour obtenir les joueurs (principalement pour les tests ou l'interface utilisateur)
    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }
}
