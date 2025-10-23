package ti4.service.draft.orchestrators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.menus.BagDraftSettings;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.draft.*;
import ti4.service.draft.BagDraftMessageService.DraftChoiceInfo;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;

/**
 * This draft orchestrator implements a prvate bag draft.
 * Players are receive a roughly even split of the available draft choices,
 * sent to their private channels. They make an appropriate number of picks,
 * then pass the remaining choices to the next player in order.
 */
public class BagDraftOrchestrator extends DraftOrchestrator {
    /**
     * The per-player state for PrivateBagDraftOrchestrator.
     */
    public static class State extends OrchestratorState {
        @Getter
        @Setter
        private int orderIndex;

        @Getter
        private List<String> baggedDraftChoices = new ArrayList<>();

        @Getter
        private List<String> pendingPicks = new ArrayList<>();

        @Getter
        @Setter
        private boolean picksLocked;
    }

    @Getter
    @Setter
    private int picksFromFirstBag;

    @Getter
    @Setter
    private int picksFromBags;

    @Getter
    @Setter
    private int currentBagRound;

    public void initialize(
            DraftManager draftManager, List<String> presetPlayerOrder, int picksFromBags, int picksFromFirstBag) {
        if (picksFromBags < 1) {
            throw new IllegalArgumentException("picksFromBags must be at least 1");
        }
        if (picksFromFirstBag < 1) {
            throw new IllegalArgumentException("picksFromFirstBag must be at least 1");
        }

        initializePlayerStates(draftManager);

        if (presetPlayerOrder != null) {
            setDraftOrder(draftManager, presetPlayerOrder);
        } else {
            List<String> shuffledPlayers =
                    new ArrayList<>(draftManager.getPlayerStates().keySet());
            Collections.shuffle(shuffledPlayers);
            setDraftOrder(draftManager, shuffledPlayers);
        }

        setPicksFromBags(picksFromBags);
        setPicksFromFirstBag(picksFromFirstBag);
        cleanUnknownChoices(draftManager);
        addMissingChoices(draftManager);
        currentBagRound = 1;
    }

    @Override
    public void initializePlayerStates(DraftPlayerManager draftManager) {
        for (PlayerDraftState playerState : draftManager.getPlayerStates().values()) {
            if (playerState.getOrchestratorState() == null || !(playerState.getOrchestratorState() instanceof State)) {
                State orchestratorState = new State();
                playerState.setOrchestratorState(orchestratorState);
            }
        }
    }

    @Override
    public void sendDraftButtons(DraftManager draftManager) {
        Game game = draftManager.getGame();
        // No active player during bag draft
        game.updateActivePlayer(null);

        for (String playerUserId : draftManager.getPlayerUserIds()) {
            Player player = game.getPlayer(playerUserId);
            if (player == null) {
                BotLogger.warning(new LogOrigin(game), "Cannot find drafting player in game: " + playerUserId);
                continue;
            }

            // Get the player's bag channel, creating it if necessary
            MessageChannel bagChannel = BagChannelService.findExistingBagChannel(game, player);
            if (bagChannel == null) {
                bagChannel = BagChannelService.regenerateBagChannel(game, player);
            }

            List<DraftChoiceInfo> draftChoiceInfos = getDraftChoiceInfosForPlayer(draftManager, playerUserId);

            Button submitDraftChoices = getSubmitPicksButton();
            if (!canSubmitPicks(draftManager, playerUserId)) {
                submitDraftChoices = submitDraftChoices.withDisabled(true);
            }

            BagDraftMessageService.sendPlayerDraftInfo(
                    draftManager, playerUserId, draftChoiceInfos, List.of(submitDraftChoices));
        }
    }

    @Override
    public String applyDraftChoice(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            String playerUserId,
            DraftChoice choice,
            CommandSource source) {

        // Ensure no one else has picked this choice
        if (draftManager
                        .getPlayersWithChoiceKey(choice.getType(), choice.getChoiceKey())
                        .size()
                > 0) {
            return DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + "That choice has already been taken.";
        }

        PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
        if (playerState == null) {
            return DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + "You are not part of this draft.";
        }
        State orchestratorState = (State) playerState.getOrchestratorState();
        if (orchestratorState == null) {
            return "Error: Your draft state is invalid.";
        }

        // Persist the choice in Player State, as a pending choice until it's locked.
        orchestratorState.getPendingPicks().add(choice.getChoiceKey());

        List<DraftChoiceInfo> draftChoiceInfos = getDraftChoiceInfosForPlayer(draftManager, playerUserId);

        Button submitDraftChoices = getSubmitPicksButton();
        if (!canSubmitPicks(draftManager, playerUserId)) {
            submitDraftChoices = submitDraftChoices.withDisabled(true);
        }

        BagDraftMessageService.editPlayerDraftInfo(
                event, draftManager, playerUserId, draftChoiceInfos, List.of(submitDraftChoices));

        return null;
    }

    @Override
    public String save() {
        StringBuilder sb = new StringBuilder();
        sb.append("1stPick").append(picksFromFirstBag).append(SAVE_SEPARATOR);
        sb.append("pickPerBag").append(picksFromBags).append(SAVE_SEPARATOR);
        sb.append("currentRound").append(currentBagRound);
        return sb.toString();
    }

    @Override
    public void load(String data) {
        String[] tokens = data.split(DraftOrchestrator.SAVE_SEPARATOR, 3);
        if (tokens.length != 3) {
            throw new IllegalArgumentException("Invalid data for PrivateBagDraftOrchestrator: " + data);
        }
        picksFromFirstBag = Integer.parseInt(tokens[0].substring("1stPick".length()));
        picksFromBags = Integer.parseInt(tokens[1].substring("pickPerBag".length()));
        currentBagRound = Integer.parseInt(tokens[2].substring("currentRound".length()));
    }

    @Override
    public String savePlayerState(OrchestratorState state) {
        if (!(state instanceof State)) {
            throw new IllegalArgumentException("Invalid state type for PublicSnakeDraftOrchestrator: "
                    + state.getClass().getSimpleName());
        }
        State pbdState = (State) state;
        StringBuilder sb = new StringBuilder();
        sb.append("o").append(pbdState.getOrderIndex()).append(DraftSaveService.DATA_SEPARATOR);
        for (String choiceKey : pbdState.getBaggedDraftChoices()) {
            sb.append("b").append(choiceKey).append(DraftSaveService.DATA_SEPARATOR);
        }
        for (String choiceKey : pbdState.getPendingPicks()) {
            sb.append("p").append(choiceKey).append(DraftSaveService.DATA_SEPARATOR);
        }
        sb.append("l").append(pbdState.isPicksLocked() ? "1" : "0");
        return sb.toString();
    }

    @Override
    public OrchestratorState loadPlayerState(String data) {
        String[] tokens = data.split(DraftSaveService.DATA_SEPARATOR);
        State state = new State();
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            char prefix = token.charAt(0);
            String value = token.substring(1);
            switch (prefix) {
                case 'o':
                    state.setOrderIndex(Integer.parseInt(value));
                    break;
                case 'b':
                    state.getBaggedDraftChoices().add(value);
                    break;
                case 'p':
                    state.getPendingPicks().add(value);
                    break;
                case 'l':
                    state.setPicksLocked(value.equals("1"));
                    break;
            }
        }
        return state;
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (currentBagRound < 1) {
            return "Current bag round should be a positive integer: " + currentBagRound;
        }
        if (picksFromBags < 1) {
            return "Picks from bags should be a positive integer: " + picksFromBags;
        }
        if (picksFromFirstBag < 1) {
            return "Picks from first bag should be a positive integer: " + picksFromFirstBag;
        }

        Set<String> allValidDraftChoices = new HashSet<>();
        for (Draftable draftable : draftManager.getDraftables()) {
            allValidDraftChoices.addAll(draftable.getAllDraftChoiceKeys());
        }

        // Ensure all players have a valid State, with unique and valid order indices.
        Set<Integer> distinctOrderIndices = new HashSet<>();
        Set<String> distinctBaggedChoices = new HashSet<>();
        Set<String> distinctPendingPicks = new HashSet<>();
        for (Map.Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerState = entry.getValue();
            OrchestratorState orchestratorState = playerState.getOrchestratorState();
            if (orchestratorState == null || !(orchestratorState instanceof State)) {
                return "Player " + playerUserId + " has invalid draft state (missing or weird type).";
            }
            State state = (State) orchestratorState;
            if (state.getOrderIndex() < 0
                    || state.getOrderIndex() >= draftManager.getPlayerStates().size()) {
                return "Player " + playerUserId + " has out of bounds order index: " + state.getOrderIndex() + ".";
            }
            if (distinctOrderIndices.contains(state.getOrderIndex())) {
                return "Duplicate order index found: " + state.getOrderIndex() + ".";
            }
            distinctOrderIndices.add(state.getOrderIndex());

            for (String choiceKey : state.getBaggedDraftChoices()) {
                if (distinctBaggedChoices.contains(choiceKey)) {
                    return "Duplicate bagged draft choice found across players: " + choiceKey;
                }

                if (!allValidDraftChoices.contains(choiceKey)) {
                    return "Player " + playerUserId + " has unknown bagged draft choice: " + choiceKey;
                }

                distinctBaggedChoices.add(choiceKey);
            }

            for (String choiceKey : state.getPendingPicks()) {
                if (distinctPendingPicks.contains(choiceKey)) {
                    return "Duplicate pending draft choice found across players: " + choiceKey;
                }

                if (!allValidDraftChoices.contains(choiceKey)) {
                    return "Player " + playerUserId + " has unknown pending draft choice: " + choiceKey;
                }

                distinctPendingPicks.add(choiceKey);
            }
        }
        if (distinctOrderIndices.size() != draftManager.getPlayerStates().size()) {
            return "Player order indices are not unique. Fix it with `/draft public_snake set_order`.";
        }

        return null;
    }

    @Override
    public String getButtonPrefix() {
        return "pbd_";
    }

    private Button getSubmitPicksButton() {
        return Buttons.gray(
                DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + getButtonPrefix() + "lockpicks", "Submit picks");
    }

    @Override
    public String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        if (buttonId.equals("lockpicks")) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            State orchestratorState = (State) playerState.getOrchestratorState();
            if (orchestratorState.isPicksLocked()) {
                return DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + "Your picks are already submitted.";
            }
            orchestratorState.setPicksLocked(true);

            // Update draft info message
            List<State> bagStates = getBagStates(draftManager);
            int playersLockedPicks =
                    bagStates.stream().filter(State::isPicksLocked).toList().size();
            updateRoundInfo(
                    currentBagRound,
                    playersLockedPicks,
                    draftManager.getPlayerStates().size(),
                    draftManager.getGame());

            // If that's the last player, advance the bag draft
            tryAdvanceBagDraft(draftManager);

            return null;
        }

        return "Unknown button action: " + buttonId;
    }

    @Override
    public String whatsStoppingDraftEnd(DraftManager draftManager) {
        // This draft mode doesn't impose any additional requirements beyond what the
        // draftables require.
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        // This draft mode doesn't do any player setup itself, the draftables handle
        // everything.
        return null;
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        if (menu == null || !(menu instanceof DraftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
        DraftSystemSettings draftSystemSettings = (DraftSystemSettings) menu;
        Game game = draftSystemSettings.getGame();
        if (game == null) {
            return "Error: Could not find game instance.";
        }
        BagDraftSettings bagDraftSettings = draftSystemSettings.getBagDraftSettings();
        if (bagDraftSettings.getPresetDraftOrder().isVal()) {
            List<String> presetOrder = bagDraftSettings.getOrderedPlayerIds();
            if (presetOrder == null
                    || presetOrder.size()
                            != draftSystemSettings.getPlayerUserIds().size()) {
                return "Error: Preset draft order is enabled, but the order is incomplete.";
            }

            initialize(
                    game.getDraftManager(),
                    presetOrder,
                    bagDraftSettings.getPicksPerRound().getVal(),
                    bagDraftSettings.getPicksOnFirstRound().getVal());
        } else {
            initialize(
                    game.getDraftManager(),
                    null,
                    bagDraftSettings.getPicksPerRound().getVal(),
                    bagDraftSettings.getPicksOnFirstRound().getVal());
        }

        return null;
    }

    public void setDraftOrder(DraftPlayerManager draftManager, List<String> playerOrder) {
        if (playerOrder.size() != draftManager.getPlayerStates().size()) {
            throw new IllegalArgumentException("Player order size "
                    + playerOrder.size()
                    + " does not match number of players in draft "
                    + draftManager.getPlayerStates().size());
        }
        Set<String> distinctPlayers = new HashSet<>(playerOrder);
        if (distinctPlayers.size() != playerOrder.size()) {
            throw new IllegalArgumentException("Player order contains duplicate players");
        }
        for (String playerUserId : playerOrder) {
            if (!draftManager.getPlayerStates().containsKey(playerUserId)) {
                throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
            }
        }

        int orderIndex = 0;
        for (String playerUserId : playerOrder) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            State orchestratorState = (State) playerState.getOrchestratorState();
            orchestratorState.setOrderIndex(orderIndex++);
        }
    }

    public void setPlayerPosition(DraftPlayerManager draftManager, String playerUserId, int position) {
        if (position < 1 || position > draftManager.getPlayerStates().size()) {
            throw new IllegalArgumentException("Position " + position + " is out of bounds for draft with "
                    + draftManager.getPlayerStates().size()
                    + " players");
        }
        if (!draftManager.getPlayerStates().containsKey(playerUserId)) {
            throw new IllegalArgumentException("Player " + playerUserId + " is not in the draft");
        }

        State targetState =
                (State) draftManager.getPlayerStates().get(playerUserId).getOrchestratorState();
        int currentIndex = targetState.getOrderIndex();
        int targetIndex = position - 1;
        if (currentIndex == targetIndex) {
            // No change
            return;
        }

        // Finally set the target player's index
        targetState.setOrderIndex(targetIndex);
    }

    private List<String> getDraftOrder(DraftManager draftManager) {
        List<String> playerOrder = new ArrayList<>();
        int numPlayers = draftManager.getPlayerStates().size();
        for (int i = 0; i < numPlayers; i++) {
            for (String playerUserId : draftManager.getPlayerStates().keySet()) {
                State orchestratorState =
                        (State) draftManager.getPlayerStates().get(playerUserId).getOrchestratorState();
                if (orchestratorState.getOrderIndex() == i) {
                    playerOrder.add(playerUserId);
                    break;
                }
            }
        }
        return playerOrder;
    }

    private void cleanUnknownChoices(DraftManager draftManager) {
        Set<String> allValidChoiceKeys = new HashSet<>();
        for (Draftable draftable : draftManager.getDraftables()) {
            allValidChoiceKeys.addAll(draftable.getAllDraftChoiceKeys());
        }

        for (PlayerDraftState playerState : draftManager.getPlayerStates().values()) {
            State orchestratorState = (State) playerState.getOrchestratorState();
            List<String> cleanedBaggedChoices = new ArrayList<>();
            for (String choiceKey : orchestratorState.getBaggedDraftChoices()) {
                if (allValidChoiceKeys.contains(choiceKey)) {
                    cleanedBaggedChoices.add(choiceKey);
                }
            }
            orchestratorState.getBaggedDraftChoices().clear();
            orchestratorState.getBaggedDraftChoices().addAll(cleanedBaggedChoices);
        }
    }

    private void addMissingChoices(DraftManager draftManager) {
        // Map<DraftableType, List<String>> allChoicesByType = new HashMap<>();
        for (Draftable draftable : draftManager.getDraftables()) {
            // Get choices for this type
            Set<String> draftChoices = draftable.getAllDraftChoiceKeys();

            // Remove draft choices already assigned to players
            for (PlayerDraftState playerState : draftManager.getPlayerStates().values()) {
                State orchestratorState = (State) playerState.getOrchestratorState();
                for (String choiceKey : orchestratorState.getBaggedDraftChoices()) {
                    draftChoices.remove(choiceKey);
                }
            }

            if (draftChoices.isEmpty()) {
                continue;
            }

            // Distribute remaining choices to players with the fewest of this type
            List<String> remainingChoices = new LinkedList<>(draftChoices);
            while (!remainingChoices.isEmpty()) {
                String curChoice = remainingChoices.removeFirst();
                PlayerDraftState targetPlayer = null;
                int fewestChoices = Integer.MAX_VALUE;
                for (String playerUserId : draftManager.getPlayerUserIds()) {
                    int numChoices = draftManager
                            .getPlayerPicks(playerUserId, draftable.getType())
                            .size();
                    if (numChoices < fewestChoices) {
                        fewestChoices = numChoices;
                        targetPlayer = draftManager.getPlayerStates().get(playerUserId);
                        ;
                    }
                }
                if (targetPlayer == null) {
                    // Should not happen
                    break;
                }
                State targetState = (State) targetPlayer.getOrchestratorState();
                targetState.getBaggedDraftChoices().add(curChoice);
            }
        }
    }

    private List<DraftChoiceInfo> getDraftChoiceInfosForPlayer(DraftManager draftManager, String playerUserId) {

        PlayerDraftState playerDraftState = draftManager.getPlayerStates().get(playerUserId);
        State orchestratorState = (State) playerDraftState.getOrchestratorState();
        List<String> baggedChoices = orchestratorState.getBaggedDraftChoices();

        // Visible choices are those in the bag, minus any already picked
        Set<String> visibleChoices = new HashSet<>(baggedChoices);
        draftManager.getPlayerStates().values().forEach(pState -> {
            pState.getPicks().values().stream()
                    .flatMap(picks -> picks.stream())
                    .map(DraftChoice::getChoiceKey)
                    .forEach(visibleChoices::remove);
        });

        // Pending picks are just anything noted as pending
        Set<String> pendingPicks = new HashSet<>(orchestratorState.getPendingPicks());

        // Legal picks are choices that the player could mark as pending
        Set<String> legalPicks = new HashSet<>(getPlayerLegalPicks(draftManager, playerUserId));

        // Collect choices into DraftChoiceInfo records, which include the choice
        // and info about what the player should see or be able to do with it.
        List<DraftChoiceInfo> draftChoiceInfos = draftManager.getDraftables().stream()
                .flatMap(draftable -> draftable.getAllDraftChoices().stream())
                .filter(dc -> visibleChoices.contains(dc.getChoiceKey()))
                .map(dc -> new DraftChoiceInfo(
                        dc,
                        visibleChoices.contains(dc.getChoiceKey()),
                        pendingPicks.contains(dc.getChoiceKey()),
                        legalPicks.contains(dc.getChoiceKey())))
                .toList();

        return draftChoiceInfos;
    }

    private boolean canSubmitPicks(DraftManager draftManager, String playerUserId) {
        PlayerDraftState playerDraftState = draftManager.getPlayerStates().get(playerUserId);
        State bagState = (State) playerDraftState.getOrchestratorState();

        // If all legal picks have been made, allow submission
        List<String> legalPicks = getPlayerLegalPicks(draftManager, playerUserId);
        if (bagState.pendingPicks.size() >= legalPicks.size()) {
            return true;
        }

        // Otherwise, check if the required number of picks have been made
        int requiredPicks = (currentBagRound == 1) ? picksFromFirstBag : picksFromBags;
        return bagState.getPendingPicks().size() >= requiredPicks && !bagState.isPicksLocked();
    }

    private List<State> getBagStates(DraftManager draftManager) {
        List<State> bagStates = new ArrayList<>();
        List<String> playerOrder = getDraftOrder(draftManager);
        for (String playerUserId : playerOrder) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            State orchestratorState = (State) playerState.getOrchestratorState();
            bagStates.add(orchestratorState);
        }
        return bagStates;
    }

    private void tryAdvanceBagDraft(DraftManager draftManager) {
        // Check if all players have locked their picks,
        // or are otherwise unable to make more picks.
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            State bagState =
                    (State) draftManager.getPlayerStates().get(playerUserId).getOrchestratorState();

            // Check if picks are locked
            if (bagState.isPicksLocked()) {
                continue;
            }

            // Check if picks are possible
            List<String> legalPicks = getPlayerLegalPicks(draftManager, playerUserId);
            if (legalPicks.isEmpty()) {
                bagState.setPicksLocked(true);
                continue;
            }

            // If picks aren't locked and there are legal choices left, this round is unfinished.
            return;
        }

        advanceBagDraft(draftManager);
    }

    private void advanceBagDraft(DraftManager draftManager) {
        Map<String, DraftChoice> allDraftChoices = draftManager.getDraftables().stream()
                .flatMap(draftable -> draftable.getAllDraftChoices().stream())
                .collect(Collectors.toMap(DraftChoice::getChoiceKey, dc -> dc));

        applyAndClearPendingPicks(draftManager, allDraftChoices);

        // Get the bags in order
        List<String> playerOrder = getDraftOrder(draftManager);

        // Pass bags to the next player, and lock in any required picks. If no players have
        // legal choices, but there are still unpicked items, advance the bags again.
        for (int passNumber = 0; passNumber < playerOrder.size(); passNumber++) {
            List<String> choicesForNextBag = null;
            for (int i = playerOrder.size() - 1; i >= 0; i--) {
                String fromPlayerUserId = playerOrder.get(i);
                int nextPlayerIndex = (i + 1) % playerOrder.size();
                String toPlayerUserId = playerOrder.get(nextPlayerIndex);

                PlayerDraftState fromPlayerState =
                        draftManager.getPlayerStates().get(fromPlayerUserId);
                State fromBagState = (State) fromPlayerState.getOrchestratorState();

                PlayerDraftState toPlayerState = draftManager.getPlayerStates().get(toPlayerUserId);
                State toBagState = (State) toPlayerState.getOrchestratorState();

                // Pass the bag
                if (choicesForNextBag == null) {
                    choicesForNextBag = new ArrayList<>(toBagState.getBaggedDraftChoices());
                }
                toBagState.getBaggedDraftChoices().clear();
                toBagState.getBaggedDraftChoices().addAll(fromBagState.getBaggedDraftChoices());
                fromBagState.getBaggedDraftChoices().clear();
            }
        }

        // Then try to end the draft.
    }

    private void applyAndClearPendingPicks(DraftManager draftManager, Map<String, DraftChoice> allDraftChoices) {
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
            State bagState = (State) playerState.getOrchestratorState();

            // Apply picks
            for (String choiceKey : bagState.getPendingPicks()) {
                DraftChoice draftChoice = allDraftChoices.get(choiceKey);
                playerState.getPicks().putIfAbsent(draftChoice.getType(), new ArrayList<>());
                playerState.getPicks().get(draftChoice.getType()).add(draftChoice);
            }

            // Clear player state
            bagState.getPendingPicks().clear();
            bagState.setPicksLocked(false);
        }
    }

    private List<String> getPlayerLegalPicks(DraftManager draftManager, String playerUserId) {
        PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
        State bagState = (State) playerState.getOrchestratorState();
        Set<String> baggedDraftChoices =
                bagState.getBaggedDraftChoices().stream().collect(Collectors.toSet());
        List<String> legalPicks = new ArrayList<>();
        // A pick is illegal if:
        // - It's not in the current bag
        // - It's been picked
        // - It's already pending in any bag (hopefully not in another player's, but cover it anyway)
        // - Another pick of the same type is already pending in this bag (TODO: improve this?)
        // - The draftable itself rejects the player's ability to pick it (pick limit, pick conflict, etc.)
        for (Draftable draftable : draftManager.getDraftables()) {
            List<DraftChoice> draftChoices = draftable.getAllDraftChoices();

            // If any of this draftable's choices are pending picks, then
            // all choices of this type are illegal (only 1 pick of a type allowed at a time)
            // TODO: Handle pick legality w/ your pending picks in a smarter way
            boolean hasPendingPickOfThisType = false;
            for (DraftChoice choiceKey : draftChoices) {
                if (bagState.pendingPicks.contains(choiceKey.getChoiceKey())) {
                    hasPendingPickOfThisType = true;
                    break;
                }
            }
            if (hasPendingPickOfThisType) {
                continue;
            }

            // Check the legality of each chice
            for (DraftChoice draftChoice : draftChoices) {
                String choiceKey = draftChoice.getChoiceKey();
                if (!baggedDraftChoices.contains(choiceKey)) {
                    continue;
                }
                if (draftManager
                                .getPlayersWithChoiceKey(draftable.getType(), choiceKey)
                                .size()
                        > 0) {
                    continue;
                }
                if (bagState.pendingPicks.contains(choiceKey)) {
                    continue;
                }
                if (draftable.isValidDraftChoice(draftManager, playerUserId, draftChoice) != null) {
                    continue;
                }
                // If we reach this point, the pick is legal
                legalPicks.add(choiceKey);
            }
        }
        return legalPicks;
    }

    private void sendRoundInfo(int roundNumber, int playersLockedPicks, int totalPlayers, Game game) {
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                getRoundInfoPrefix(roundNumber) + "Players submitted picks: " + playersLockedPicks + "/"
                        + totalPlayers);
    }

    private String getRoundInfoPrefix(int roundNumber) {
        return "Bag Draft - Round " + roundNumber + ": ";
    }

    private void updateRoundInfo(int roundNumber, int playersLockedPicks, int totalPlayers, Game game) {
        game.getMainGameChannel().getHistory().retrievePast(10).queue(messageHistory -> {
            for (var message : messageHistory) {
                if (message.getAuthor().isBot()
                        && message.getContentRaw().startsWith(getRoundInfoPrefix(roundNumber))) {
                    message.editMessage(getRoundInfoPrefix(roundNumber) + "Players submitted picks: "
                                    + playersLockedPicks + "/" + totalPlayers)
                            .queue();
                    return;
                }
            }
            // No existing message found, send a new one
            sendRoundInfo(roundNumber, playersLockedPicks, totalPlayers, game);
        });
    }
}
