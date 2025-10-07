package ti4.service.draft.orchestrators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.PublicSnakeDraftSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.DraftPlayerManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.OrchestratorState;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.draft.PublicDraftInfoService;

/**
 * This draft orchestrator implements a prvate bag draft.
 * Players are receive a roughly even split of the available draft choices,
 * sent to their private channels. They make an appropriate number of picks,
 * then pass the remaining choices to the next player in order. 
 */
public class PrivateBagDraftOrchestrator extends DraftOrchestrator {
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
    

    private void cleanUnknownChoices(DraftManager draftManager) {

    }

    private void addMissingChoices(DraftManager draftManager) {

    }

    public void initialize(DraftManager draftManager, List<String> presetPlayerOrder, int picksFromBags, int picksFromFirstBag) {
        if(picksFromBags < 1) {
            throw new IllegalArgumentException("picksFromBags must be at least 1");
        }
        if(picksFromFirstBag < 1) {
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
        List<String> playerOrder = getDraftOrder(draftManager);
        String currentPlayerUserId = getCurrentPlayer(playerOrder);
        draftManager.getGame().setActivePlayerID(currentPlayerUserId);
        PublicDraftInfoService.send(
                draftManager,
                playerOrder,
                currentPlayerUserId,
                getNextPlayer(playerOrder),
                List.of(getReprintDraftButton()));
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
            //TODO: PEBKAC
            return "That choice has already been taken.";
        }

        PlayerDraftState playerState = draftManager.getPlayerStates().get(playerUserId);
        if (playerState == null) {
            //TODO: PEBKAC
            return "You are not part of this draft.";
        }
        State orchestratorState = (State) playerState.getOrchestratorState();
        if (orchestratorState == null) {
            return "Error: Your draft state is invalid.";
        }

        // Persist the choice in Player State, as a pending choice until it's locked.
        orchestratorState.getPendingPicks().add(choice.getChoiceKey());

        // Replace the button with an "unpick" button.
        if(event instanceof ButtonInteractionEvent buttonEvent) {
            Button unpickButton = makeUnpickButton(choice);
            MessageHelper.editButtonInMessage(buttonEvent, unpickButton);
        }

        // Delete buttons when they're picked.
        return DraftButtonService.DELETE_BUTTON;
    }

    @Override
    public String save() {
        return currentPlayerIndex + DraftOrchestrator.SAVE_SEPARATOR + isReversing;
    }

    @Override
    public void load(String data) {
        String[] tokens = data.split(DraftOrchestrator.SAVE_SEPARATOR, 2);
        if (tokens.length != 2) {
            throw new IllegalArgumentException("Invalid data for PublicSnakeDraftOrchestrator: " + data);
        }
        currentPlayerIndex = Integer.parseInt(tokens[0]);
        isReversing = Boolean.parseBoolean(tokens[1]);
    }

    @Override
    public String savePlayerState(OrchestratorState state) {
        if (!(state instanceof State)) {
            throw new IllegalArgumentException("Invalid state type for PublicSnakeDraftOrchestrator: "
                    + state.getClass().getSimpleName());
        }
        State psdState = (State) state;
        return psdState.getOrderIndex() + "";
    }

    @Override
    public OrchestratorState loadPlayerState(String data) {
        int orderIndex = Integer.parseInt(data);
        State state = new State();
        state.setOrderIndex(orderIndex);
        return state;
    }

    @Override
    public String validateState(DraftManager draftManager) {
        if (currentPlayerIndex < 0
                || currentPlayerIndex >= draftManager.getPlayerStates().size()) {
            return "Invalid 'current player' index: " + currentPlayerIndex
                    + ". Fix it with `/draft public_snake set_current_player`.";
        }
        // Ensure all players have a valid State, with unique and valid order indices.
        Set<Integer> distinctOrderIndices = new HashSet<>();
        for (Map.Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerState = entry.getValue();
            OrchestratorState orchestratorState = playerState.getOrchestratorState();
            if (orchestratorState == null || !(orchestratorState instanceof State)) {
                return "Player " + playerUserId
                        + " has invalid draft state (missing or weird type). Try `/draft manage set_orchestrator public_snake` (this will reset the draft order).";
            }
            State state = (State) orchestratorState;
            if (state.getOrderIndex() < 0
                    || state.getOrderIndex() >= draftManager.getPlayerStates().size()) {
                return "Player " + playerUserId + " has out of bounds order index: " + state.getOrderIndex()
                        + ". Fix it with `/draft public_snake set_order`.";
            }
            if (distinctOrderIndices.contains(state.getOrderIndex())) {
                return "Duplicate order index found: " + state.getOrderIndex()
                        + ". Fix it with `/draft public_snake set_order`.";
            }
            distinctOrderIndices.add(state.getOrderIndex());
        }
        if (distinctOrderIndices.size() != draftManager.getPlayerStates().size()) {
            return "Player order indices are not unique. Fix it with `/draft public_snake set_order`.";
        }

        return null;
    }

    @Override
    public String getButtonPrefix() {
        return "psd_";
    }

    private Button getReprintDraftButton() {
        return Buttons.gray(
                DraftButtonService.DRAFT_BUTTON_SERVICE_PREFIX + getButtonPrefix() + "reprintdraft",
                "Show draft again");
    }

    @Override
    public String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {

        if (buttonId.equals("reprintdraft")) {
            List<String> playerOrder = getDraftOrder(draftManager);
            PublicDraftInfoService.send(
                    draftManager,
                    playerOrder,
                    getCurrentPlayer(playerOrder),
                    getNextPlayer(playerOrder),
                    List.of(getReprintDraftButton()));
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
        PublicSnakeDraftSettings snakeSettings = draftSystemSettings.getPublicSnakeDraftSettings();
        if (snakeSettings.getPresetDraftOrder().isVal()) {
            List<String> presetOrder = snakeSettings.getOrderedPlayerIds();
            if (presetOrder == null
                    || presetOrder.size()
                            != draftSystemSettings.getPlayerUserIds().size()) {
                return "Error: Preset draft order is enabled, but the order is incomplete.";
            }

            initialize(game.getDraftManager(), presetOrder);
        } else {
            initialize(game.getDraftManager(), null);
        }

        return null;
    }

    public List<String> getDraftOrder(DraftManager draftManager) {
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

    private String getCurrentPlayer(List<String> playerOrder) {
        return playerOrder.get(currentPlayerIndex);
    }

    private String getNextPlayer(List<String> playerOrder) {
        int nextIndex = currentPlayerIndex + (isReversing ? -1 : 1);
        if (nextIndex < 0 || nextIndex >= playerOrder.size()) {
            // When you get to an end of the snake, the next player is the current player
            // repeated.
            return playerOrder.get(currentPlayerIndex);
        }

        return playerOrder.get(nextIndex);
    }

    private void advanceToNextPlayer(List<String> playerOrder) {
        currentPlayerIndex += isReversing ? -1 : 1;
        if (currentPlayerIndex < 0) {
            currentPlayerIndex = 0;
            isReversing = false;
        } else if (currentPlayerIndex >= playerOrder.size()) {
            currentPlayerIndex = playerOrder.size() - 1;
            isReversing = true;
        }
    }
}
