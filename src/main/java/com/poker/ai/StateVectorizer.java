package com.poker.ai;

import com.poker.engine.Card;
import com.poker.engine.GameState;
import com.poker.engine.Player;
import com.poker.engine.Action;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Responsable de la conversion d'un objet {@link GameState} en un vecteur numérique (INDArray).
 * Ce vecteur servira d'entrée pour le réseau de neurones de l'IA.
 * Les valeurs du vecteur sont normalisées (généralement entre 0 et 1 ou -1 et 1).
 */
public class StateVectorizer {

    // Dimensions pour l'encodage one-hot des cartes
    private static final int SUIT_COUNT = Card.Suit.values().length; // 4
    private static final int RANK_COUNT = Card.Rank.values().length; // 13
    private static final int CARD_VECTOR_SIZE = SUIT_COUNT * RANK_COUNT; // 52

    // Taille maximale de l'historique des actions à considérer (par joueur, par tour de mise)
    // Ceci est une simplification. Un historique plus complet pourrait être plus complexe.
    private static final int MAX_ACTION_HISTORY_PER_ROUND_TYPE = 3; // e.g., last 3 actions (bet/raise, call, check, fold)

    // Nombre de joueurs maximum (pour fixer la taille du vecteur pour les infos des autres joueurs)
    // Si on veut une taille de vecteur fixe, il faut un nombre max de joueurs.
    // Alternativement, la taille du vecteur peut varier, ou on encode seulement N adversaires les plus pertinents.
    // Pour l'instant, supposons une table de 6 joueurs max.
    private static final int MAX_PLAYERS_FOR_VECTOR = 6;


    private final int vectorSize;

    public StateVectorizer(int maxPlayersInGame) {
        // Calcul de la taille du vecteur d'état. C'est crucial et doit être cohérent.
        int size = 0;
        // 1. Cartes en main du joueur (2 cartes * 52 bits one-hot)
        size += 2 * CARD_VECTOR_SIZE;
        // 2. Cartes communes sur le board (5 cartes * 52 bits one-hot)
        size += 5 * CARD_VECTOR_SIZE;
        // 3. Position du joueur (normalisée, ex: 0 à 1 par rapport au nombre de joueurs)
        size += 1;
        // 4. Stack du joueur / stack moyen à la table (ou / stack initial)
        size += 1;
        // 5. Taille du pot / stack initial moyen (ou / somme des stacks initiaux)
        size += 1;
        // 6. Tour de mise actuel (Preflop, Flop, Turn, River) - one-hot encodé
        size += GameState.BettingRound.values().length; // Moins Showdown/Hand_Over si non pertinents pour décision
        // 7. Montant à payer pour suivre (normalisé par rapport au pot ou au stack)
        size += 1;
        // 8. Montant minimum pour relancer (normalisé)
        size += 1;

        // 9. Informations sur les autres joueurs (simplifié):
        // Pour chaque autre joueur (jusqu'à maxPlayersInGame - 1):
        //    - Est-il actif/foldé/all-in? (one-hot, 3 bits)
        //    - Son stack relatif au stack du joueur actuel (ou au stack moyen)
        //    - Sa mise actuelle dans le tour (relative au pot ou à la BB)
        //    - Son agressivité perçue (simplifié, ex: nombre de mises/relances récentes)
        // Pour l'instant, simplifions :
        // Pour chaque siège (maxPlayersInGame):
        //    - Est-ce le joueur actuel ? (1 bit)
        //    - Est-il actif dans la main (pas fold/out) ? (1 bit)
        //    - Son stack normalisé (par rapport au stack initial du joueur actuel) (1 float)
        //    - Sa mise ce tour normalisée (par rapport à la BB) (1 float)
        //    - Sa position relative au joueur actuel (cyclique, ex: -2, -1, 0, 1, 2 pour 5 autres joueurs) (1 float)
        // Ceci est un exemple, la représentation des adversaires est un domaine de recherche actif.
        // Pour une première version, on peut se concentrer sur les infos globales et les infos du joueur.
        // Ajoutons une version simplifiée pour les adversaires :
        // Pour (MAX_PLAYERS_FOR_VECTOR - 1) adversaires potentiels:
        //    - actif (1/0)
        //    - stack normalisé (par rapport au stack initial du joueur actuel)
        //    - mise ce tour normalisée (par rapport à la BB)
        //    - a-t-il misé/relancé dans ce tour ? (1/0)
        int opponentFeatures = 4;
        size += (maxPlayersInGame - 1) * opponentFeatures;

        // 10. Historique des actions (très simplifié pour l'instant)
        //    Nombre de mises, relances, calls, folds dans le tour actuel et la main.
        //    Pourrait être un vecteur binaire des X dernières actions (type d'action + agresseur relatif).
        //    Exemple: 4 types d'actions * (nombre de tours de mise = 4) = 16 bits pour les actions du joueur.
        //    Exemple: 4 types d'actions * (nombre de tours de mise = 4) = 16 bits pour les actions des adversaires (aggrégé).
        // Pour l'instant, on omet l'historique complexe pour garder la taille du vecteur gérable.
        // On pourrait ajouter:
        //    - Nombre de joueurs actifs restants (normalisé)
        size += 1;
        //    - Nombre de relances dans le tour actuel
        size += 1;


        this.vectorSize = size;
        // Afficher la taille pour info.
        System.out.println("Calculated State Vector Size: " + this.vectorSize);
    }

    public int getVectorSize() {
        return vectorSize;
    }

    /**
     * Convertit le GameState en un INDArray pour le joueur spécifié.
     *
     * @param gameState L'état actuel du jeu.
     * @param playerId  L'ID du joueur pour lequel vectoriser l'état (sa perspective).
     * @param initialPlayerStack Le stack initial du joueur (pour la normalisation).
     * @param bigBlindAmount Le montant de la grosse blinde (pour la normalisation).
     * @return Un INDArray représentant l'état du jeu.
     */
    public INDArray vectorizeState(GameState gameState, String playerId, double initialPlayerStack, double bigBlindAmount) {
        float[] vector = new float[vectorSize];
        int currentIndex = 0;

        Player self = gameState.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player ID not found in GameState: " + playerId));

        // 1. Cartes en main du joueur (2 cartes * 52 bits one-hot)
        List<Card> holeCards = self.getHoleCards();
        currentIndex = encodeCardToVector(holeCards.size() > 0 ? holeCards.get(0) : null, vector, currentIndex);
        currentIndex = encodeCardToVector(holeCards.size() > 1 ? holeCards.get(1) : null, vector, currentIndex);

        // 2. Cartes communes sur le board (5 cartes * 52 bits one-hot)
        List<Card> communityCards = gameState.getCommunityCards();
        for (int i = 0; i < 5; i++) {
            currentIndex = encodeCardToVector(i < communityCards.size() ? communityCards.get(i) : null, vector, currentIndex);
        }

        // 3. Position du joueur (normalisée)
        // Position relative au bouton dealer, ou nombre de joueurs à parler après soi.
        // Pourrait être 0 (SB) à N-1 (Bouton). Normalisé par N-1.
        // Ou position absolue (0 à N-1) normalisée par N-1.
        // Ou position par rapport au bouton (0 = bouton, 1 = SB, etc.)
        // Ou nombre de joueurs actifs restants à parler après ce joueur.
        // Simplifions : position absolue / (nombre de joueurs - 1)
        int numPlayersTotal = gameState.getPlayers().size();
        if (numPlayersTotal > 1) {
            vector[currentIndex++] = (float) self.getPosition() / (numPlayersTotal - 1.0f);
        } else {
            vector[currentIndex++] = 0.5f; // Valeur neutre si un seul joueur
        }


        // 4. Stack du joueur / stack initial du joueur (ou / stack moyen)
        vector[currentIndex++] = normalizeStack(self.getStack(), initialPlayerStack);

        // 5. Taille du pot / (nombre de joueurs * stack initial moyen) ou / (BB * 100)
        // Normalisation par rapport à un multiple de la BB (ex: 100 BBs pot est un gros pot)
        // Ou par rapport au stack initial total.
        float totalInitialStack = 0;
        for(Player p : gameState.getPlayers()) totalInitialStack += initialPlayerStack; // Approximation
        if (totalInitialStack == 0) totalInitialStack = initialPlayerStack * numPlayersTotal;
        if (totalInitialStack == 0 && bigBlindAmount > 0) totalInitialStack = bigBlindAmount * 100 * numPlayersTotal; // fallback
        if (totalInitialStack == 0) totalInitialStack = 1; // éviter division par zéro

        vector[currentIndex++] = normalizePot(gameState.getPotSize(), totalInitialStack);


        // 6. Tour de mise actuel (one-hot)
        GameState.BettingRound currentRound = gameState.getCurrentBettingRound();
        GameState.BettingRound[] rounds = {GameState.BettingRound.PREFLOP, GameState.BettingRound.FLOP, GameState.BettingRound.TURN, GameState.BettingRound.RIVER};
        for (GameState.BettingRound r : rounds) { // On ne met que les rounds de décision
            vector[currentIndex++] = (currentRound == r) ? 1.0f : 0.0f;
        }
        // Si on a plus de place dans le vecteur à cause du calcul de `size` dans le constructeur,
        // il faut ajuster le calcul de `size` pour GameState.BettingRound.values().length
        // et ici ne pas encoder SHOWDOWN et HAND_OVER.

        // 7. Montant à payer pour suivre (normalisé par rapport au stack du joueur ou au pot)
        double amountToCall = gameState.getAmountToCallForPlayer(self);
        if (self.getStack() > 0) {
            vector[currentIndex++] = (float) Math.min(1.0, amountToCall / self.getStack());
        } else {
            vector[currentIndex++] = 0.0f;
        }

        // 8. Montant minimum pour relancer (normalisé par rapport au stack du joueur ou au pot)
        // Le minRaiseAmount dans GameState est le montant TOTAL de la mise.
        // On veut peut-être la taille de la relance elle-même (MinRaiseAmount - CurrentBetToCall)
        double minRaiseTotalBet = gameState.getMinRaiseAmount();
        double additionalRaiseAmount = minRaiseTotalBet - gameState.getCurrentBetToCall();
        if (additionalRaiseAmount < bigBlindAmount && gameState.getCurrentBetToCall() > 0) { // Si c'est une "vraie" relance
            additionalRaiseAmount = bigBlindAmount; // Le minimum à ajouter est souvent la BB ou la relance précédente
        } else if (additionalRaiseAmount <=0 && gameState.getCurrentBetToCall() ==0) { // Si c'est un "BET"
             additionalRaiseAmount = bigBlindAmount;
        }


        if (self.getStack() > 0 && additionalRaiseAmount > 0) {
            vector[currentIndex++] = (float) Math.min(1.0, additionalRaiseAmount / self.getStack());
        } else {
            vector[currentIndex++] = 0.0f;
        }

        // 9. Informations sur les autres joueurs (simplifié)
        // On va itérer sur les sièges. Le joueur "self" est à sa propre position.
        // Les autres sont relatifs.
        List<Player> allPlayers = gameState.getPlayers();
        int playerSelfIndexInAllPlayers = -1;
        for(int i=0; i<allPlayers.size(); ++i) {
            if(allPlayers.get(i).getId().equals(self.getId())) {
                playerSelfIndexInAllPlayers = i;
                break;
            }
        }

        int opponentCount = 0;
        for (int i = 0; i < allPlayers.size() && opponentCount < (MAX_PLAYERS_FOR_VECTOR -1) ; i++) {
            Player opponent = allPlayers.get((playerSelfIndexInAllPlayers + 1 + i) % allPlayers.size()); // Prochain joueur, cyclique
            if (opponent.getId().equals(self.getId())) continue; // Ne pas s'encoder soi-même comme adversaire

            // Est actif (pas fold/out)
            vector[currentIndex++] = (opponent.getStatus() != Player.PlayerStatus.FOLDED && opponent.getStatus() != Player.PlayerStatus.OUT) ? 1.0f : 0.0f;
            // Stack normalisé (par rapport au stack initial du joueur actuel)
            vector[currentIndex++] = normalizeStack(opponent.getStack(), initialPlayerStack);
            // Mise ce tour normalisée (par rapport à la BB)
            if (bigBlindAmount > 0) {
                vector[currentIndex++] = (float) Math.min(10.0, opponent.getAmountBetThisRound() / bigBlindAmount); // Cap à 10xBB
            } else {
                vector[currentIndex++] = 0.0f;
            }
            // A-t-il misé/relancé dans ce tour ? (Basé sur l'historique ou le montant misé)
            // Simplification: si amountBetThisRound > 0 et qu'il n'est pas juste une blinde non-augmentée.
            // Ou si la dernière action de ce joueur dans l'historique du tour est BET/RAISE.
            boolean hasBeenAggressive = opponent.getAmountBetThisRound() > 0; // Simplifié
            // TODO: Améliorer la détection d'agressivité.
            vector[currentIndex++] = hasBeenAggressive ? 1.0f : 0.0f;
            opponentCount++;
        }
        // Remplir les slots restants pour les adversaires avec des zéros si moins de MAX_PLAYERS_FOR_VECTOR-1 adversaires
        for (int i = opponentCount; i < (MAX_PLAYERS_FOR_VECTOR - 1); i++) {
            vector[currentIndex++] = 0.0f; // actif
            vector[currentIndex++] = 0.0f; // stack
            vector[currentIndex++] = 0.0f; // mise
            vector[currentIndex++] = 0.0f; // agressif
        }


        // 10. Nombre de joueurs actifs restants (pas folded/out, incluant self si actif)
        long activePlayersCount = gameState.getPlayers().stream()
                                    .filter(p -> p.getStatus() != Player.PlayerStatus.FOLDED && p.getStatus() != Player.PlayerStatus.OUT)
                                    .count();
        vector[currentIndex++] = (float) activePlayersCount / numPlayersTotal;

        // 11. Nombre de relances dans le tour actuel (par les autres)
        // Cela nécessite de parser l'historique des actions.
        long raisesThisRound = gameState.getActionHistoryThisRound().stream()
                                .filter(actionString -> actionString.contains("RAISE") || actionString.contains("BET")) // BET est la première "relance"
                                .count();
        // Normaliser par un nombre max de relances (ex: 4-5)
        vector[currentIndex++] = (float) Math.min(raisesThisRound, 5.0) / 5.0f;


        if (currentIndex != vectorSize) {
            throw new IllegalStateException("Mismatch in vector size. Expected: " + vectorSize + ", Actual: " + currentIndex +
                                            ". This means the calculation in the constructor and vectorization are inconsistent.");
        }

        return Nd4j.create(vector).reshape(1, vectorSize); // Reshape en [1, vectorSize] pour DL4J
    }

    /**
     * Encode une carte en un vecteur one-hot de taille 52.
     * Si la carte est null, retourne un vecteur de zéros.
     *
     * @param card La carte à encoder.
     * @param vector Le vecteur flottant où écrire les bits encodés.
     * @param startIndex L'index de départ dans le vecteur pour cette carte.
     * @return Le prochain index disponible dans le vecteur.
     */
    private int encodeCardToVector(Card card, float[] vector, int startIndex) {
        if (card != null) {
            // L'ordre des couleurs et des rangs doit être cohérent.
            // Ex: (Suit0*Rank_Max + Rank0), (Suit0*Rank_Max + Rank1), ..., (SuitN*Rank_Max + RankM)
            int suitIndex = card.getSuit().ordinal(); // 0-3
            int rankIndex = card.getRank().ordinal(); // 0-12
            vector[startIndex + (suitIndex * RANK_COUNT) + rankIndex] = 1.0f;
        }
        // Si card est null, les valeurs restent 0.0f par défaut.
        return startIndex + CARD_VECTOR_SIZE;
    }

    private float normalizeStack(double stack, double initialStack) {
        if (initialStack <= 0) return stack > 0 ? 1.0f : 0.0f; // Éviter division par zéro
        return (float) Math.min(3.0, stack / initialStack); // Cap à 3x initial stack, peut être négatif si stack < 0 (ne devrait pas arriver)
    }

    private float normalizePot(double potSize, double totalInitialPlayerStack) {
        if (totalInitialPlayerStack <= 0) return potSize > 0 ? 1.0f : 0.0f;
         // Un pot peut devenir très grand. Cap à X fois le stack initial total.
        return (float) Math.min(5.0, potSize / totalInitialPlayerStack);
    }

    // ----- Fonctions d'aide pour décoder (pour le débuggage ou l'affichage) -----

    public static Map<String, Float> decodeVectorToMap(INDArray vectorArray, int maxPlayersInGame) {
        // Inverse de vectorizeState, principalement pour le débogage.
        // Nécessite de connaître la structure exacte.
        // C'est complexe et hors de portée pour cette implémentation initiale.
        // On pourrait extraire des morceaux spécifiques si nécessaire.
        Map<String, Float> decoded = new HashMap<>();
        // Exemple:
        // decoded.put("HoleCard1_Is_Present", vectorArray.getFloat(0, some_index_for_first_card));
        // decoded.put("PlayerStack_Normalized", vectorArray.getFloat(0, index_for_player_stack));
        // ...
        return decoded; // À implémenter si besoin de débogage détaillé.
    }
}
