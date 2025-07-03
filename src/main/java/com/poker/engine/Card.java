package com.poker.engine;

import java.util.Objects;

/**
 * Représente une carte de poker standard, avec une couleur (Suit) et un rang (Rank).
 * Cette classe est immuable.
 */
public class Card implements Comparable<Card> {

    /**
     * Représente les quatre couleurs possibles d'une carte.
     * HEART (Cœur), DIAMOND (Carreau), CLUB (Trèfle), SPADE (Pique).
     */
    public enum Suit {
        HEART("♥"), DIAMOND("♦"), CLUB("♣"), SPADE("♠");

        private final String symbol;

        Suit(String symbol) {
            this.symbol = symbol;
        }

        /**
         * @return Le symbole textuel de la couleur (ex: ♥).
         */
        public String getSymbol() {
            return symbol;
        }
    }

    /**
     * Représente les treize rangs possibles d'une carte.
     * De TWO (2) à ACE (As).
     * Les rangs ont une valeur associée pour faciliter la comparaison et l'évaluation des mains.
     */
    public enum Rank {
        TWO(2, "2"),
        THREE(3, "3"),
        FOUR(4, "4"),
        FIVE(5, "5"),
        SIX(6, "6"),
        SEVEN(7, "7"),
        EIGHT(8, "8"),
        NINE(9, "9"),
        TEN(10, "T"),
        JACK(11, "J"),
        QUEEN(12, "Q"),
        KING(13, "K"),
        ACE(14, "A"); // L'As peut aussi valoir 1 dans une quinte A-2-3-4-5

        private final int value;
        private final String symbol;

        Rank(int value, String symbol) {
            this.value = value;
            this.symbol = symbol;
        }

        /**
         * @return La valeur numérique du rang (TWO=2, ..., ACE=14).
         */
        public int getValue() {
            return value;
        }

        /**
         * @return Le symbole textuel du rang (ex: A, K, Q, J, T, 9...).
         */
        public String getSymbol() {
            return symbol;
        }
    }

    private final Suit suit;
    private final Rank rank;

    /**
     * Construit une nouvelle carte avec la couleur et le rang spécifiés.
     *
     * @param suit La couleur de la carte.
     * @param rank Le rang de la carte.
     * @throws NullPointerException si suit ou rank est null.
     */
    public Card(Suit suit, Rank rank) {
        Objects.requireNonNull(suit, "Suit cannot be null");
        Objects.requireNonNull(rank, "Rank cannot be null");
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * @return La couleur de la carte.
     */
    public Suit getSuit() {
        return suit;
    }

    /**
     * @return Le rang de la carte.
     */
    public Rank getRank() {
        return rank;
    }

    @Override
    public String toString() {
        return rank.getSymbol() + suit.getSymbol();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    /**
     * Compare cette carte avec une autre carte pour l'ordre, basé d'abord sur le rang, puis sur la couleur.
     * Principalement utile pour trier des listes de cartes, bien que la couleur ait rarement d'importance
     * dans l'évaluation des mains au poker (sauf pour les couleurs).
     *
     * @param other L'autre carte à comparer.
     * @return une valeur négative, zéro, ou une valeur positive si cette carte est
     * respectivement inférieure, égale ou supérieure à l'autre carte.
     */
    @Override
    public int compareTo(Card other) {
        int rankComparison = Integer.compare(this.rank.getValue(), other.rank.getValue());
        if (rankComparison != 0) {
            return rankComparison;
        }
        return this.suit.compareTo(other.suit); // Ordre arbitraire mais cohérent des couleurs si les rangs sont égaux
    }
}
