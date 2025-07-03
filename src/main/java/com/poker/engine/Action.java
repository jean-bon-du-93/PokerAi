package com.poker.engine;

import java.util.Objects;

/**
 * Représente une action qu'un joueur peut effectuer pendant un tour de mise au poker.
 * Les actions possibles sont FOLD, CHECK, CALL, BET, RAISE.
 * Pour BET et RAISE, un montant est associé.
 */
public class Action {

    /**
     * Type d'action de poker.
     */
    public enum ActionType {
        FOLD,   // Se coucher
        CHECK,  // Parler (si aucune mise précédente)
        CALL,   // Suivre (égaliser la mise précédente)
        BET,    // Miser (si aucune mise précédente)
        RAISE   // Relancer (augmenter la mise précédente)
    }

    private final ActionType type;
    private final double amount; // Applicable pour BET et RAISE

    /**
     * Crée une action de type FOLD, CHECK, ou CALL.
     * Pour ces types, le montant est implicitement zéro.
     *
     * @param type Le type d'action (doit être FOLD, CHECK, ou CALL).
     * @throws IllegalArgumentException si le type est BET ou RAISE.
     */
    public Action(ActionType type) {
        if (type == ActionType.BET || type == ActionType.RAISE) {
            throw new IllegalArgumentException("Amount must be specified for BET or RAISE actions.");
        }
        this.type = type;
        this.amount = 0;
    }

    /**
     * Crée une action de type BET ou RAISE avec un montant spécifié.
     *
     * @param type Le type d'action (doit être BET ou RAISE).
     * @param amount Le montant de la mise ou de la relance. Doit être positif.
     * @throws IllegalArgumentException si le type n'est pas BET ou RAISE, ou si le montant n'est pas positif.
     */
    public Action(ActionType type, double amount) {
        if (type != ActionType.BET && type != ActionType.RAISE) {
            throw new IllegalArgumentException("Amount should not be specified for FOLD, CHECK, or CALL actions.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount for BET or RAISE must be positive.");
        }
        this.type = type;
        this.amount = amount;
    }

    /**
     * @return Le type de l'action.
     */
    public ActionType getType() {
        return type;
    }

    /**
     * @return Le montant associé à l'action (0 pour FOLD, CHECK, CALL).
     */
    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        switch (type) {
            case FOLD:
                return "FOLD";
            case CHECK:
                return "CHECK";
            case CALL:
                return "CALL";
            case BET:
                return "BET " + String.format("%.2f", amount);
            case RAISE:
                return "RAISE " + String.format("%.2f", amount);
            default:
                throw new IllegalStateException("Unknown action type: " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;
        return type == action.type && Double.compare(action.amount, amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount);
    }
}
