package com.poker.engine;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Représente une main de poker et contient la logique pour évaluer sa force.
 * Une main de poker est généralement composée de 5 cartes. Au Texas Hold'em,
 * un joueur forme la meilleure main possible à partir de ses 2 cartes privées
 * et des 5 cartes communes.
 */
public class Hand implements Comparable<Hand> {

    /**
     * Définit les différents types de mains de poker, classés du plus fort au plus faible.
     */
    public enum HandType {
        ROYAL_FLUSH(10, "Royal Flush"),          // As, Roi, Dame, Valet, Dix de la même couleur
        STRAIGHT_FLUSH(9, "Straight Flush"),     // Cinq cartes consécutives de la même couleur
        FOUR_OF_A_KIND(8, "Four of a Kind"),     // Quatre cartes du même rang
        FULL_HOUSE(7, "Full House"),             // Trois cartes d'un rang et deux cartes d'un autre rang
        FLUSH(6, "Flush"),                       // Cinq cartes de la même couleur, non consécutives
        STRAIGHT(5, "Straight"),                 // Cinq cartes consécutives de couleurs différentes
        THREE_OF_A_KIND(4, "Three of a Kind"),   // Trois cartes du même rang
        TWO_PAIR(3, "Two Pair"),                 // Deux paires de rangs différents
        ONE_PAIR(2, "One Pair"),                 // Deux cartes du même rang
        HIGH_CARD(1, "High Card");               // Aucune des combinaisons ci-dessus, la main est évaluée par la carte la plus haute

        private final int strength;
        private final String displayName;

        HandType(int strength, String displayName) {
            this.strength = strength;
            this.displayName = displayName;
        }

        public int getStrength() {
            return strength;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final List<Card> cards; // Les 5 cartes constituant la main finale
    private final HandType handType;
    private final List<Card.Rank> kickerRanks; // Rangs des kickers, importants pour départager

    /**
     * Évalue la meilleure main de 5 cartes possible à partir d'un ensemble de cartes donné.
     * Typiquement, au Texas Hold'em, cela implique 2 cartes privées et 5 cartes communes (total 7).
     *
     * @param playerCards Les deux cartes privées du joueur.
     * @param communityCards Les cartes communes sur le board (peut être 3, 4 ou 5).
     * @return L'objet Hand représentant la meilleure main de 5 cartes possible.
     * @throws IllegalArgumentException si le nombre total de cartes est inférieur à 5.
     */
    public static HandevaluateBestHand(List<Card> playerCards, List<Card> communityCards) {
        List<Card> allCards = new ArrayList<>(playerCards);
        allCards.addAll(communityCards);

        if (allCards.size() < 5) {
            // Pas assez de cartes pour former une main de 5 cartes, cela peut arriver pre-flop ou flop
            // Dans un contexte d'évaluation finale, cela ne devrait pas arriver après la river.
            // Pour l'instant, on retourne une main "High Card" avec les cartes disponibles si moins de 5.
            // Une meilleure gestion pourrait être nécessaire selon les règles exactes du jeu.
            // Cependant, l'évaluation se fait généralement sur 5 cartes.
            // Si on veut évaluer une main partielle (ex: juste les cartes du joueur), il faut une autre méthode.
             throw new IllegalArgumentException("Cannot evaluate a hand with fewer than 5 cards. Total cards: " + allCards.size());
        }

        List<Hand> possibleHands = new ArrayList<>();
        List<Card> currentCombination = new ArrayList<>(5);

        // Générer toutes les combinaisons de 5 cartes parmi les cartes disponibles
        generateCombinations(allCards, 0, 5, currentCombination, possibleHands);

        if (possibleHands.isEmpty()) {
             // Ce cas ne devrait jamais arriver si allCards.size() >= 5
            throw new IllegalStateException("No 5-card combinations could be generated from " + allCards.size() + " cards.");
        }

        // Trier les mains possibles pour trouver la meilleure
        // Collections.sort(possibleHands); // Comparable s'assure que la meilleure est à la fin
        // return possibleHands.get(possibleHands.size() - 1);
        return Collections.max(possibleHands); // Plus direct
    }


    /**
     * Fonction utilitaire récursive pour générer toutes les combinaisons de k cartes
     * à partir d'une liste de cartes.
     *
     * @param allCards Liste de toutes les cartes disponibles.
     * @param start Index de départ pour la sélection dans allCards.
     * @param k Nombre de cartes à choisir pour une combinaison (généralement 5).
     * @param currentCombination Combinaison en cours de construction.
     * @param possibleHands Liste pour stocker toutes les mains de 5 cartes générées.
     */
    private static void generateCombinations(List<Card> allCards, int start, int k,
                                             List<Card> currentCombination, List<Hand> possibleHands) {
        if (k == 0) { // Une combinaison de 5 cartes est formée
            possibleHands.add(new Hand(new ArrayList<>(currentCombination)));
            return;
        }

        for (int i = start; i <= allCards.size() - k; i++) {
            currentCombination.add(allCards.get(i));
            generateCombinations(allCards, i + 1, k - 1, currentCombination, possibleHands);
            currentCombination.remove(currentCombination.size() - 1); // Backtrack
        }
    }


    /**
     * Construit une main à partir d'une liste de 5 cartes.
     * L'évaluation de la main (type, kickers) est effectuée lors de la construction.
     *
     * @param fiveCards Une liste de précisément 5 cartes.
     * @throws IllegalArgumentException si la liste ne contient pas exactement 5 cartes.
     */
    public Hand(List<Card> fiveCards) {
        if (fiveCards == null || fiveCards.size() != 5) {
            throw new IllegalArgumentException("A hand must consist of exactly 5 cards. Received: " +
                                               (fiveCards == null ? "null" : fiveCards.size()));
        }
        // Trier les cartes par rang décroissant pour faciliter l'évaluation
        this.cards = fiveCards.stream()
                              .sorted(Comparator.comparing(Card::getRank).reversed())
                              .collect(Collectors.toList());

        // Évaluation de la main
        EvaluationResult eval = evaluateHandType(this.cards);
        this.handType = eval.handType;
        this.kickerRanks = eval.kickerRanks;
    }

    /**
     * @return Les 5 cartes qui composent cette main, triées par rang décroissant.
     */
    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    /**
     * @return Le type de cette main (ex: FULL_HOUSE, STRAIGHT, etc.).
     */
    public HandType getHandType() {
        return handType;
    }

    /**
     * @return La liste des rangs des kickers, triés par ordre d'importance (décroissant).
     *         Peut être vide si non applicable (ex: Royal Flush).
     */
    public List<Card.Rank> getKickerRanks() {
        return Collections.unmodifiableList(kickerRanks);
    }

    // Méthodes d'évaluation (privées ou package-private)
    private static class EvaluationResult {
        HandType handType;
        List<Card.Rank> kickerRanks;

        EvaluationResult(HandType handType, List<Card.Rank> kickerRanks) {
            this.handType = handType;
            this.kickerRanks = kickerRanks;
        }
    }

    private static EvaluationResult evaluateHandType(List<Card> fiveCardsSorted) {
        // Assurez-vous que les cartes sont triées par rang décroissant pour la logique ci-dessous
        List<Card> sortedCards = new ArrayList<>(fiveCardsSorted);
        sortedCards.sort(Comparator.comparing(Card::getRank).reversed());

        boolean isFlush = isFlush(sortedCards);
        boolean isStraight = isStraight(sortedCards);
        Map<Card.Rank, Long> rankCounts = countRanks(sortedCards);

        if (isFlush && isStraight) {
            if (sortedCards.get(0).getRank() == Card.Rank.ACE) { // Le premier carte d'une quinte flush triée est la plus haute
                return new EvaluationResult(HandType.ROYAL_FLUSH, Collections.emptyList());
            }
            // Kicker pour Straight Flush est la carte la plus haute de la quinte
            return new EvaluationResult(HandType.STRAIGHT_FLUSH, List.of(sortedCards.get(0).getRank()));
        }

        Optional<Card.Rank> fourOfAKindRank = findNOfAKindRank(rankCounts, 4);
        if (fourOfAKindRank.isPresent()) {
            List<Card.Rank> kickers = getKickers(sortedCards, List.of(fourOfAKindRank.get()), 1);
            return new EvaluationResult(HandType.FOUR_OF_A_KIND, kickers);
        }

        Optional<Card.Rank> threeOfAKindRankForFullHouse = findNOfAKindRank(rankCounts, 3);
        Optional<Card.Rank> pairRankForFullHouse = findNOfAKindRank(rankCounts, 2);

        if (threeOfAKindRankForFullHouse.isPresent() && pairRankForFullHouse.isPresent()) {
            // Les kickers sont le rang du brelan puis le rang de la paire
            return new EvaluationResult(HandType.FULL_HOUSE, List.of(threeOfAKindRankForFullHouse.get(), pairRankForFullHouse.get()));
        }

        // Cas spécial: deux brelans ne sont pas possibles avec 5 cartes, mais si on a 3 et 2,2 (ex: AAA KK QQ -> Full AAA KK)
        // La logique ci-dessus est correcte pour 5 cartes. Si on a un brelan et une autre paire, c'est un full.

        if (isFlush) {
            // Kickers sont toutes les cartes de la couleur, par ordre de rang
            List<Card.Rank> kickers = sortedCards.stream().map(Card::getRank).collect(Collectors.toList());
            return new EvaluationResult(HandType.FLUSH, kickers);
        }

        if (isStraight) {
            // Kicker pour Straight est la carte la plus haute de la quinte
            return new EvaluationResult(HandType.STRAIGHT, List.of(sortedCards.get(0).getRank()));
        }

        Optional<Card.Rank> threeOfAKindRank = findNOfAKindRank(rankCounts, 3);
        if (threeOfAKindRank.isPresent()) {
            List<Card.Rank> kickers = getKickers(sortedCards, List.of(threeOfAKindRank.get()), 2);
            return new EvaluationResult(HandType.THREE_OF_A_KIND, kickers);
        }

        List<Card.Rank> pairs = findPairRanks(rankCounts);
        if (pairs.size() == 2) { // Deux paires
            // Les kickers sont les rangs des deux paires (la plus haute d'abord), puis la carte restante
            List<Card.Rank> pairKickers = new ArrayList<>(pairs); // Déjà triées par findPairRanks
            List<Card.Rank> mainRanks = new ArrayList<>(pairKickers);
            List<Card.Rank> remainingKickers = getKickers(sortedCards, mainRanks, 1);
            pairKickers.addAll(remainingKickers);
            return new EvaluationResult(HandType.TWO_PAIR, pairKickers);
        }

        if (pairs.size() == 1) { // Une paire
            List<Card.Rank> kickers = new ArrayList<>();
            kickers.add(pairs.get(0)); // Le rang de la paire
            kickers.addAll(getKickers(sortedCards, List.of(pairs.get(0)), 3));
            return new EvaluationResult(HandType.ONE_PAIR, kickers);
        }

        // High Card
        List<Card.Rank> kickers = sortedCards.stream().map(Card::getRank).collect(Collectors.toList());
        return new EvaluationResult(HandType.HIGH_CARD, kickers);
    }

    private static Map<Card.Rank, Long> countRanks(List<Card> cards) {
        return cards.stream().collect(Collectors.groupingBy(Card::getRank, Collectors.counting()));
    }

    private static boolean isFlush(List<Card> cards) {
        if (cards.isEmpty()) return false;
        Card.Suit firstSuit = cards.get(0).getSuit();
        return cards.stream().allMatch(card -> card.getSuit() == firstSuit);
    }

    private static boolean isStraight(List<Card> sortedCards) { // sorted by rank descending
        if (sortedCards.size() != 5) return false; // Straight is always 5 cards

        boolean standardStraight = true;
        for (int i = 0; i < 4; i++) {
            if (sortedCards.get(i).getRank().getValue() - sortedCards.get(i + 1).getRank().getValue() != 1) {
                standardStraight = false;
                break;
            }
        }
        if (standardStraight) return true;

        // Check for A-2-3-4-5 straight (wheel)
        // Sorted: A, 5, 4, 3, 2
        boolean isWheel = sortedCards.get(0).getRank() == Card.Rank.ACE &&
                          sortedCards.get(1).getRank() == Card.Rank.FIVE &&
                          sortedCards.get(2).getRank() == Card.Rank.FOUR &&
                          sortedCards.get(3).getRank() == Card.Rank.THREE &&
                          sortedCards.get(4).getRank() == Card.Rank.TWO;
        return isWheel;
    }

    private static Optional<Card.Rank> findNOfAKindRank(Map<Card.Rank, Long> rankCounts, int n) {
        return rankCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == n)
                .map(Map.Entry::getKey)
                .max(Comparator.comparingInt(Card.Rank::getValue)); // Retourne le plus haut rang si plusieurs (ex: deux brelans dans 7 cartes)
    }

    private static List<Card.Rank> findPairRanks(Map<Card.Rank, Long> rankCounts) {
        return rankCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == 2)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(Card.Rank::getValue).reversed()) // Plus haute paire en premier
                .collect(Collectors.toList());
    }


    /**
     * Obtient les N meilleurs kickers parmi les cartes qui ne font pas partie des "main ranks".
     * @param allSortedCards Toutes les 5 cartes de la main, triées par rang décroissant.
     * @param mainRanks Les rangs des cartes qui constituent la combinaison principale (ex: rang de la paire, du brelan).
     * @param numberOfKickers Le nombre de kickers à retourner.
     * @return Une liste de rangs de kickers, triés par valeur décroissante.
     */
    private static List<Card.Rank> getKickers(List<Card> allSortedCards, List<Card.Rank> mainRanks, int numberOfKickers) {
        return allSortedCards.stream()
                .map(Card::getRank)
                .filter(rank -> !mainRanks.contains(rank))
                .sorted(Comparator.comparingInt(Card.Rank::getValue).reversed())
                .limit(numberOfKickers)
                .collect(Collectors.toList());
    }


    @Override
    public int compareTo(Hand other) {
        if (this.handType.getStrength() != other.handType.getStrength()) {
            return Integer.compare(this.handType.getStrength(), other.handType.getStrength());
        }

        // Si même type de main, comparer les kickers
        // Pour Full House, le brelan est plus important, puis la paire.
        // KickerRanks sont déjà stockés dans l'ordre d'importance.
        List<Card.Rank> thisKickers = this.getKickerRanks();
        List<Card.Rank> otherKickers = other.getKickerRanks();

        // Cas spécifique pour Straight et Straight Flush (où le kicker est la carte haute)
        // et Flush (où tous les 5 rangs sont des kickers).
        // Pour Four of a Kind, Three of a Kind, Two Pair, One Pair, High Card, les kickers sont bien définis.

        // Pour Straight/StraightFlush, kickerRanks contient une seule carte : la plus haute de la quinte.
        // Pour le wheel (A-5), l'As est le plus haut pour la quinte A-K-Q-J-T, mais le 5 est le plus haut pour A-2-3-4-5.
        // La logique de isStraight et la façon dont les kickers sont stockés doivent être cohérentes.
        // Si c'est une wheel A-5-4-3-2, la carte la plus haute est le 5 pour la comparaison.
        // Notre `kickerRanks` pour STRAIGHT/STRAIGHT_FLUSH stocke la carte la plus haute (Ace pour A-K-Q-J-T, 5 pour A-2-3-4-5 si on le décide ainsi).
        // La méthode `isStraight` trie A-5-4-3-2. Si on stocke le premier élément, c'est l'As.
        // Il faut une convention. Normalement, une quinte A-5 est moins forte qu'une quinte K-Q-J-T-9.
        // Le rang de la quinte est déterminé par sa carte la plus haute.
        // Pour A-5, c'est 5. Pour T-A, c'est A.
        if (this.handType == HandType.STRAIGHT || this.handType == HandType.STRAIGHT_FLUSH) {
            Card.Rank thisHighCard = getEffectiveHighCardForStraight(this.cards);
            Card.Rank otherHighCard = getEffectiveHighCardForStraight(other.cards);
            return Integer.compare(thisHighCard.getValue(), otherHighCard.getValue());
        }


        for (int i = 0; i < Math.min(thisKickers.size(), otherKickers.size()); i++) {
            if (thisKickers.get(i).getValue() != otherKickers.get(i).getValue()) {
                return Integer.compare(thisKickers.get(i).getValue(), otherKickers.get(i).getValue());
            }
        }
        return 0; // Mains parfaitement égales
    }

    /**
     * Pour les quintes, la carte la plus haute détermine sa force.
     * Gère le cas spécial de la quinte A-2-3-4-5 (wheel) où le 5 est la carte haute pour la comparaison.
     * @param sortedStraightCards Les 5 cartes de la quinte, triées par rang décroissant.
     * @return Le rang de la carte la plus haute effective de la quinte.
     */
    private static Card.Rank getEffectiveHighCardForStraight(List<Card> sortedStraightCards) {
        // sortedStraightCards est triée par rang décroissant (A, K, Q, J, T) ou (A, 5, 4, 3, 2)
        if (sortedStraightCards.get(0).getRank() == Card.Rank.ACE &&
            sortedStraightCards.get(1).getRank() == Card.Rank.FIVE) {
            return Card.Rank.FIVE; // Wheel A-5, le 5 est la carte haute pour la comparaison
        }
        return sortedStraightCards.get(0).getRank(); // Carte la plus haute
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(handType.getDisplayName());
        sb.append(" (").append(cards.stream().map(Card::toString).collect(Collectors.joining(", "))).append(")");
        if (!kickerRanks.isEmpty()) {
            // Pourrait être plus précis sur quels kickers sont pertinents pour quel type de main.
            // Par exemple, pour un Full House, on pourrait afficher "Full House, Aces over Kings".
            // Pour l'instant, on affiche le type et les cartes.
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Hand hand = (Hand) o;
        return handType == hand.handType && Objects.equals(kickerRanks, hand.kickerRanks) && new HashSet<>(cards).equals(new HashSet<>(hand.cards));
        // La comparaison des kickers devrait suffire si compareTo retourne 0.
        // Comparer les listes de cartes directement peut être sensible à l'ordre si elles ne sont pas triées de manière identique.
        // Utiliser compareTo == 0 est plus robuste pour l'égalité sémantique.
        // return compareTo(hand) == 0; // Plus simple et correct
    }

    @Override
    public int hashCode() {
        // Basé sur le type et les kickers, car ce sont eux qui définissent la force unique de la main.
        // Les cartes spécifiques (couleurs) ne comptent que pour flush.
        // Pour être cohérent avec equals (si on utilise compareTo), le hashcode devrait refléter cela.
        int result = Objects.hash(handType);
        // Pour les quintes A-5, le kicker effectif est 5.
        if (handType == HandType.STRAIGHT || handType == HandType.STRAIGHT_FLUSH) {
            result = 31 * result + Objects.hash(getEffectiveHighCardForStraight(this.cards));
        } else {
            result = 31 * result + Objects.hash(kickerRanks);
        }
        return result;
    }

    // --- Méthodes d'aide pour l'évaluation (visibilité package ou private) ---
    // La plupart ont été rendues statiques et privées ci-dessus.
}
