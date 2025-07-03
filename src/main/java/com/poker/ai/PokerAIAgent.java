package com.poker.ai;

import com.poker.engine.GameState;
import com.poker.engine.Action;
import java.util.List;

/**
 * Interface simple définissant la méthode que toutes les IA de poker
 * devront implémenter pour choisir une action.
 */
public interface PokerAIAgent {

    /**
     * Choisit une action à effectuer en fonction de l'état actuel du jeu et des actions légales disponibles.
     *
     * @param gameState L'état actuel du jeu (immuable).
     * @param legalActions La liste des actions légales que l'agent peut choisir.
     * @return L'action choisie par l'agent.
     */
    Action chooseAction(GameState gameState, List<Action> legalActions);

    /**
     * Méthode optionnelle appelée à la fin d'une main pour permettre à l'agent
     * d'observer le résultat final et potentiellement d'apprendre.
     *
     * @param finalState L'état final de la main.
     * @param reward Le gain (positif ou négatif) du joueur pour cette main.
     */
    default void handComplete(GameState finalState, double reward) {
        // Implémentation par défaut ne fait rien.
        // Les agents apprenants (comme DQNAgent) surchargeront cette méthode.
    }

    /**
     * Méthode optionnelle pour donner un nom/identifiant à l'agent.
     * @return Le nom de l'agent.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Méthode appelée lorsqu'une nouvelle partie (série de mains) commence.
     * Peut être utilisée pour réinitialiser des états internes de l'agent si nécessaire.
     */
    default void gameStart() {
        // Implémentation par défaut ne fait rien.
    }
}
