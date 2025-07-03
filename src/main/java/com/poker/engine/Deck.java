package com.poker.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Représente un paquet standard de 52 cartes de poker.
 * Fournit des fonctionnalités pour mélanger le paquet et distribuer des cartes.
 */
public class Deck {

    private final List<Card> cards;
    private int currentCardIndex;
    private final Random random;

    /**
     * Construit un nouveau paquet de 52 cartes, initialement trié.
     * Appelle {@link #shuffle()} pour le mélanger.
     */
    public Deck() {
        this(new Random());
    }

    /**
     * Construit un nouveau paquet de 52 cartes avec un générateur de nombres aléatoires spécifié.
     * Ceci est utile pour les tests afin d'avoir des mélanges reproductibles.
     * Le paquet est initialement trié, puis mélangé.
     *
     * @param random Le générateur de nombres aléatoires à utiliser pour le mélange.
     */
    public Deck(Random random) {
        this.random = random;
        this.cards = new ArrayList<>(52);
        initializeDeck();
        shuffle();
    }

    /**
     * Initialise le paquet avec les 52 cartes standard.
     */
    private void initializeDeck() {
        cards.clear();
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        currentCardIndex = 0;
    }

    /**
     * Mélange les cartes dans le paquet en utilisant l'algorithme de Fisher-Yates
     * avec le générateur de nombres aléatoires fourni.
     * Réinitialise également l'index de la carte actuelle à 0.
     */
    public void shuffle() {
        Collections.shuffle(this.cards, this.random);
        currentCardIndex = 0;
    }

    /**
     * Distribue une carte du dessus du paquet.
     *
     * @return La carte distribuée.
     * @throws IllegalStateException si le paquet est vide (toutes les cartes ont été distribuées).
     */
    public CarddealCard() {
        if (currentCardIndex >= cards.size()) {
            throw new IllegalStateException("Cannot deal card from an empty deck.");
        }
        return cards.get(currentCardIndex++);
    }

    /**
     * Distribue un nombre spécifié de cartes.
     *
     * @param numberOfCards Le nombre de cartes à distribuer.
     * @return Une liste de cartes distribuées.
     * @throws IllegalArgumentException si numberOfCards est négatif.
     * @throws IllegalStateException si le paquet n'a pas assez de cartes restantes.
     */
    public List<Card> dealCards(int numberOfCards) {
        if (numberOfCards < 0) {
            throw new IllegalArgumentException("Number of cards to deal cannot be negative.");
        }
        if (currentCardIndex + numberOfCards > cards.size()) {
            throw new IllegalStateException("Not enough cards in the deck to deal " + numberOfCards + " cards.");
        }
        List<Card> dealtCards = new ArrayList<>(numberOfCards);
        for (int i = 0; i < numberOfCards; i++) {
            dealtCards.add(dealCard());
        }
        return dealtCards;
    }

    /**
     * @return Le nombre de cartes restantes dans le paquet.
     */
    public int cardsRemaining() {
        return cards.size() - currentCardIndex;
    }

    /**
     * Réinitialise le paquet à son état initial (52 cartes triées) puis le mélange.
     * Utile pour commencer une nouvelle partie ou un nouveau test.
     */
    public void reset() {
        initializeDeck();
        shuffle();
    }

    /**
     * Renvoie une vue non modifiable des cartes du paquet.
     * Principalement à des fins de test ou de débogage.
     * @return Une liste non modifiable des cartes.
     */
    List<Card> getCardsView() {
        return Collections.unmodifiableList(cards);
    }
}
