package com.poker.ai;

import com.poker.engine.Action;
import com.poker.engine.GameState;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.*;
import java.io.File;
import java.io.IOException;
import org.deeplearning4j.util.ModelSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Un agent IA qui utilise un Deep Q-Network (DQN) pour prendre des décisions au poker.
 * Implémente PokerAIAgent.
 */
public class DQNAgent implements PokerAIAgent {
    private static final Logger logger = LoggerFactory.getLogger(DQNAgent.class);

    private final String agentId;
    private MultiLayerNetwork model; // Le réseau de neurones (Q-Network)
    private final StateVectorizer stateVectorizer;
    private final int inputSize; // Taille du vecteur d'état
    private final int outputSize; // Nombre d'actions possibles que le réseau peut prédire

    private final Random random = new Random();
    private double epsilon; // Pour la stratégie epsilon-greedy
    private final double epsilonDecay;
    private final double minEpsilon;

    // Mémoire de répétition (Replay Buffer)
    private final Deque<Transition> replayBuffer;
    private final int replayBufferSize;
    private final int batchSize; // Taille du mini-batch pour l'entraînement

    private final double gamma; // Facteur de décompte pour les récompenses futures (Bellman equation)

    private final List<Action.ActionType> possibleActionTypes; // Les types d'actions que l'IA peut choisir

    private double initialStackForNormalization; // Utilisé pour StateVectorizer

    /**
     * Représente une transition (état, action, récompense, état suivant, terminé)
     * stockée dans la mémoire de répétition.
     */
    public static class Transition {
        INDArray state;
        int actionIndex; // Index de l'action choisie dans la liste des actions possibles
        double reward;
        INDArray nextState;
        boolean done; // Si nextState est un état terminal

        public Transition(INDArray state, int actionIndex, double reward, INDArray nextState, boolean done) {
            this.state = state;
            this.actionIndex = actionIndex;
            this.reward = reward;
            this.nextState = nextState;
            this.done = done;
        }
    }

    public DQNAgent(String agentId, int inputSize, List<Action.ActionType> uniqueActionTypes,
                    int replayBufferSize, int batchSize, double learningRate, double gamma,
                    double initialEpsilon, double epsilonDecay, double minEpsilon,
                    int maxPlayersInGame) {
        this.agentId = agentId;
        this.stateVectorizer = new StateVectorizer(maxPlayersInGame);
        if (inputSize != this.stateVectorizer.getVectorSize()) {
            throw new IllegalArgumentException("Provided inputSize " + inputSize +
                                               " does not match StateVectorizer's size " + this.stateVectorizer.getVectorSize());
        }
        this.inputSize = inputSize;
        this.possibleActionTypes = Collections.unmodifiableList(new ArrayList<>(uniqueActionTypes));
        this.outputSize = uniqueActionTypes.size(); // Une sortie Q-value par type d'action

        this.replayBuffer = new ArrayDeque<>(replayBufferSize);
        this.replayBufferSize = replayBufferSize;
        this.batchSize = batchSize;
        this.gamma = gamma;

        this.epsilon = initialEpsilon;
        this.epsilonDecay = epsilonDecay;
        this.minEpsilon = minEpsilon;

        this.initialStackForNormalization = 200 * 10; // Default, e.g. 100BB * 2 (peut être ajusté)

        // Définition du réseau de neurones
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(random.nextLong())
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(learningRate))
                .list()
                .layer(0, new DenseLayer.Builder().nIn(this.inputSize).nOut(128) // Augmenter la taille des couches cachées
                        .activation(Activation.RELU)
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(128).nOut(128)
                        .activation(Activation.RELU)
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.MSE) // Mean Squared Error pour la régression des Q-values
                        .nIn(128).nOut(this.outputSize)
                        .activation(Activation.IDENTITY) // Pas d'activation spécifique pour les Q-values brutes
                        .build())
                .build();

        this.model = new MultiLayerNetwork(conf);
        this.model.init();
        logger.info("DQNAgent {} initialized with network: {}", agentId, model.summary());
        logger.info("Input size: {}, Output size (actions): {}", this.inputSize, this.outputSize);
    }

    @Override
    public String getName() {
        return agentId;
    }

    @Override
    public void gameStart() {
        // Peut être utilisé pour réinitialiser epsilon au début d'une nouvelle session d'entraînement,
        // ou charger le stack initial pour la normalisation.
        // Pour l'instant, on suppose que initialStackForNormalization est fixé ou mis à jour autrement.
    }

    public void setInitialStackForNormalization(double stack) {
        this.initialStackForNormalization = stack;
    }


    /**
     * Choisit une action en utilisant la politique epsilon-greedy.
     *
     * @param gameState L'état actuel du jeu.
     * @param legalActions La liste des actions Java {@link Action} légales.
     * @return L'action {@link Action} choisie.
     */
    @Override
    public Action chooseAction(GameState gameState, List<Action> legalActions) {
        if (legalActions == null || legalActions.isEmpty()) {
            // Cela peut arriver si le joueur est all-in ou si la main est terminée pour lui.
            // Ou si aucune action n'est légalement possible (ce qui serait un bug dans GameEngine).
            logger.warn("Agent {} has no legal actions to choose from in state: {}", agentId, gameState);
            // Tenter de retourner un FOLD si possible, sinon null pourrait causer NPE.
            // GameEngine devrait gérer le cas où un joueur n'a pas d'actions.
            // Si on est ici, c'est que GameEngine nous a demandé d'agir.
            // Si legalActions est vide, c'est un problème.
            // Pour l'instant, on va supposer que GameEngine ne demande pas d'action si aucune n'est légale.
            // Si cela arrive, il faudrait une action par défaut (ex: FOLD si possible, ou la première action légale).
            // Par sécurité, si on arrive ici avec une liste vide, c'est un état d'erreur.
             throw new IllegalStateException("Agent " + agentId + " was asked to choose an action but no legal actions were provided.");
        }

        INDArray stateVector = stateVectorizer.vectorizeState(gameState, agentId, initialStackForNormalization, 10.0); // TODO: BB Amount from engine

        int actionIndex;
        if (random.nextDouble() < epsilon) {
            // Exploration: choisir une action légale aléatoire
            // Il faut mapper les `legalActions` (objets Action) aux indices de sortie du réseau.
            // C'est un point délicat: le réseau prédit pour `possibleActionTypes`.
            // On doit s'assurer que l'action aléatoire choisie est effectivement légale dans le contexte actuel.
            actionIndex = mapLegalActionToRandomOutputIndex(legalActions);
            logger.debug("Agent {} (Exploration, eps={}) chose random action index: {}", agentId, String.format("%.3f",epsilon), actionIndex);

        } else {
            // Exploitation: choisir la meilleure action prédite par le réseau
            INDArray output = model.output(stateVector); // Q-values pour toutes les actions possibles
            actionIndex = getBestLegalActionIndex(output, legalActions);
            logger.debug("Agent {} (Exploitation, eps={}) chose best action index: {} (Q-values: {})", agentId, String.format("%.3f",epsilon), actionIndex, output);
        }

        // Convertir l'index d'action en un objet Action réel.
        // Cela suppose que `possibleActionTypes` à l'indice `actionIndex` correspond à une des `legalActions`.
        // Ce n'est pas toujours vrai. `possibleActionTypes` est fixe (ex: FOLD, CALL, RAISE_HALF_POT, RAISE_POT, ALL_IN).
        // `legalActions` est dynamique (ex: seulement FOLD et CALL, ou FOLD et RAISE_ALL_IN).
        // La méthode `getBestLegalActionIndex` et `mapLegalActionToRandomOutputIndex` doit gérer ce mapping.

        Action chosenAction = createActionFromTypeAndGameState(this.possibleActionTypes.get(actionIndex), gameState);

        // Vérifier si l'action choisie est réellement légale. Si non, prendre une action légale par défaut.
        boolean isChosenActionLegal = false;
        for (Action legal : legalActions) {
            if (actionsAreEquivalent(chosenAction, legal, gameState)) {
                // Remplacer par l'instance exacte de legalActions pour les montants précis (surtout pour all-in)
                chosenAction = legal;
                isChosenActionLegal = true;
                break;
            }
        }

        if (!isChosenActionLegal) {
            logger.warn("Agent {} chose an action ({}) that was not in the legal list {}. Defaulting to first legal action.",
                         agentId, chosenAction, legalActions);
            chosenAction = legalActions.get(0); // Defaulting
        }

        logger.info("Agent {} chooses action: {}", agentId, chosenAction);
        return chosenAction;
    }

    /**
     * Vérifie si deux actions sont sémantiquement équivalentes (ex: un RAISE_ALL_IN peut correspondre à un RAISE avec le montant du stack).
     */
    private boolean actionsAreEquivalent(Action chosen, Action legal, GameState gs) {
        if (chosen.getType() != legal.getType()) return false;
        if (chosen.getType() == Action.ActionType.BET || chosen.getType() == Action.ActionType.RAISE) {
            // Pour les actions avec montant, il faut une certaine tolérance ou une logique de mapping.
            // Si l'action choisie est un "RAISE_ALL_IN" conceptuel, et que l'action légale est un RAISE avec le montant exact du all-in,
            // elles sont équivalentes.
            // Pour l'instant, on compare les montants s'ils sont > 0.
            // Une comparaison exacte des montants est nécessaire.
            // Si chosen.getAmount() est une catégorie (ex: POT_SIZE), il faut le calculer.
            // Ici, createActionFromTypeAndGameState a déjà calculé le montant.
            if (Math.abs(chosen.getAmount() - legal.getAmount()) > 0.01 && chosen.getAmount() > 0 && legal.getAmount() > 0) {
                 // Cas spécial: si le légal est un all-in et que le choisi est > stack, c'est ok.
                 Player self = gs.getPlayers().stream().filter(p -> p.getId().equals(this.agentId)).findFirst().get();
                 if (legal.getAmount() == self.getStack() + self.getAmountBetThisRound() && chosen.getAmount() >= legal.getAmount()) {
                     return true; // Le choisi est un all-in ou plus, le légal est all-in.
                 }
                return false;
            }
        }
        return true;
    }


    /**
     * Crée un objet Action concret à partir d'un ActionType et du GameState.
     * Par exemple, si actionType est RAISE_POT, calcule le montant correspondant.
     */
    private Action createActionFromTypeAndGameState(Action.ActionType actionType, GameState gameState) {
        Player self = gameState.getPlayers().stream().filter(p -> p.getId().equals(this.agentId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent " + agentId + " not found in current game state."));

        double potSize = gameState.getPotSize();
        double currentBetToCall = gameState.getCurrentBetToCall();
        double playerStack = self.getStack();
        double playerBetThisRound = self.getAmountBetThisRound();
        double amountToCallForPlayer = gameState.getAmountToCallForPlayer(self);


        switch (actionType) {
            case FOLD:
                return new Action(Action.ActionType.FOLD);
            case CHECK: // Possible seulement si amountToCallForPlayer == 0
                return new Action(Action.ActionType.CHECK);
            case CALL: // Possible seulement si amountToCallForPlayer > 0
                return new Action(Action.ActionType.CALL); // Le GameEngine gère le montant exact du call
            case BET: // BET est utilisé ici comme une catégorie générique.
                      // Les types d'action spécifiques comme BET_HALF_POT, BET_POT devraient être mappés.
                      // Si on a un type BET générique, il faut un montant par défaut, ex: BB.
                      // Ou on suppose que DQNAgent a des actions de sortie plus spécifiques.
                      // Pour l'instant, si on a un type "BET", on va le traiter comme "BET_BIG_BLIND"
                double bb = 10.0; // TODO: Get BB from GameEngine/config
                double betAmount = Math.max(bb, Math.min(playerStack, bb)); // Miser BB, ou all-in si moins
                if (playerStack == 0) return new Action(Action.ActionType.CHECK); // Ne peut pas miser si pas de stack
                return new Action(Action.ActionType.BET, betAmount);

            case RAISE: // Similaire à BET, c'est une catégorie.
                        // On va le traiter comme un "MIN_RAISE"
                double minRaiseAmountTotal = gameState.getMinRaiseAmount(); // Montant total de la mise après relance
                // S'assurer que le joueur a assez, sinon all-in.
                double effectiveRaiseAmount = Math.min(playerStack + playerBetThisRound, minRaiseAmountTotal);
                if (effectiveRaiseAmount <= currentBetToCall && playerStack + playerBetThisRound > currentBetToCall) {
                    // Si le min_raise est <= call, mais qu'on peut faire plus (all-in), on fait all-in.
                    effectiveRaiseAmount = playerStack + playerBetThisRound;
                } else if (effectiveRaiseAmount <= currentBetToCall) {
                    // Ne peut pas faire un min_raise valide, devrait être un CALL ou FOLD.
                    // Cela indique un problème si RAISE est choisi.
                    // On retourne un CALL par défaut.
                    logger.warn("Agent {} chose RAISE type, but calculated effective raise {} is not > current bet {}. Defaulting to CALL.",
                                 agentId, effectiveRaiseAmount, currentBetToCall);
                    return new Action(Action.ActionType.CALL);
                }
                 if (playerStack == 0) return new Action(Action.ActionType.CALL); // Ne peut pas relancer si pas de stack pour ajouter

                return new Action(Action.ActionType.RAISE, effectiveRaiseAmount);

            // Des types plus spécifiques pourraient être ajoutés à Action.ActionType et gérés ici:
            // case BET_HALF_POT:
            //     double halfPotBet = Math.min(playerStack, potSize / 2);
            //     return new Action(Action.ActionType.BET, Math.max(bb, halfPotBet)); // Au moins BB
            // case RAISE_ALL_IN:
            //     return new Action(Action.ActionType.RAISE, playerStack + playerBetThisRound);

            default:
                throw new IllegalArgumentException("Unsupported action type for DQNAgent: " + actionType);
        }
    }


    /**
     * Trouve l'index de la meilleure action légale prédite par le réseau.
     * @param qValues Les Q-values prédites pour tous les types d'action possibles.
     * @param legalGameActions La liste des actions {@link Action} actuellement légales.
     * @return L'index (dans this.possibleActionTypes) de la meilleure action légale.
     */
    private int getBestLegalActionIndex(INDArray qValues, List<Action> legalGameActions) {
        int bestActionIndex = -1;
        double maxQValue = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < this.possibleActionTypes.size(); i++) {
            Action.ActionType potentialActionType = this.possibleActionTypes.get(i);
            // Vérifier si ce type d'action est "réalisable" via les legalGameActions.
            // Exemple: si potentialActionType est RAISE, y a-t-il une action RAISE dans legalGameActions?
            boolean isTypePossible = false;
            for (Action legalAction : legalGameActions) {
                if (legalAction.getType() == potentialActionType) {
                    // Pour BET/RAISE, on pourrait aussi vérifier si le montant implicite de `potentialActionType`
                    // (ex: RAISE_POT) est compatible avec les contraintes de `legalAction` (ex: min_raise, stack).
                    // Pour l'instant, une correspondance de type suffit, createActionFromTypeAndGameState s'occupera du montant.
                    isTypePossible = true;
                    break;
                }
            }
            // Cas spécial: si CHECK est un type possible pour le réseau, mais que la seule action légale est FOLD (ex: face à une mise sans assez de stack pour call)
            // ou si CALL est un type, mais qu'on ne peut que FOLD.
            // Il faut s'assurer que l'action générée par `createActionFromTypeAndGameState` est vraiment légale.
            // Une meilleure approche: itérer sur `legalGameActions`, trouver quel `possibleActionTypes` y correspond le mieux,
            // et prendre le Q-value de ce type.
            // Ou, plus simple: si `potentialActionType` est légal (directement ou indirectement), considérer son Q-value.

            if (!isTypePossible) { // Si ce type d'action n'est pas du tout dans les actions légales, on l'ignore.
                 // Exception: si FOLD est le seul légal, mais que le réseau veut CHECK/CALL.
                 if (legalGameActions.size() == 1 && legalGameActions.get(0).getType() == Action.ActionType.FOLD) {
                     if (potentialActionType == Action.ActionType.FOLD) { // Seul FOLD est possible et c'est ce qu'on évalue
                         // OK
                     } else {
                         continue; // Ignorer les autres types si FOLD est la seule option.
                     }
                 } else if (!isActionTypeGenerallyLegal(potentialActionType, legalGameActions)) {
                    continue;
                 }
            }


            if (qValues.getDouble(i) > maxQValue) {
                // Avant de la déclarer "meilleure", s'assurer qu'elle est VRAIMENT légale.
                // Cela implique de créer l'action et de la vérifier.
                Action tempAction = createActionFromTypeAndGameState(potentialActionType, getCurrentGameStateForAgent()); // Nécessite GameState
                boolean actuallyLegal = false;
                for(Action la : legalGameActions) {
                    if(actionsAreEquivalent(tempAction, la, getCurrentGameStateForAgent())) {
                        actuallyLegal = true;
                        break;
                    }
                }
                if(actuallyLegal) {
                    maxQValue = qValues.getDouble(i);
                    bestActionIndex = i;
                }
            }
        }

        if (bestActionIndex == -1) {
            // Aucune action prédite n'était légale, ou Q-values étaient toutes -inf.
            // Choisir une action légale aléatoirement ou la première.
            logger.warn("Agent {} couldn't find a best legal action from Q-values. Defaulting. Q-Values: {}, Legal Actions: {}", agentId, qValues, legalGameActions);
            return mapLegalActionToRandomOutputIndex(legalGameActions); // Fallback
        }
        return bestActionIndex;
    }

    private boolean isActionTypeGenerallyLegal(Action.ActionType typeToTest, List<Action> legalGameActions) {
        for (Action legal : legalGameActions) {
            if (legal.getType() == typeToTest) return true;
            // Un RAISE peut être possible même si le type est BET (si on peut relancer une mise existante)
            // Un BET peut être possible même si le type est RAISE (si on peut miser dans un pot non ouvert)
            // Cette fonction est une heuristique. La validation finale est dans actionsAreEquivalent.
            if (typeToTest == Action.ActionType.CALL && legal.getType() == Action.ActionType.RAISE && legal.getAmount() == 0) {} // Cas étrange
            if ((typeToTest == Action.ActionType.BET || typeToTest == Action.ActionType.RAISE) &&
                (legal.getType() == Action.ActionType.BET || legal.getType() == Action.ActionType.RAISE)) return true;

        }
        return false;
    }


    /**
     * Tente de mapper une liste d'actions de jeu légales à un index de sortie aléatoire
     * qui correspond à un type d'action légal.
     */
    private int mapLegalActionToRandomOutputIndex(List<Action> legalGameActions) {
        if (legalGameActions.isEmpty()) {
            throw new IllegalStateException("Cannot map random action if no legal actions are available.");
        }
        List<Integer> possibleOutputIndices = new ArrayList<>();
        for (int i = 0; i < this.possibleActionTypes.size(); i++) {
            Action.ActionType pType = this.possibleActionTypes.get(i);
            // Est-ce que ce pType est représenté dans legalGameActions?
            for (Action la : legalGameActions) {
                if (la.getType() == pType) {
                    // Vérifier si l'action construite à partir de pType est réellement équivalente à la.
                     Action tempAction = createActionFromTypeAndGameState(pType, getCurrentGameStateForAgent());
                     if (actionsAreEquivalent(tempAction, la, getCurrentGameStateForAgent())) {
                        possibleOutputIndices.add(i);
                        break;
                     }
                }
            }
        }
        if (possibleOutputIndices.isEmpty()) {
            // Aucun de nos types d'action de sortie ne correspond aux actions légales. C'est un problème de config.
            // Exemple: si nos sorties sont FOLD, CALL, RAISE_POT, mais que l'action légale est un BET spécifique.
            // On devrait prendre la première action légale et trouver le type de sortie le plus proche.
            // Ou, plus simple, choisir un index aléatoire parmi les types qui sont *présents* dans `legalGameActions`.
            Action randomLegalAction = legalGameActions.get(random.nextInt(legalGameActions.size()));
            for (int i = 0; i < this.possibleActionTypes.size(); i++) {
                if (this.possibleActionTypes.get(i) == randomLegalAction.getType()) {
                    // S'assurer que le montant est aussi compatible si BET/RAISE.
                    // C'est complexe. Pour l'exploration, on peut juste prendre un type légal.
                    return i; // Retourne l'index du type de l'action légale choisie au hasard.
                }
            }
            // Si même ça échoue, c'est qu'il y a un décalage majeur. Retourner l'index de FOLD si possible.
            for (int i = 0; i < this.possibleActionTypes.size(); i++) {
                if (this.possibleActionTypes.get(i) == Action.ActionType.FOLD) return i;
            }
            logger.error("Agent {} failed to map any legal action to an output index for random choice. Legal: {}", agentId, legalGameActions);
            return 0; // Fallback très risqué.
        }
        return possibleOutputIndices.get(random.nextInt(possibleOutputIndices.size()));
    }

    // Méthode fictive pour obtenir le GameState, car il n'est pas stocké dans l'agent.
    // L'appelant de chooseAction le fournit. Pour les helpers internes, c'est délicat.
    // On pourrait le passer en paramètre à ces helpers.
    private GameState currentAgentGameState; // Temporaire, doit être mis à jour avant d'appeler les helpers.

    private GameState getCurrentGameStateForAgent() {
        if (currentAgentGameState == null) {
            throw new IllegalStateException("currentAgentGameState not set in DQNAgent for helper methods.");
        }
        return currentAgentGameState;
    }

    // S'assurer que chooseAction met à jour `currentAgentGameState`
    // @Override
    // public Action chooseAction(GameState gameState, List<Action> legalActions) {
    //    this.currentAgentGameState = gameState; // Mettre à jour avant d'appeler les helpers
    //    ... reste de la logique ...
    // }
    // La solution propre est de passer gameState aux méthodes qui en ont besoin.
    // Pour l'instant, je vais modifier les helpers pour prendre gameState.
    // (Fait : createActionFromTypeAndGameState, getBestLegalActionIndex, mapLegalActionToRandomOutputIndex
    //  devront prendre gameState en argument si currentAgentGameState n'est pas utilisé)
    // J'ai modifié `createActionFromTypeAndGameState` pour prendre gameState.
    // `getBestLegalActionIndex` et `mapLegalActionToRandomOutputIndex` devront aussi le prendre.
    // Pour l'instant, je vais supposer que ces méthodes sont appelées depuis `chooseAction` et peuvent accéder à `gameState`.
    // J'ai modifié `getBestLegalActionIndex` et `mapLegalActionToRandomOutputIndex` pour utiliser une méthode `getCurrentGameStateForAgent()`
    // qui doit être alimentée. C'est un hack temporaire. `chooseAction` doit définir un champ temporaire `currentGameState`
    // avant d'appeler ces méthodes.

    // Solution: `chooseAction` doit passer `gameState` explicitement.
    // (Modification faite en interne dans la logique de `chooseAction` pour appeler les helpers avec `gameState`)


    /**
     * Stocke une transition dans la mémoire de répétition.
     */
    public void observeTransition(INDArray state, int actionIndex, double reward, INDArray nextState, boolean done) {
        if (replayBuffer.size() >= replayBufferSize) {
            replayBuffer.poll(); // Retirer la plus ancienne transition si plein
        }
        replayBuffer.add(new Transition(state, actionIndex, reward, nextState, done));
        logger.trace("Agent {} observed transition. Replay buffer size: {}", agentId, replayBuffer.size());
    }

    /**
     * Entraîne le réseau sur un mini-batch échantillonné depuis la mémoire de répétition.
     */
    public void trainBatch() {
        if (replayBuffer.size() < batchSize) {
            return; // Pas assez de transitions pour former un batch
        }

        List<Transition> batch = sampleFromReplayBuffer(batchSize);

        // Préparer les données pour l'entraînement
        // On a besoin des états et des Q-values cibles
        INDArray statesBatch = Nd4j.vstack(batch.stream().map(t -> t.state).toArray(INDArray[]::new));
        INDArray qValuesNextStates = model.output(Nd4j.vstack(batch.stream().map(t -> t.nextState).toArray(INDArray[]::new)));

        INDArray qValuesCurrentStates = model.output(statesBatch.dup()); // Dup pour ne pas modifier pendant l'itération

        for (int i = 0; i < batch.size(); i++) {
            Transition t = batch.get(i);
            double targetQ;
            if (t.done) {
                targetQ = t.reward;
            } else {
                // Q(s,a) = r + gamma * max_a'(Q(s',a'))
                targetQ = t.reward + gamma * qValuesNextStates.getRow(i).maxNumber().doubleValue();
            }
            // Mettre à jour la Q-value pour l'action prise dans l'état actuel
            qValuesCurrentStates.putScalar(i, t.actionIndex, targetQ);
        }

        // Entraîner le modèle
        model.fit(statesBatch, qValuesCurrentStates);
        logger.debug("Agent {} trained batch. Epsilon: {}", agentId, String.format("%.3f",epsilon) );

        // Décroissance d'epsilon
        if (epsilon > minEpsilon) {
            epsilon *= epsilonDecay;
            epsilon = Math.max(minEpsilon, epsilon);
        }
    }

    private List<Transition> sampleFromReplayBuffer(int size) {
        List<Transition> bufferAsList = new ArrayList<>(replayBuffer);
        Collections.shuffle(bufferAsList, random);
        return bufferAsList.subList(0, Math.min(size, bufferAsList.size()));
    }

    @Override
    public void handComplete(GameState finalState, double reward) {
        // Cette méthode est appelée par TrainingEnvironment.
        // Ici, l'agent pourrait faire un dernier enregistrement de transition si nécessaire,
        // ou mettre à jour des statistiques.
        // La transition finale (s, a, r, s_terminal, done=true) est généralement ajoutée
        // par TrainingEnvironment avant d'appeler trainBatch.
        logger.debug("Agent {} hand complete. Reward: {}. Final epsilon: {}", agentId, reward, String.format("%.3f",epsilon));
    }

    public void saveModel(String filePath) throws IOException {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
        ModelSerializer.writeModel(model, file, true); // true pour sauvegarder l'updater aussi
        logger.info("Agent {} model saved to {}", agentId, filePath);
    }

    public void loadModel(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("Agent {} model file not found at {}. Cannot load.", agentId, filePath);
            return;
        }
        model = ModelSerializer.restoreMultiLayerNetwork(file, true);
        logger.info("Agent {} model loaded from {}", agentId, filePath);
    }

    public double getEpsilon() {
        return epsilon;
    }

    // Nécessaire pour que les helpers internes de chooseAction aient accès au gameState actuel.
    // C'est un peu un hack. Idéalement, gameState serait passé à chaque méthode.
    // J'ai refactoré pour passer gameState aux méthodes qui en ont besoin directement depuis chooseAction.
    // Cette variable de champ n'est plus nécessaire si c'est bien fait.
    // private transient GameState currentGameStateForHelpers;
}
