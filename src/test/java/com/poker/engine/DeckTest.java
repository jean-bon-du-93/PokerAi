package com.poker.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DeckTest {

    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck(new Random(12345)); // Seeded random for reproducible tests
    }

    @Test
    void newDeckHas52Cards() {
        assertEquals(52, deck.cardsRemaining(), "A new deck should have 52 cards.");
    }

    @Test
    void dealCardReducesRemainingCount() {
        deck.dealCard();
        assertEquals(51, deck.cardsRemaining(), "Dealing a card should reduce the count.");
    }

    @Test
    void dealAllCardsEmptiesDeck() {
        for (int i = 0; i < 52; i++) {
            assertNotNull(deck.dealCard(), "Should be able to deal card " + (i + 1));
        }
        assertEquals(0, deck.cardsRemaining(), "Deck should be empty after dealing 52 cards.");
        assertThrows(IllegalStateException.class, () -> deck.dealCard(),
                     "Should throw IllegalStateException when dealing from an empty deck.");
    }

    @Test
    void dealMultipleCards() {
        List<Card> dealt = deck.dealCards(5);
        assertEquals(5, dealt.size(), "Should deal the requested number of cards.");
        assertEquals(52 - 5, deck.cardsRemaining(), "Remaining cards should be correct after dealing multiple.");
    }

    @Test
    void dealTooManyCardsThrowsException() {
        assertThrows(IllegalStateException.class, () -> deck.dealCards(53),
                     "Should throw IllegalStateException when trying to deal more cards than available.");
    }

    @Test
    void dealNegativeCardsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> deck.dealCards(-1),
                     "Should throw IllegalArgumentException for negative number of cards.");
    }

    @Test
    void shuffleChangesCardOrder() {
        // This test is probabilistic but good enough for typical shuffle implementations.
        // For a deterministic shuffle (with a seed), we can check specific card positions.
        Deck deck1 = new Deck(new Random(1));
        List<Card> cards1Order = deck1.getCardsView(); // Get internal view for comparison

        Deck deck2 = new Deck(new Random(1)); // Same seed, should be same order initially
        // We need to shuffle deck2 again with a *different* seed or rely on the default shuffle
        // or compare deck1 before and after shuffle.

        List<Card> initialOrder = List.copyOf(new Deck(new Random(1)).getCardsView()); // Fresh deck with seed 1

        Deck deckToShuffle = new Deck(new Random(1)); // Deck with seed 1
        deckToShuffle.shuffle(); // Shuffle it (uses its internal Random, which was seeded)
        List<Card> shuffledOrder = deckToShuffle.getCardsView();

        // A robust test would be to check if the order is different from a non-shuffled deck.
        // However, even a shuffled deck *could* end up in the original order (highly unlikely).
        // A better test: check that two shuffles with the same seed produce the same order,
        // and two shuffles with different seeds produce different orders (likely).

        Deck d1 = new Deck(new Random(100)); // Seeded shuffle
        List<Card> order1 = List.copyOf(d1.getCardsView());

        Deck d2 = new Deck(new Random(100)); // Same seed
        List<Card> order2 = List.copyOf(d2.getCardsView());
        assertEquals(order1, order2, "Shuffling with the same seed should produce the same order.");

        Deck d3 = new Deck(new Random(101)); // Different seed
        List<Card> order3 = List.copyOf(d3.getCardsView());
        assertNotEquals(order1, order3, "Shuffling with different seeds should likely produce different orders.");

        // Also check that all 52 unique cards are present after shuffle
        assertEquals(52, shuffledOrder.size());
        Set<Card> uniqueCards = new HashSet<>(shuffledOrder);
        assertEquals(52, uniqueCards.size(), "Shuffled deck should still contain 52 unique cards.");

    }

    @Test
    void resetDeckRestoresTo52CardsAndShuffles() {
        deck.dealCards(10);
        assertEquals(42, deck.cardsRemaining());

        // To test shuffle on reset, we'd need to compare card orders,
        // similar to the shuffle test. For now, just check count.
        List<Card> cardsBeforeReset = List.copyOf(deck.getCardsView());
        int indexBeforeReset = deck.currentCardIndex;


        deck.reset();
        assertEquals(52, deck.cardsRemaining(), "Reset deck should have 52 cards.");

        List<Card> cardsAfterReset = List.copyOf(deck.getCardsView());
        int indexAfterReset = deck.currentCardIndex;

        assertEquals(0, indexAfterReset, "Current card index should be 0 after reset.");
        // It's hard to deterministically check if it "shuffled" without knowing the exact previous state
        // and comparing. But a new shuffle did occur.
        // If the seed is the same, the shuffle will be the same.
        // Deck constructor already calls shuffle. Reset calls initializeDeck then shuffle.
        // So, if the Random object is the same, the sequence of shuffles will be deterministic.

        Deck deckForResetTest = new Deck(new Random(77));
        List<Card> orderA = List.copyOf(deckForResetTest.getCardsView());
        deckForResetTest.dealCards(5);
        deckForResetTest.reset(); // Uses the same Random(77) instance for its shuffle
        List<Card> orderB = List.copyOf(deckForResetTest.getCardsView());

        // Because reset re-initializes and then shuffles with the *same* Random instance,
        // the sequence of random numbers produced by that Random instance continues.
        // So, orderB will be the result of the *second* shuffle sequence from Random(77),
        // while orderA was the *first*. They should be different.
        // If we wanted them to be the same, Deck would need to re-seed its Random or use a new one on reset.
        // The current implementation reuses the Random, so subsequent shuffles are part of the same sequence.
        assertNotEquals(orderA, orderB, "Order after reset (and re-shuffle) should generally be different if Random state changed or re-seeded differently.");
        // This depends on the Random instance behavior. If Random(seed) always produces the same sequence from the start,
        // and shuffle uses a fixed number of random numbers, then two shuffles on the same Random instance
        // will produce different results unless the Random instance itself is reset.
        // Our Deck reuses the Random instance.
    }

    @Test
    void allCardsAreUniqueInNewDeck() {
        Set<Card> uniqueCards = new HashSet<>();
        for (int i = 0; i < 52; i++) {
            uniqueCards.add(deck.dealCard());
        }
        assertEquals(52, uniqueCards.size(), "A new deck should contain 52 unique cards.");
    }
}
