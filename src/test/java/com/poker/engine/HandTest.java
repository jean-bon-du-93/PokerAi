package com.poker.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class HandTest {

    // Helper method to create a card from string like "AS" (Ace of Spades), "KH" (King of Hearts)
    private Card c(String cardStr) {
        if (cardStr == null || cardStr.length() != 2) {
            throw new IllegalArgumentException("Card string must be 2 characters long (e.g., 'AS', 'KH'). Received: " + cardStr);
        }
        Card.Rank rank;
        Card.Suit suit;
        char rankChar = cardStr.charAt(0);
        char suitChar = cardStr.charAt(1);

        switch (rankChar) {
            case 'A': rank = Card.Rank.ACE; break;
            case 'K': rank = Card.Rank.KING; break;
            case 'Q': rank = Card.Rank.QUEEN; break;
            case 'J': rank = Card.Rank.JACK; break;
            case 'T': rank = Card.Rank.TEN; break;
            case '9': rank = Card.Rank.NINE; break;
            case '8': rank = Card.Rank.EIGHT; break;
            case '7': rank = Card.Rank.SEVEN; break;
            case '6': rank = Card.Rank.SIX; break;
            case '5': rank = Card.Rank.FIVE; break;
            case '4': rank = Card.Rank.FOUR; break;
            case '3': rank = Card.Rank.THREE; break;
            case '2': rank = Card.Rank.TWO; break;
            default: throw new IllegalArgumentException("Invalid rank character: " + rankChar);
        }

        switch (suitChar) {
            case 'S': suit = Card.Suit.SPADE; break;
            case 'H': suit = Card.Suit.HEART; break;
            case 'D': suit = Card.Suit.DIAMOND; break;
            case 'C': suit = Card.Suit.CLUB; break;
            default: throw new IllegalArgumentException("Invalid suit character: " + suitChar);
        }
        return new Card(suit, rank);
    }

    private List<Card> cards(String... cardStrs) {
        List<Card> cardList = new ArrayList<>();
        for (String s : cardStrs) {
            cardList.add(c(s));
        }
        return cardList;
    }

    @Test
    void constructor_requiresExactlyFiveCards() {
        assertThrows(IllegalArgumentException.class, () -> new Hand(cards("AS", "KS", "QS", "JS"))); // 4 cards
        assertThrows(IllegalArgumentException.class, () -> new Hand(cards("AS", "KS", "QS", "JS", "TS", "9S"))); // 6 cards
        assertDoesNotThrow(() -> new Hand(cards("AS", "KS", "QS", "JS", "TS"))); // 5 cards
    }

    @Test
    void evaluateRoyalFlush() {
        Hand hand = new Hand(cards("AS", "KS", "QS", "JS", "TS"));
        assertEquals(Hand.HandType.ROYAL_FLUSH, hand.getHandType());
        assertTrue(hand.getKickerRanks().isEmpty(), "Royal Flush should have no kickers.");

        Hand handHearts = new Hand(cards("AH", "KH", "QH", "JH", "TH"));
        assertEquals(Hand.HandType.ROYAL_FLUSH, handHearts.getHandType());
    }

    @Test
    void evaluateStraightFlush() {
        Hand hand = new Hand(cards("9S", "KS", "QS", "JS", "TS")); // K-high straight flush
        assertEquals(Hand.HandType.STRAIGHT_FLUSH, hand.getHandType());
        assertEquals(List.of(Card.Rank.KING), hand.getKickerRanks(), "Kicker for K-high SF should be King.");

        Hand handSteelWheel = new Hand(cards("AS", "2S", "3S", "4S", "5S")); // 5-high straight flush (Steel Wheel)
        assertEquals(Hand.HandType.STRAIGHT_FLUSH, handSteelWheel.getHandType());
        assertEquals(List.of(Card.Rank.FIVE), handSteelWheel.getKickerRanks(), "Kicker for 5-high SF (A-5) should be Five.");
    }

    @Test
    void evaluateFourOfAKind() {
        Hand hand = new Hand(cards("AS", "AD", "AC", "AH", "KS")); // Four Aces, King kicker
        assertEquals(Hand.HandType.FOUR_OF_A_KIND, hand.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("2S", "AS", "AD", "AC", "AH")); // Kicker is lower
        assertEquals(Hand.HandType.FOUR_OF_A_KIND, hand2.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.TWO), hand2.getKickerRanks());
    }

    @Test
    void evaluateFullHouse() {
        Hand hand = new Hand(cards("AS", "AD", "AC", "KH", "KS")); // Aces full of Kings
        assertEquals(Hand.HandType.FULL_HOUSE, hand.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("KS", "KD", "AS", "AD", "AC")); // Kings full of Aces (order shouldn't matter for eval)
        assertEquals(Hand.HandType.FULL_HOUSE, hand2.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING), hand2.getKickerRanks());
    }

    @Test
    void evaluateFlush() {
        Hand hand = new Hand(cards("AS", "KS", "QS", "JS", "9S")); // Ace-high flush
        assertEquals(Hand.HandType.FLUSH, hand.getHandType());
        assertEquals(cards("AS", "KS", "QS", "JS", "9S").stream().map(Card::getRank).sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("2H", "4H", "6H", "8H", "TH")); // Ten-high flush
        assertEquals(Hand.HandType.FLUSH, hand2.getHandType());
        assertEquals(cards("TH", "8H", "6H", "4H", "2H").stream().map(Card::getRank).sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()), hand2.getKickerRanks());
    }

    @Test
    void evaluateStraight() {
        Hand handBroadway = new Hand(cards("AS", "KD", "QC", "JH", "TS")); // Ace-high straight (Broadway)
        assertEquals(Hand.HandType.STRAIGHT, handBroadway.getHandType());
        assertEquals(List.of(Card.Rank.ACE), handBroadway.getKickerRanks());

        Hand handWheel = new Hand(cards("AS", "2D", "3C", "4H", "5S")); // Five-high straight (Wheel)
        assertEquals(Hand.HandType.STRAIGHT, handWheel.getHandType());
        assertEquals(List.of(Card.Rank.FIVE), handWheel.getKickerRanks()); // Effective high card is 5 for wheel

        Hand handMiddle = new Hand(cards("9S", "TD", "JC", "QH", "KS")); // King-high straight
        assertEquals(Hand.HandType.STRAIGHT, handMiddle.getHandType());
        assertEquals(List.of(Card.Rank.KING), handMiddle.getKickerRanks());
    }

    @Test
    void evaluateThreeOfAKind() {
        Hand hand = new Hand(cards("AS", "AD", "AC", "KH", "QS")); // Three Aces, K, Q kickers
        assertEquals(Hand.HandType.THREE_OF_A_KIND, hand.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("KS", "2H", "2D", "2C", "AS")); // Three Twos, A, K kickers
        assertEquals(Hand.HandType.THREE_OF_A_KIND, hand2.getHandType());
        assertEquals(List.of(Card.Rank.TWO, Card.Rank.ACE, Card.Rank.KING), hand2.getKickerRanks());
    }

    @Test
    void evaluateTwoPair() {
        Hand hand = new Hand(cards("AS", "AD", "KC", "KH", "QS")); // Aces and Kings, Q kicker
        assertEquals(Hand.HandType.TWO_PAIR, hand.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("AS", "2D", "2C", "3H", "3S")); // Threes and Twos, A kicker
        assertEquals(Hand.HandType.TWO_PAIR, hand2.getHandType());
        assertEquals(List.of(Card.Rank.THREE, Card.Rank.TWO, Card.Rank.ACE), hand2.getKickerRanks());
    }

    @Test
    void evaluateOnePair() {
        Hand hand = new Hand(cards("AS", "AD", "KC", "QH", "JS")); // Pair of Aces, K, Q, J kickers
        assertEquals(Hand.HandType.ONE_PAIR, hand.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN, Card.Rank.JACK), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("2S", "2D", "3C", "4H", "5S")); // Pair of Twos, 5, 4, 3 kickers
        assertEquals(Hand.HandType.ONE_PAIR, hand2.getHandType());
        assertEquals(List.of(Card.Rank.TWO, Card.Rank.FIVE, Card.Rank.FOUR, Card.Rank.THREE), hand2.getKickerRanks());
    }

    @Test
    void evaluateHighCard() {
        Hand hand = new Hand(cards("AS", "KD", "QC", "JH", "9S")); // Ace high, K, Q, J, 9
        assertEquals(Hand.HandType.HIGH_CARD, hand.getHandType());
        assertEquals(cards("AS", "KD", "QC", "JH", "9S").stream().map(Card::getRank).sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()), hand.getKickerRanks());

        Hand hand2 = new Hand(cards("2S", "4D", "6C", "8H", "TS")); // Ten high
        assertEquals(Hand.HandType.HIGH_CARD, hand2.getHandType());
        assertEquals(cards("TS", "8H", "6C", "4D", "2S").stream().map(Card::getRank).sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()), hand2.getKickerRanks());
    }

    // Comparison Tests
    @Test
    void compareRoyalFlush_isHighest() {
        Hand royalFlush = new Hand(cards("AS", "KS", "QS", "JS", "TS"));
        Hand straightFlush = new Hand(cards("KS", "QS", "JS", "TS", "9S"));
        assertTrue(royalFlush.compareTo(straightFlush) > 0);
    }

    @Test
    void compareStraightFlushes_higherCardWins() {
        Hand kingHighSF = new Hand(cards("KS", "QS", "JS", "TS", "9S"));
        Hand queenHighSF = new Hand(cards("QS", "JS", "TS", "9S", "8S"));
        assertTrue(kingHighSF.compareTo(queenHighSF) > 0);

        Hand steelWheel = new Hand(cards("AS", "2S", "3S", "4S", "5S")); // 5-high
        Hand sixHighSF = new Hand(cards("2D", "3D", "4D", "5D", "6D"));  // 6-high
        assertTrue(sixHighSF.compareTo(steelWheel) > 0);
    }

    @Test
    void compareFourOfAKind_rankThenKicker() {
        Hand fourAcesK = new Hand(cards("AS", "AD", "AC", "AH", "KS"));
        Hand fourKingsA = new Hand(cards("KS", "KD", "KC", "KH", "AS"));
        assertTrue(fourAcesK.compareTo(fourKingsA) > 0);

        Hand fourAcesQ = new Hand(cards("AS", "AD", "AC", "AH", "QS"));
        assertTrue(fourAcesK.compareTo(fourAcesQ) > 0); // Kicker wins
    }

    @Test
    void compareFullHouses_threeOfAKindRankThenPairRank() {
        Hand acesOverKings = new Hand(cards("AS", "AD", "AC", "KH", "KS"));
        Hand kingsOverAces = new Hand(cards("KS", "KD", "KC", "AH", "AS")); // This is actually Aces over Kings
        assertTrue(acesOverKings.compareTo(new Hand(cards("KS", "KD", "KC", "QH", "QS"))) > 0); // Aces full vs Kings full

        Hand acesOverQueens = new Hand(cards("AS", "AD", "AC", "QH", "QS"));
        assertTrue(acesOverKings.compareTo(acesOverQueens) > 0); // Same three of a kind, higher pair wins
    }

    @Test
    void compareFlushes_highCardsInOrder() {
        Hand aceHighFlush =   new Hand(cards("AS", "KS", "QS", "JS", "9S"));
        Hand kingHighFlush =  new Hand(cards("KS", "QS", "JS", "TS", "8S"));
        assertTrue(aceHighFlush.compareTo(kingHighFlush) > 0);

        Hand flush1 = new Hand(cards("AS", "KS", "QS", "JS", "8S")); // A K Q J 8
        Hand flush2 = new Hand(cards("AS", "KS", "QS", "JS", "7S")); // A K Q J 7
        assertTrue(flush1.compareTo(flush2) > 0); // Last kicker
    }

    @Test
    void compareStraights_higherCardWins() {
        Hand broadway = new Hand(cards("AS", "KD", "QC", "JH", "TS")); // A-high
        Hand kingHigh = new Hand(cards("KS", "QD", "JC", "TH", "9S")); // K-high
        assertTrue(broadway.compareTo(kingHigh) > 0);

        Hand wheel = new Hand(cards("AS", "2D", "3C", "4H", "5S"));    // 5-high
        Hand sixHigh = new Hand(cards("2S", "3D", "4C", "5H", "6S"));  // 6-high
        assertTrue(sixHigh.compareTo(wheel) > 0);
        assertEquals(0, broadway.compareTo(new Hand(cards("AC", "KH", "QS", "JD", "TC"))), "Straights of same rank are equal");
    }

    @Test
    void compareThreeOfAKind_rankThenKickers() {
        Hand threeAcesKQ = new Hand(cards("AS", "AD", "AC", "KH", "QS"));
        Hand threeKingsAQ = new Hand(cards("KS", "KD", "KC", "AH", "QS"));
        assertTrue(threeAcesKQ.compareTo(threeKingsAQ) > 0);

        Hand threeAcesKJ = new Hand(cards("AS", "AD", "AC", "KH", "JS"));
        assertTrue(threeAcesKQ.compareTo(threeAcesKJ) > 0); // Higher first kicker

        Hand threeAcesK9 = new Hand(cards("AS", "AD", "AC", "KH", "9S"));
        assertTrue(threeAcesKJ.compareTo(threeAcesK9) > 0); // Higher second kicker
    }

    @Test
    void compareTwoPairs_highPairThenLowPairThenKicker() {
        Hand acesKingsQ = new Hand(cards("AS", "AD", "KC", "KH", "QS")); // A,A,K,K,Q
        Hand acesQueensK = new Hand(cards("AS", "AD", "QC", "QH", "KS"));// A,A,Q,Q,K
        assertTrue(acesKingsQ.compareTo(acesQueensK) > 0); // Higher second pair

        Hand kingsQueensA = new Hand(cards("KS", "KD", "QC", "QH", "AS")); // K,K,Q,Q,A
        assertTrue(acesQueensK.compareTo(kingsQueensA) > 0); // Higher first pair

        Hand acesKingsJ = new Hand(cards("AS", "AD", "KC", "KH", "JS")); // A,A,K,K,J
        assertTrue(acesKingsQ.compareTo(acesKingsJ) > 0); // Kicker
    }

    @Test
    void compareOnePairs_pairRankThenKickersInOrder() {
        Hand acesKQJ = new Hand(cards("AS", "AD", "KC", "QH", "JS")); // A,A - K,Q,J
        Hand kingsAQJ = new Hand(cards("KS", "KD", "AC", "QH", "JS"));// K,K - A,Q,J
        assertTrue(acesKQJ.compareTo(kingsAQJ) > 0); // Higher pair

        Hand acesKQ10 = new Hand(cards("AS", "AD", "KC", "QH", "TS")); // A,A - K,Q,T
        assertTrue(acesKQJ.compareTo(acesKQ10) > 0); // First kicker

        Hand acesK9J = new Hand(cards("AS", "AD", "KC", "9H", "JS")); // A,A - K,J,9
        assertTrue(acesKQJ.compareTo(acesK9J) > 0); // Second kicker (Q vs 9, J is third for acesK9J)

        Hand acesK_Q_8 = new Hand(cards("AS", "AD", "KC", "QH", "8S"));
        Hand acesK_J_9 = new Hand(cards("AS", "AD", "KC", "JS", "9H"));
        assertTrue(acesK_Q_8.compareTo(acesK_J_9) > 0); // Second kicker matters (Q vs J)
    }

    @Test
    void compareHighCards_allCardsInOrder() {
        Hand aceKQJ9 = new Hand(cards("AS", "KD", "QC", "JH", "9S"));
        Hand aceKQJ8 = new Hand(cards("AS", "KD", "QC", "JH", "8S"));
        assertTrue(aceKQJ9.compareTo(aceKQJ8) > 0); // Last kicker

        Hand kingHigh = new Hand(cards("KS", "QD", "JC", "TH", "8S"));
        assertTrue(aceKQJ8.compareTo(kingHigh) > 0); // Highest card
    }

    @Test
    void evaluateBestHand_fromSevenCards_RoyalFlush() {
        Hand best = Hand.evaluateBestHand(
            cards("AS", "TS"), // Player cards
            cards("KS", "QS", "JS", "2D", "3H") // Community cards
        );
        assertEquals(Hand.HandType.ROYAL_FLUSH, best.getHandType());
        assertEquals(cards("AS", "KS", "QS", "JS", "TS"), best.getCards());
    }

    @Test
    void evaluateBestHand_fromSevenCards_StraightFlushWheel() {
        Hand best = Hand.evaluateBestHand(
            cards("AS", "2S"), // Player cards
            cards("3S", "4S", "5S", "JD", "QH") // Community cards
        );
        assertEquals(Hand.HandType.STRAIGHT_FLUSH, best.getHandType());
         // Expected cards sorted: AS, 5S, 4S, 3S, 2S
        List<Card> expectedCards = cards("AS", "5S", "4S", "3S", "2S");
        expectedCards.sort(Comparator.comparing(Card::getRank).reversed());
        assertEquals(expectedCards, best.getCards());
        assertEquals(List.of(Card.Rank.FIVE), best.getKickerRanks());
    }

    @Test
    void evaluateBestHand_fromSevenCards_FourOfAKind() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "AS"), // Player cards
            cards("AD", "AC", "KS", "QH", "JD") // Community cards
        );
        assertEquals(Hand.HandType.FOUR_OF_A_KIND, best.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING), best.getKickerRanks());
        // Expected cards: AH, AS, AD, AC, KS (order may vary based on suit, but ranks are key)
        List<Card> best5 = best.getCards();
        assertTrue(best5.containsAll(cards("AH", "AS", "AD", "AC", "KS")));
    }

    @Test
    void evaluateBestHand_fromSevenCards_FullHouse() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "AS"), // Player cards
            cards("AD", "KC", "KS", "QH", "JD") // Community cards
        );
        assertEquals(Hand.HandType.FULL_HOUSE, best.getHandType()); // Aces full of Kings
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING), best.getKickerRanks());
    }

    @Test
    void evaluateBestHand_fromSevenCards_FlushUsingOnePlayerCard() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "2D"), // Player cards (AH makes the flush)
            cards("KH", "QH", "JH", "7H", "5S") // Community cards
        );
        assertEquals(Hand.HandType.FLUSH, best.getHandType());
        // Expected flush: AH, KH, QH, JH, 7H
        List<Card> expectedFlushCards = cards("AH", "KH", "QH", "JH", "7H");
        expectedFlushCards.sort(Comparator.comparing(Card::getRank).reversed());
        assertEquals(expectedFlushCards, best.getCards());
    }

    @Test
    void evaluateBestHand_fromSevenCards_FlushOnBoardPlays() {
         // Player has lower flush, board flush plays
        Hand best = Hand.evaluateBestHand(
            cards("2S", "3S"), // Player cards (low spade flush)
            cards("AS", "KS", "QS", "JS", "9S") // Community cards (Royal Flush on board)
        );
        // This should be a Royal Flush, not the player's lower flush.
        // The method evaluateBestHand should find the absolute best 5-card hand.
        assertEquals(Hand.HandType.ROYAL_FLUSH, best.getHandType());
        assertEquals(cards("AS", "KS", "QS", "JS", "9S"), best.getCards());
    }

    @Test
    void evaluateBestHand_fromSevenCards_Straight() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "2S"), // Player cards
            cards("KD", "QC", "JS", "TH", "3D") // Community cards (Broadway on board with player Ace)
        );
        assertEquals(Hand.HandType.STRAIGHT, best.getHandType());
        assertEquals(List.of(Card.Rank.ACE), best.getKickerRanks());
        List<Card> expectedStraight = cards("AH", "KD", "QC", "JS", "TH");
        Collections.sort(expectedStraight, Comparator.comparing(Card::getRank).reversed());
        assertEquals(expectedStraight, best.getCards());
    }

    @Test
    void evaluateBestHand_fromSevenCards_PlayerPairMakesTwoPair() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "AS"), // Player cards (Pair of Aces)
            cards("KC", "KH", "QD", "JS", "2H") // Community cards (Pair of Kings on board)
        );
        assertEquals(Hand.HandType.TWO_PAIR, best.getHandType()); // Aces and Kings
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN), best.getKickerRanks());
    }

    @Test
    void evaluateBestHand_fromSevenCards_OnePairFromPlayer() {
        Hand best = Hand.evaluateBestHand(
            cards("AH", "2S"),    // Player cards
            cards("AD", "KC", "QH", "JS", "7C") // Community cards (Player makes pair of Aces)
        );
        assertEquals(Hand.HandType.ONE_PAIR, best.getHandType());
        assertEquals(List.of(Card.Rank.ACE, Card.Rank.KING, Card.Rank.QUEEN, Card.Rank.JACK), best.getKickerRanks());
    }

    @Test
    void evaluateBestHand_fromSevenCards_HighCardPlays() {
        Hand best = Hand.evaluateBestHand(
            cards("2D", "3C"),    // Player cards
            cards("AS", "KH", "QS", "JC", "7H") // Community cards (Ace high on board)
        );
        assertEquals(Hand.HandType.HIGH_CARD, best.getHandType());
        List<Card> expectedHighCardHand = cards("AS", "KH", "QS", "JC", "7H");
        expectedHighCardHand.sort(Comparator.comparing(Card::getRank).reversed());
        assertEquals(expectedHighCardHand, best.getCards());
    }

    @Test
    void evaluateBestHand_fewerThan5Cards_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> Hand.evaluateBestHand(
            cards("AS", "KS"),
            cards("QS", "JS")  // Total 4 cards
        ));
    }

    @Test
    void evaluateBestHand_exactly5Cards() {
         Hand best = Hand.evaluateBestHand(
            cards("AS", "KS"),
            cards("QS", "JS", "TS")  // Total 5 cards -> Royal Flush
        );
        assertEquals(Hand.HandType.ROYAL_FLUSH, best.getHandType());
    }
}
