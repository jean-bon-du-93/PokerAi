package com.poker.app;

import com.poker.ai.DQNAgent;
import com.poker.ai.PokerAIAgent;
import com.poker.engine.*;
import com.poker.training.TrainingEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0];

        if ("--train".equalsIgnoreCase(mode)) {
            runTrainingMode(args);
        } else if ("--play".equalsIgnoreCase(mode)) {
            runPlayMode(args);
        } else {
            logger.error("Invalid mode: {}", mode);
            printUsage();
        }
    }

    private static void runTrainingMode(String[] args) {
        int numPlayers = 3; // Default
        int numHands = 100000; // Default
        int saveInterval = 10000; // Default

        // TODO: Parser les arguments pour numPlayers, numHands, saveInterval si nécessaire
        // Exemple: --train --players 6 --hands 200000 --save 5000
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--players") && i + 1 < args.length) {
                try {
                    numPlayers = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    logger.error("Invalid number for players: {}", args[i]);
                    return;
                }
            } else if (args[i].equalsIgnoreCase("--hands") && i + 1 < args.length) {
                try {
                    numHands = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    logger.error("Invalid number for hands: {}", args[i]);
                    return;
                }
            } else if (args[i].equalsIgnoreCase("--saveInterval") && i + 1 < args.length) {
                 try {
                    saveInterval = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    logger.error("Invalid number for saveInterval: {}", args[i]);
                    return;
                }
            }
        }

        logger.info("Starting Training Mode with {} players, {} hands, saving every {} hands.", numPlayers, numHands, saveInterval);
        TrainingEnvironment trainingEnv = new TrainingEnvironment(numPlayers);
        trainingEnv.train(numHands, saveInterval);
        logger.info("Training finished.");
    }

    private static void runPlayMode(String[] args) {
        logger.info("Starting Play Mode...");
        Scanner scanner = new Scanner(System.in);

        int numAiOpponents = 1; // Default: 1 AI opponent
        String modelPath = "poker_dqn_agent_Agent_1_final.zip"; // Default model path for Agent_1

        // TODO: Parser les arguments pour numAiOpponents, modelPath
        // Exemple: --play --opponents 2 --modelAgent1 path1.zip --modelAgent2 path2.zip
        // Pour l'instant, on simplifie avec un seul modèle pour tous les IA.

        List<PokerAIAgent> agents = new ArrayList<>();
        List<Player> enginePlayers = new ArrayList<>();

        // Human Player
        System.out.print("Enter your name: ");
        String humanName = scanner.nextLine();
        Player humanPlayer = new Player(humanName, 10000); // Stack initial
        enginePlayers.add(humanPlayer);

        // AI Opponents
        for (int i = 0; i < numAiOpponents; i++) {
            String agentId = "AI_Opponent_" + (i + 1);
            // Pour l'instant, on utilise la config par défaut du DQNAgent, mais on devrait la rendre configurable.
            // Surtout inputSize et outputSize (actionTypes) doivent correspondre au modèle chargé.
             List<Action.ActionType> defaultActionTypes = List.of(
                Action.ActionType.FOLD, Action.ActionType.CHECK, Action.ActionType.CALL,
                Action.ActionType.BET, Action.ActionType.RAISE);
            StateVectorizer tempVectorizer = new StateVectorizer(numAiOpponents + 1);


            DQNAgent aiAgent = new DQNAgent(agentId, tempVectorizer.getVectorSize(), defaultActionTypes,
                                            1, 1, 0.001, 0.99,
                                            0.0, 0.0, 0.0, numAiOpponents + 1); // Epsilon à 0 pour exploitation
            try {
                // Utiliser un chemin de modèle spécifique si fourni, sinon le défaut.
                // Pour un jeu multi-IA, il faudrait charger des modèles différents.
                String currentModelPath = modelPath; // TODO: adapter si plusieurs modèles
                logger.info("Loading model for {} from {}", agentId, currentModelPath);
                aiAgent.loadModel(currentModelPath);
                aiAgent.setInitialStackForNormalization(10000); // Doit correspondre à l'entraînement
            } catch (IOException e) {
                logger.error("Failed to load model for AI agent {}: {}. AI will play randomly (high epsilon).", agentId, e.getMessage());
                // L'agent jouera avec son epsilon initial (qui devrait être 0 pour le mode jeu)
                // On peut forcer epsilon à 0 ou à 1 (complètement aléatoire) si le modèle ne charge pas.
            }
            agents.add(aiAgent);
            enginePlayers.add(new Player(agentId, 10000));
        }

        GameEngine gameEngine = new GameEngine(enginePlayers, 50, 100); // SB=50, BB=100

        while (true) { // Boucle de jeu principale (plusieurs mains)
            GameState gameState = gameEngine.startNewHand();
            if (gameState.isHandOver() && gameState.getPlayers().size() < 2) {
                logger.info("Not enough players to continue. Game over.");
                break;
            }

            // Afficher les stacks au début de la main
            System.out.println("\n--- New Hand ---");
            gameState.getPlayers().forEach(p ->
                System.out.printf("%s stack: %.0f%n", p.getName(), p.getStack())
            );


            while (!gameState.isHandOver()) {
                Player currentPlayer = gameState.getCurrentPlayer();
                if (currentPlayer == null) break;

                System.out.println("\n------------------------------------");
                System.out.println("Community Cards: " + gameState.getCommunityCards());
                System.out.println("Pot: " + String.format("%.0f", gameState.getPotSize()));
                System.out.println("Current Bet To Call: " + String.format("%.0f", gameState.getCurrentBetToCall()));
                System.out.println("It's " + currentPlayer.getName() + "'s turn.");
                System.out.println(currentPlayer.getName() + " stack: " + String.format("%.0f", currentPlayer.getStack()));
                if (currentPlayer.getId().equals(humanPlayer.getId())) {
                    System.out.println("Your cards: " + currentPlayer.getHoleCards());
                }


                List<Action> legalActions = gameEngine.determineLegalActions(currentPlayer, gameState);
                if (legalActions.isEmpty()) {
                    System.out.println(currentPlayer.getName() + " has no legal actions.");
                    // Normalement, GameEngine devrait gérer cela et passer au joueur suivant ou terminer le tour/main.
                    // Forcer une action vide ou un check si possible pour avancer, mais c'est un patch.
                    // Si on est ici, c'est probablement que le joueur est all-in et que l'action a été "passée".
                    // Le GameEngine devrait avancer automatiquement. Si on boucle ici, c'est un problème.
                    break;
                }

                Action chosenAction;
                if (currentPlayer.getId().equals(humanPlayer.getId())) { // Tour du joueur humain
                    System.out.println("Legal Actions:");
                    for (int i = 0; i < legalActions.size(); i++) {
                        System.out.println((i + 1) + ": " + legalActions.get(i));
                    }
                    System.out.print("Choose action (number): ");
                    int choice = -1;
                    try {
                        choice = Integer.parseInt(scanner.nextLine()) - 1;
                        if (choice < 0 || choice >= legalActions.size()) throw new NumberFormatException();
                        chosenAction = legalActions.get(choice);

                        if (chosenAction.getType() == Action.ActionType.BET || chosenAction.getType() == Action.ActionType.RAISE) {
                            // Si l'action légale avait un montant par défaut (ex: all-in), on l'utilise.
                            // Sinon, on demande le montant.
                            // Pour les actions prédéfinies par determineLegalActions (ex: BET POT), le montant est déjà là.
                            // Si on veut permettre des montants arbitraires:
                            if (chosenAction.getAmount() == 0 ||
                                (chosenAction.getType() == Action.ActionType.BET && chosenAction.getAmount() == 100) || // Si c'est le bet BB par défaut
                                (chosenAction.getType() == Action.ActionType.RAISE && chosenAction.getAmount() == gameState.getMinRaiseAmount()) // Si c'est le min_raise par défaut
                                ) {
                                System.out.print("Enter amount for " + chosenAction.getType() +
                                                 (chosenAction.getType() == Action.ActionType.RAISE ? " (total bet amount)" : "") +
                                                 ": ");
                                double amount = Double.parseDouble(scanner.nextLine());
                                if (amount <=0) throw new IllegalArgumentException("Amount must be positive.");
                                // Valider le montant par rapport au stack, min_bet/min_raise
                                // Pour la simplicité, on laisse GameEngine valider plus tard.
                                chosenAction = new Action(chosenAction.getType(), amount);
                            }
                        }

                    } catch (NumberFormatException | IllegalArgumentException e) {
                        System.out.println("Invalid choice. Defaulting to FOLD (if possible) or first action.");
                        chosenAction = legalActions.stream().filter(a -> a.getType() == Action.ActionType.FOLD).findFirst().orElse(legalActions.get(0));
                    }
                } else { // Tour de l'IA
                    PokerAIAgent ai = agents.stream()
                                           .filter(a -> a.getName().equals(currentPlayer.getId()))
                                           .findFirst()
                                           .orElseThrow();
                    chosenAction = ai.chooseAction(gameState, legalActions);
                    System.out.println(currentPlayer.getName() + " (AI) chooses: " + chosenAction);
                }
                gameState = gameEngine.handleAction(chosenAction);
                // Petite pause pour lisibilité
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }

            // Fin de la main
            System.out.println("\n--- Hand Over ---");
            System.out.println("Community Cards: " + gameState.getCommunityCards());
            System.out.println("Final Pot: " + String.format("%.0f", gameState.getPotSize()));

            if (gameState.getWinners() != null && !gameState.getWinners().isEmpty()) {
                System.out.print("Winner(s): ");
                List<String> winnerNames = new ArrayList<>();
                for (Player winner : gameState.getWinners()) {
                    winnerNames.add(winner.getName());
                    // Montrer les cartes des gagnants et des autres joueurs impliqués dans le showdown
                    System.out.print(winner.getName() + (winner.getHoleCards().isEmpty() ? "" : " with " + winner.getHoleCards()) + " ");
                }
                System.out.println();
                if (gameState.getWinningHand() != null) {
                    System.out.println("Winning Hand Type: " + gameState.getWinningHand().getHandType().getDisplayName());
                    System.out.println("Winning Hand Cards: " + gameState.getWinningHand().getCards());
                }
            } else {
                System.out.println("No winner declared by GameEngine (should not happen if pot > 0).");
            }

            // Afficher les stacks mis à jour
            System.out.println("Stacks after hand:");
             gameState.getPlayers().forEach(p -> {
                System.out.printf("%s stack: %.0f%n", p.getName(), p.getStack());
                // Révéler les cartes de l'IA si c'était un showdown impliquant l'IA
                if (!p.getId().equals(humanPlayer.getId()) &&
                    (gameState.getCurrentBettingRound() == GameState.BettingRound.SHOWDOWN || gameState.isHandOver()) &&
                    !p.getHoleCards().isEmpty() && p.getStatus() != Player.PlayerStatus.FOLDED) {
                    System.out.printf("  %s's cards: %s%n", p.getName(), p.getHoleCards());
                }
            });


            System.out.print("\nPlay another hand? (y/n): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
                break;
            }

            // Vérifier si le joueur humain a encore des jetons
            Player humanInEngineList = gameState.getPlayers().stream().filter(p -> p.getId().equals(humanPlayer.getId())).findFirst().orElse(null);
            if (humanInEngineList != null && humanInEngineList.getStack() <= 0) {
                System.out.println(humanName + ", you are out of chips! Game over.");
                break;
            }
            if (gameState.getPlayers().stream().filter(p -> p.getStack() > 0).count() < 2) {
                 System.out.println("Not enough players with chips to continue. Game over.");
                 break;
            }
        }
        scanner.close();
        System.out.println("Exiting Play Mode.");
    }


    private static void printUsage() {
        System.out.println("Poker AI Application");
        System.out.println("Usage: java -jar poker-ai.jar <mode> [options]");
        System.out.println("Modes:");
        System.out.println("  --train         Run in training mode.");
        System.out.println("    Options for --train:");
        System.out.println("      --players <N>     Number of AI agents to train (default: 3).");
        System.out.println("      --hands <N>       Number of hands to play for training (default: 100000).");
        System.out.println("      --saveInterval <N> Number of hands between model saves (default: 10000).");
        System.out.println("  --play          Run in interactive play mode against AI(s).");
        System.out.println("    Options for --play:");
        System.out.println("      --opponents <N>   Number of AI opponents (default: 1).");
        System.out.println("      --model <path>    Path to the pre-trained AI model (default: poker_dqn_agent_Agent_1_final.zip).");
        // TODO: Add options for multiple AI models if more opponents.
    }
}
