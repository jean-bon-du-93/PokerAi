package com.poker.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GameEngineTest {
    private static final Logger log = LoggerFactory.getLogger(GameEngineTest.class);

    private Player player1;
    private Player player2;
    private Player player3;
    private List<Player> players;
    private GameEngine engine;

    private final double SB = 5;
    private final double BB = 10;

    @BeforeEach
    void setUp() {
        player1 = new Player("Alice", 1000);
        player2 = new Player("Bob", 1000);
        player3 = new Player("Charlie", 1000);
        players = new ArrayList<>(List.of(player1, player2, player3));
        // Note: GameEngine clones players, so modifications to player1, player2, player3
        // after engine creation won't affect the engine's internal list directly,
        // but we can use them for referencing who is who.
        engine = new GameEngine(players, SB, BB);
    }

    private Player getEnginePlayer(Player originalPlayer) {
        return engine.getPlayers().stream()
                     .filter(p -> p.getId().equals(originalPlayer.getId()))
                     .findFirst()
                     .orElseThrow(() -> new AssertionError("Player not found in engine: " + originalPlayer.getName()));
    }


    @Test
    void startNewHand_assignsBlindsAndDistributesCards() {
        GameState state = engine.startNewHand();
        assertNotNull(state);
        assertEquals(GameState.BettingRound.PREFLOP, state.getCurrentBettingRound());
        assertEquals(SB + BB, state.getPotSize(), 0.01);

        // Verify blinds were posted
        // This depends on dealer position, which is random.
        // We need to find SB and BB players.
        int dealerPos = state.getDealerButtonPosition();
        int numPlayers = state.getPlayers().size();

        Player sbPlayer = state.getPlayers().get((dealerPos + 1) % numPlayers);
        Player bbPlayer = state.getPlayers().get((dealerPos + 2) % numPlayers);

        assertEquals(SB, sbPlayer.getAmountBetThisRound());
        assertEquals(1000 - SB, sbPlayer.getStack(), 0.01);

        assertEquals(BB, bbPlayer.getAmountBetThisRound());
        assertEquals(1000 - BB, bbPlayer.getStack(), 0.01);

        for (Player p : state.getPlayers()) {
            assertEquals(2, p.getHoleCards().size(), "Each player should have 2 hole cards.");
        }

        assertNotNull(state.getCurrentPlayer(), "There should be a current player to act.");
        assertFalse(state.getLegalActions().isEmpty(), "Current player should have legal actions.");
    }

    @Test
    void handleAction_Fold_PlayerFoldsAndNextPlayerActs() {
        GameState state = engine.startNewHand();
        Player firstToActOriginal = state.getCurrentPlayer(); // This is a copy from GameState
        assertNotNull(firstToActOriginal, "Initial state must have a current player.");

        Player firstToActInEngine = getEnginePlayer(firstToActOriginal);

        Action foldAction = new Action(Action.ActionType.FOLD);
        state = engine.handleAction(foldAction);

        assertEquals(Player.PlayerStatus.FOLDED, firstToActInEngine.getStatus(), "Player should be folded.");
        assertNotEquals(firstToActOriginal.getId(), state.getCurrentPlayer().getId(), "Current player should change after a fold.");
        assertTrue(state.getActionHistoryThisRound().get(state.getActionHistoryThisRound().size()-1).contains("FOLD"));
    }

    @Test
    void handleAction_Call_PlayerCallsAndPotIncreases() {
        GameState state = engine.startNewHand(); // SB=5, BB=10. Pot=15. BetToCall=10
        Player firstToAct = getEnginePlayer(state.getCurrentPlayer());
        double initialStack = firstToAct.getStack();
        double betToCall = state.getAmountToCallForPlayer(state.getCurrentPlayer()); // Should be 10 if UTG

        Action callAction = new Action(Action.ActionType.CALL);
        state = engine.handleAction(callAction);

        assertEquals(betToCall, firstToAct.getAmountBetThisRound(), 0.01, "Player should have called the correct amount.");
        assertEquals(initialStack - betToCall, firstToAct.getStack(), 0.01, "Player stack should decrease by call amount.");
        assertEquals(SB + BB + betToCall, state.getPotSize(), 0.01, "Pot size should increase by call amount.");
    }

    @Test
    void handleAction_Bet_PlayerBetsAndPotIncreases_CurrentBetChanges() {
        GameState state = engine.startNewHand();
        // Manually advance to a state where a BET is possible (e.g., BB checks option preflop, or postflop)
        // For simplicity, let's assume it's post-flop, first to act.
        // We need to simulate actions to get to that state.

        // Let's simplify: UTG calls, SB calls, BB checks. Then Flop. SB is first to act.
        // This is too complex for a simple Bet test without a lot of setup.
        // Instead, let's find a player who can check, then the next player bets.

        // Assume player P1 is SB, P2 is BB, P3 is UTG (Button).
        // Start hand: P3=Dealer. P1=SB(5). P2=BB(10). P3=UTG.
        // P3 (UTG) acts first. BetToCall = 10.
        // If P3 calls (10), P1 (SB) acts. BetToCall = 10. P1 needs to add 5.
        // If P1 calls (adds 5), P2 (BB) acts. BetToCall = 10. P2 already has 10. P2 can check or raise.

        // Find UTG (player after BB)
        int dealerPos = state.getDealerButtonPosition();
        int numPlayers = state.getPlayers().size();
        int utgIndex = (dealerPos + 3) % numPlayers; // If 3 players, this is the dealer.
                                                     // If 2 players, SB acts first.
        if (numPlayers == 2) utgIndex = dealerPos; // SB is first to act preflop in HU

        Player utgPlayerState = state.getPlayers().get(utgIndex);
        Player utgPlayerEngine = getEnginePlayer(utgPlayerState);

        // UTG calls
        engine.handleAction(new Action(Action.ActionType.CALL));

        // Next player (SB if 3 players, BB if 2 players)
        state = engine.getCurrentGameState();
        Player secondToActState = state.getCurrentPlayer();
        Player secondToActEngine = getEnginePlayer(secondToActState);
        double amountToCallForSecond = state.getAmountToCallForPlayer(secondToActState);
        // SB calls (adds `amountToCallForSecond` which is BB - SB amount)
        engine.handleAction(new Action(Action.ActionType.CALL));

        // Next player (BB)
        state = engine.getCurrentGameState();
        Player bbPlayerState = state.getCurrentPlayer(); // Should be BB
        Player bbPlayerEngine = getEnginePlayer(bbPlayerState);

        // BB checks (option)
        engine.handleAction(new Action(Action.ActionType.CHECK));

        // Now it's Flop. First to act is SB (or player after dealer).
        state = engine.getCurrentGameState();
        assertEquals(GameState.BettingRound.FLOP, state.getCurrentBettingRound());
        assertEquals(0, state.getCurrentBetToCall(), "Bet to call should be 0 at start of Flop.");

        Player flopFirstActorState = state.getCurrentPlayer();
        Player flopFirstActorEngine = getEnginePlayer(flopFirstActorState);
        double initialStackFlop = flopFirstActorEngine.getStack();
        double initialPotFlop = state.getPotSize();

        double betAmount = 20;
        Action betAction = new Action(Action.ActionType.BET, betAmount);
        state = engine.handleAction(betAction);

        assertEquals(betAmount, flopFirstActorEngine.getAmountBetThisRound(), 0.01);
        assertEquals(initialStackFlop - betAmount, flopFirstActorEngine.getStack(), 0.01);
        assertEquals(initialPotFlop + betAmount, state.getPotSize(), 0.01);
        assertEquals(betAmount, state.getCurrentBetToCall(), 0.01, "Current bet to call should be the bet amount.");
    }

    @Test
    void handleAction_Raise_PlayerRaisesAndPotIncreases_CurrentBetChanges() {
        GameState state = engine.startNewHand(); // SB=5, BB=10. Pot=15. BetToCall=10

        Player utgPlayer = getEnginePlayer(state.getCurrentPlayer());
        double utgInitialStack = utgPlayer.getStack();

        double raiseAmountTotal = 30; // UTG raises to 30
        Action raiseAction = new Action(Action.ActionType.RAISE, raiseAmountTotal);
        state = engine.handleAction(raiseAction);

        assertEquals(raiseAmountTotal, utgPlayer.getAmountBetThisRound(), 0.01);
        assertEquals(utgInitialStack - raiseAmountTotal, utgPlayer.getStack(), 0.01);
        assertEquals(SB + BB + (raiseAmountTotal /*- 0, as UTG had 0 in before this action*/), state.getPotSize(), 0.01);
        assertEquals(raiseAmountTotal, state.getCurrentBetToCall(), 0.01, "Current bet to call should be the raise amount.");
        // Min next raise should be currentBet + (currentBet - previousBet) = 30 + (30 - 10) = 50
        assertEquals(raiseAmountTotal + (raiseAmountTotal - BB) , state.getMinRaiseAmount(), 0.01);
    }

    @Test
    void bettingRoundEnds_FlopTurnRiverAreDealt() {
        GameState state = engine.startNewHand(); // Preflop

        // Simulate actions for Preflop to end: UTG calls, SB folds, BB checks
        // This depends on who is UTG, SB, BB.
        // For 3 players: P0=D, P1=SB, P2=BB. UTG is P0.
        // Order: P0 (UTG/D), P1 (SB), P2 (BB)
        // Let's make it simple: everyone calls/checks.

        // Player 1 (UTG equivalent for this round)
        Player p1 = getEnginePlayer(state.getCurrentPlayer());
        engine.handleAction(new Action(Action.ActionType.CALL)); // Call BB
        log.debug("State after P1 CALL: {}", engine.getCurrentGameState());

        // Player 2 (SB equivalent)
        state = engine.getCurrentGameState();
        Player p2 = getEnginePlayer(state.getCurrentPlayer());
        engine.handleAction(new Action(Action.ActionType.CALL)); // Call BB (SB adds remaining)
        log.debug("State after P2 CALL: {}", engine.getCurrentGameState());

        // Player 3 (BB equivalent)
        state = engine.getCurrentGameState();
        Player p3 = getEnginePlayer(state.getCurrentPlayer());
        // BB has option if no raise. Amount to call for BB is 0 if currentBet is BB.
        if (state.getAmountToCallForPlayer(state.getCurrentPlayer()) == 0) {
            engine.handleAction(new Action(Action.ActionType.CHECK));
        } else {
             engine.handleAction(new Action(Action.ActionType.CALL)); // Should not happen if no raise
        }
        log.debug("State after P3 CHECK/CALL: {}", engine.getCurrentGameState());

        // Should be Flop now
        state = engine.getCurrentGameState();
        assertEquals(GameState.BettingRound.FLOP, state.getCurrentBettingRound());
        assertEquals(3, state.getCommunityCards().size());
        assertEquals(BB * players.size(), state.getPotSize(), 0.01); // If everyone just called BB

        // Simulate actions for Flop to end (e.g., everyone checks)
        for (int i = 0; i < players.size(); i++) {
            if (getEnginePlayer(state.getCurrentPlayer()).getStatus() == Player.PlayerStatus.ACTIVE) {
                 if (state.getAmountToCallForPlayer(state.getCurrentPlayer()) == 0) {
                    engine.handleAction(new Action(Action.ActionType.CHECK));
                 } else {
                     fail("Should be able to check if no bet on flop start");
                 }
                 state = engine.getCurrentGameState();
            }
        }
        assertEquals(GameState.BettingRound.TURN, state.getCurrentBettingRound());
        assertEquals(4, state.getCommunityCards().size());

        // Simulate actions for Turn to end
         for (int i = 0; i < players.size(); i++) {
            if (getEnginePlayer(state.getCurrentPlayer()).getStatus() == Player.PlayerStatus.ACTIVE) {
                 if (state.getAmountToCallForPlayer(state.getCurrentPlayer()) == 0) {
                    engine.handleAction(new Action(Action.ActionType.CHECK));
                 } else {
                     fail("Should be able to check if no bet on turn start");
                 }
                 state = engine.getCurrentGameState();
            }
        }
        assertEquals(GameState.BettingRound.RIVER, state.getCurrentBettingRound());
        assertEquals(5, state.getCommunityCards().size());

        // Simulate actions for River to end
        for (int i = 0; i < players.size(); i++) {
            if (getEnginePlayer(state.getCurrentPlayer()).getStatus() == Player.PlayerStatus.ACTIVE) {
                 if (state.getAmountToCallForPlayer(state.getCurrentPlayer()) == 0) {
                    engine.handleAction(new Action(Action.ActionType.CHECK));
                 } else {
                     fail("Should be able to check if no bet on river start");
                 }
                 state = engine.getCurrentGameState();
            }
        }
        assertEquals(GameState.BettingRound.SHOWDOWN, state.getCurrentBettingRound());
        assertTrue(state.isHandOver());
        assertNotNull(state.getWinners());
        assertFalse(state.getWinners().isEmpty());
        assertNotNull(state.getWinningHand()); // Assuming more than 1 player went to showdown
    }

    @Test
    void handEndsWhenAllButOneFold() {
        GameState state = engine.startNewHand();

        Player p1_engine = getEnginePlayer(state.getCurrentPlayer()); // UTG
        engine.handleAction(new Action(Action.ActionType.FOLD));
        log.debug("State after P1 FOLD: {}", engine.getCurrentGameState());

        state = engine.getCurrentGameState();
        Player p2_engine = getEnginePlayer(state.getCurrentPlayer()); // SB
        engine.handleAction(new Action(Action.ActionType.FOLD));
        log.debug("State after P2 FOLD: {}", engine.getCurrentGameState());

        // BB (P3) should be the winner
        state = engine.getCurrentGameState();
        assertTrue(state.isHandOver());
        assertEquals(1, state.getWinners().size());

        // Find BB player (depends on initial dealer position)
        int dealerPos = state.getDealerButtonPosition();
        int numPlayers = state.getPlayers().size();
        Player bbPlayerInitial = players.get((dealerPos + 2) % numPlayers); // Original player object
        Player winnerInState = state.getWinners().get(0);

        assertEquals(bbPlayerInitial.getId(), winnerInState.getId(), "BB should be the winner.");

        Player bbPlayerEngine = getEnginePlayer(bbPlayerInitial);
        // Pot should be SB + BB, which BB wins back + SB's contribution.
        // Initial pot was SB+BB. BB put in BB. SB put in SB.
        // BB wins SB's blind. So BB's stack should be initial_stack - BB (posted) + (SB+BB) (pot) = initial_stack + SB.
        assertEquals(1000 - BB + (SB + BB), bbPlayerEngine.getStack(), 0.01);
    }

    @Test
    void headsupGame_BlindsAndOrder() {
        players = new ArrayList<>(List.of(player1, player2));
        engine = new GameEngine(players, SB, BB); // Player1, Player2

        GameState state = engine.startNewHand();
        // In HU: Dealer posts SB and acts first pre-flop. Other player posts BB.
        int dealerIdx = state.getDealerButtonPosition();
        Player dealerPlayer = state.getPlayers().get(dealerIdx);
        Player otherPlayer = state.getPlayers().get((dealerIdx + 1) % 2);

        assertEquals(SB, dealerPlayer.getAmountBetThisRound(), "Dealer (SB) should post SB.");
        assertEquals(BB, otherPlayer.getAmountBetThisRound(), "Other player (BB) should post BB.");

        assertEquals(dealerPlayer.getId(), state.getCurrentPlayer().getId(), "Dealer (SB) should act first pre-flop in HU.");

        // SB calls (completes to BB amount)
        engine.handleAction(new Action(Action.ActionType.CALL));
        state = engine.getCurrentGameState();

        // BB's turn (option)
        assertEquals(otherPlayer.getId(), state.getCurrentPlayer().getId(), "BB should act after SB calls.");
        engine.handleAction(new Action(Action.ActionType.CHECK)); // BB checks
        state = engine.getCurrentGameState();

        // Flop
        assertEquals(GameState.BettingRound.FLOP, state.getCurrentBettingRound());
        // Post-flop, BB (non-dealer) acts first.
        assertEquals(otherPlayer.getId(), state.getCurrentPlayer().getId(), "BB (non-dealer) should act first post-flop in HU.");
    }

    @Test
    void allInPlayer_CannotActFurther_SidePotsNotFullyTestedHere() {
        player1 = new Player("Alice", 20); // Alice is short stacked
        player2 = new Player("Bob", 1000);
        player3 = new Player("Charlie", 1000);
        players = new ArrayList<>(List.of(player1, player2, player3));
        engine = new GameEngine(players, SB, BB); // SB=5, BB=10

        GameState state = engine.startNewHand();
        // Find Alice. Assume Alice is UTG for simplicity (or forced to act).
        // Let's say Alice is UTG and goes all-in.
        // This requires setting dealer position or iterating until Alice acts.

        // Simplified: Assume Alice (P1) is current player and goes all-in with a RAISE.
        // This test setup is tricky without controlling dealer button.
        // Let's find Alice in the game state players list.
        Player aliceEngine = engine.getPlayers().stream().filter(p -> p.getId().equals(player1.getId())).findFirst().get();

        // Manually set Alice to be current player for this test fragment (not ideal, but for focused test)
        // This is hard because GameState is immutable and engine controls it.
        // Instead, let's make Alice go all-in when it's her turn.

        // Iterate turns until it's Alice's turn or hand ends.
        for(int i=0; i < players.size() * 2 && !state.isHandOver(); ++i) { // Limit iterations
            Player currentPlayer = state.getCurrentPlayer();
            if (currentPlayer == null) break; // Hand ended

            if (currentPlayer.getId().equals(aliceEngine.getId())) {
                // Alice's turn.
                // If bet to call is > Alice's stack, she can only CALL all-in or FOLD.
                // If she can raise all-in:
                double amountToCall = state.getAmountToCallForPlayer(currentPlayer);
                if (aliceEngine.getStack() + aliceEngine.getAmountBetThisRound() > amountToCall) { // Can raise
                    Action allInRaise = new Action(Action.ActionType.RAISE, aliceEngine.getStack() + aliceEngine.getAmountBetThisRound());
                    state = engine.handleAction(allInRaise);
                } else { // Can only call all-in or fold
                    Action allInCall = new Action(Action.ActionType.CALL); // Call will cap at her stack
                    state = engine.handleAction(allInCall);
                }
                assertEquals(Player.PlayerStatus.ALL_IN, aliceEngine.getStatus());
                assertEquals(0, aliceEngine.getStack(), 0.01);
                break;
            } else {
                // Other players just call to keep it simple
                 if (state.getAmountToCallForPlayer(currentPlayer) > 0) {
                    state = engine.handleAction(new Action(Action.ActionType.CALL));
                 } else {
                    state = engine.handleAction(new Action(Action.ActionType.CHECK));
                 }
            }
             if (state.isHandOver()) break;
        }
        assertTrue(aliceEngine.getStatus() == Player.PlayerStatus.ALL_IN || state.isHandOver(), "Alice should be all-in or hand ended.");

        // Now, if Alice is all-in, subsequent getCurrentPlayer() should skip her.
        // And legal actions for her should be empty.
        if (aliceEngine.isAllIn()) {
            final GameState finalState = state; // For lambda
            Optional<Player> aliceInFinalStateOpt = finalState.getPlayers().stream().filter(p -> p.getId().equals(aliceEngine.getId())).findFirst();
            assertTrue(aliceInFinalStateOpt.isPresent());
            assertTrue(aliceInFinalStateOpt.get().isAllIn());

            // If betting continues, Alice should not be asked to act.
            // This part of test would require more players and continued betting.
            // For now, we've confirmed she goes all-in and her stack is 0.
            // Side pot logic is more complex and would need dedicated tests.
        }
    }
}
