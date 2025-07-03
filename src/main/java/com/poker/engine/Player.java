package com.poker.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Représente un joueur de poker.
 * Gère l'état du joueur, y compris son identifiant, son stack (tapis),
 * ses cartes en main, sa position à la table, et son statut actuel dans la main.
 */
public class Player {

    /**
     * Statut du joueur pendant une main de poker.
     */
    public enum PlayerStatus {
        ACTIVE,  // Le joueur est toujours actif dans la main et peut agir
        FOLDED,  // Le joueur s'est couché
        ALL_IN,  // Le joueur a misé tout son tapis
        OUT      // Le joueur est éliminé de la partie (pas seulement de la main)
    }

    private final String id; // Identifiant unique du joueur
    private String name;
    private double stack;
    private List<Card> holeCards;
    private int position; // Position à la table (0 = Small Blind, 1 = Big Blind, etc.)
    private PlayerStatus status;
    private double amountBetThisRound; // Montant misé par le joueur dans le tour de mise actuel
    private double amountBetThisHand;  // Montant total misé par le joueur dans la main actuelle

    /**
     * Construit un nouveau joueur avec un ID unique, un nom et un stack initial.
     *
     * @param name Le nom du joueur.
     * @param initialStack Le tapis de départ du joueur.
     * @throws IllegalArgumentException si initialStack est négatif.
     */
    public Player(String name, double initialStack) {
        this(UUID.randomUUID().toString(), name, initialStack);
    }

    /**
     * Construit un nouveau joueur avec un ID spécifié, un nom et un stack initial.
     *
     * @param id L'identifiant unique du joueur.
     * @param name Le nom du joueur.
     * @param initialStack Le tapis de départ du joueur.
     * @throws IllegalArgumentException si initialStack est négatif.
     */
    public Player(String id, String name, double initialStack) {
        Objects.requireNonNull(id, "Player ID cannot be null");
        Objects.requireNonNull(name, "Player name cannot be null");
        if (initialStack < 0) {
            throw new IllegalArgumentException("Initial stack cannot be negative.");
        }
        this.id = id;
        this.name = name;
        this.stack = initialStack;
        this.holeCards = new ArrayList<>();
        this.status = PlayerStatus.ACTIVE; // Par défaut, un joueur commence comme actif
        this.position = -1; // Position non définie initialement
        this.amountBetThisRound = 0;
        this.amountBetThisHand = 0;
    }

    /**
     * @return L'identifiant unique du joueur.
     */
    public String getId() {
        return id;
    }

    /**
     * @return Le nom du joueur.
     */
    public String getName() {
        return name;
    }

    /**
     * Définit le nom du joueur.
     * @param name Le nouveau nom.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return Le tapis actuel du joueur.
     */
    public double getStack() {
        return stack;
    }

    /**
     * Modifie le tapis du joueur.
     * @param stack Le nouveau tapis.
     */
    public void setStack(double stack) {
        this.stack = stack;
    }

    /**
     * Ajoute un montant au tapis du joueur (par exemple, en gagnant un pot).
     * @param amount Le montant à ajouter.
     */
    public void addToStack(double amount) {
        if (amount < 0) {
            //throw new IllegalArgumentException("Cannot add a negative amount to stack.");
            // Permettre un montant négatif pour la soustraction peut être utile,
            // mais il est préférable d'avoir une méthode dédiée comme removeFromStack.
            // Pour l'instant, on s'en tient à l'ajout positif.
        }
        this.stack += amount;
    }

    /**
     * Retire un montant du tapis du joueur (par exemple, en misant).
     * Assure que le tapis ne devient pas négatif.
     * @param amount Le montant à retirer.
     * @return Le montant effectivement retiré (peut être inférieur à `amount` si le joueur est all-in).
     */
    public double removeFromStack(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot remove a negative amount from stack.");
        }
        double actualAmount = Math.min(amount, this.stack);
        this.stack -= actualAmount;
        return actualAmount;
    }

    /**
     * @return La liste des cartes en main du joueur (immuable).
     */
    public List<Card> getHoleCards() {
        return List.copyOf(holeCards); // Retourne une copie pour l'immutabilité externe
    }

    /**
     * Définit les cartes en main du joueur.
     * @param cards Les deux cartes distribuées au joueur.
     * @throws IllegalArgumentException si la liste ne contient pas exactement 2 cartes.
     */
    public void setHoleCards(List<Card> cards) {
        if (cards != null && cards.size() != 2 && !cards.isEmpty()) { // Permet de vider les cartes entre les mains
             // Pourrait être plus strict et ne permettre que 2 cartes ou null/vide.
        }
        this.holeCards.clear();
        if (cards != null) {
            this.holeCards.addAll(cards);
        }
    }

    /**
     * Vide les cartes en main du joueur.
     */
    public void clearHoleCards() {
        this.holeCards.clear();
    }


    /**
     * @return La position du joueur à la table.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Définit la position du joueur à la table.
     * @param position La nouvelle position.
     */
    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * @return Le statut actuel du joueur dans la main.
     */
    public PlayerStatus getStatus() {
        return status;
    }

    /**
     * Définit le statut du joueur.
     * @param status Le nouveau statut.
     */
    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    /**
     * @return Le montant misé par le joueur dans le tour de mise actuel.
     */
    public double getAmountBetThisRound() {
        return amountBetThisRound;
    }

    /**
     * Ajoute un montant aux mises du joueur pour le tour actuel.
     * @param amount Le montant à ajouter.
     */
    public void addToBetThisRound(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot bet a negative amount.");
        }
        this.amountBetThisRound += amount;
        this.amountBetThisHand += amount;
    }

    /**
     * Réinitialise le montant misé par le joueur pour un nouveau tour de mise.
     */
    public void resetAmountBetThisRound() {
        this.amountBetThisRound = 0;
    }

    /**
     * @return Le montant total misé par le joueur dans la main actuelle.
     */
    public double getAmountBetThisHand() {
        return amountBetThisHand;
    }

    /**
     * Réinitialise le montant total misé par le joueur pour une nouvelle main.
     */
    public void resetAmountBetThisHand() {
        this.amountBetThisHand = 0;
        this.amountBetThisRound = 0; // Aussi réinitialiser le montant du tour
    }


    /**
     * Indique si le joueur peut encore effectuer des actions dans la main.
     * @return true si le joueur est ACTIF, false sinon.
     */
    public boolean isActive() {
        return this.status == PlayerStatus.ACTIVE;
    }

    /**
     * Indique si le joueur est à tapis.
     * @return true si le joueur est ALL_IN ou si son stack est à zéro et qu'il n'est pas FOLDED.
     */
    public boolean isAllIn() {
        return this.status == PlayerStatus.ALL_IN || (this.stack == 0 && this.status != PlayerStatus.FOLDED && this.status != PlayerStatus.OUT) ;
    }


    @Override
    public String toString() {
        return "Player{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", stack=" + String.format("%.2f", stack) +
                ", holeCards=" + holeCards +
                ", position=" + position +
                ", status=" + status +
                ", amountBetThisRound=" + String.format("%.2f", amountBetThisRound) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id.equals(player.id); // L'ID est suffisant pour l'égalité
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Prépare le joueur pour une nouvelle main.
     * Réinitialise les cartes, le statut (sauf si OUT), et les montants misés.
     */
    public void prepareForNewHand() {
        clearHoleCards();
        if (this.status != PlayerStatus.OUT) {
            this.status = PlayerStatus.ACTIVE;
        }
        resetAmountBetThisHand();
        // Le stack et la position ne sont pas réinitialisés ici, ils persistent entre les mains
        // ou sont gérés par le GameEngine (par exemple, rotation du bouton).
    }
}
