package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.componentsV2.MessageV2Editor;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;

@UtilityClass
public class BagDraftMessageService {
    private static final String PICK_SUMMARY_START = "### Pending Picks";
    private static final Integer MAX_MESSAGE_SPLITS = 15;

    public record DraftChoiceInfo(
            DraftChoice draftChoice, Boolean isVisible, Boolean isPendingPick, Boolean isLegalToPick) {}

    public static void sendPlayerDraftInfo(
            DraftManager draftManager,
            String playerUserId,
            List<DraftChoiceInfo> draftChoices,
            List<Button> extraButtons) {

        Game game = draftManager.getGame();
        Player player = game.getPlayer(playerUserId);
        if (player == null) {
            BotLogger.warning(new LogOrigin(game), "Cannot find drafting player in game: " + playerUserId);
            return;
        }

        MessageChannel channel = BagChannelService.regenerateBagChannel(game, player);
        if (channel == null) return;

        MessageV2Builder builder = new MessageV2Builder(channel, MAX_MESSAGE_SPLITS);

        List<String> visibleChoiceKeys = new ArrayList<>(draftChoices.stream()
                .filter(info -> info.isVisible)
                .map(info -> info.draftChoice.getChoiceKey())
                .toList());

        for (Draftable d : draftManager.getDraftables()) {
            String uniqueKey = game.getName() + "_" + d.getType().toString().toLowerCase();
            FileUpload uploadedImage = d.generateSummaryImage(draftManager, uniqueKey, visibleChoiceKeys);
            if (uploadedImage != null) {
                builder.appendInlineImage(uploadedImage);
                // MessageHelper.sendFileUploadToChannel(channel, uploadedImage);
            }
        }

        // String draftSummary = getSummary(draftManager, draftChoices);
        // builder.append(draftSummary);

        for (Draftable d : draftManager.getDraftables()) {
            String draftableHeader = getSectionHeader(d.getDisplayName());
            List<MessageTopLevelComponent> buttons =
                    new ArrayList<>(getDraftButtonWithText(draftManager, d, draftChoices));

            builder.appendLine(draftableHeader);
            buttons.forEach(builder::append);
            // builder.append(buttons);

            // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, draftableHeader, buttons);
        }

        String pendingPickSummary = getPendingPickSummary(draftManager, draftChoices);
        builder.appendReplaceableText(pendingPickSummary);
        if (extraButtons != null && !extraButtons.isEmpty()) {
            builder.append(extraButtons);
        }
        // builder.append(getSubmitPicksText());
        // builder.append(getSubmitPicksButton(canSubmitPicks));
        // MessageHelper.sendMessageToChannel(channel, pendingPickSummary);

        builder.send();
    }

    // public static void sendPublicSummary(
    //     DraftManager draftManager,
    //     int lockedInPlayers
    // ) {
    //     Game game = draftManager.getGame();
    //     MessageChannel channel = game.getMainGameChannel();
    //     if (channel == null) return;

    //     String summary = "### Draft Summary\n"
    //             + "> " + lockedInPlayers + " / " + draftManager.getPlayerUserIds().size() + " players have locked in
    // their bags.\n";

    //     MessageHelper.sendMessageToChannel(channel, summary);
    // }

    public static void editPlayerDraftInfo(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            String playerUserId,
            List<DraftChoiceInfo> draftChoices,
            List<Button> extraButtons
            // List<String> visibleChoiceKeys,
            // List<String> pendingPicks
            ) {

        Game game = draftManager.getGame();
        Player player = game.getPlayer(playerUserId);
        if (player == null) {
            BotLogger.warning(new LogOrigin(game), "Cannot find drafting player in game: " + playerUserId);
            return;
        }

        MessageChannel channel = BagChannelService.regenerateBagChannel(game, player);
        if (channel == null) return;

        MessageV2Editor editor = new MessageV2Editor();

        List<String> visibleChoiceKeys = new ArrayList<>(draftChoices.stream()
                .filter(info -> info.isVisible)
                .map(info -> info.draftChoice.getChoiceKey())
                .toList());
        for (Draftable d : draftManager.getDraftables()) {
            String uniqueKey = game.getName() + "_" + d.getType().toString().toLowerCase();
            FileUpload uploadedImage = d.generateSummaryImage(draftManager, uniqueKey, visibleChoiceKeys);
            if (uploadedImage != null) {
                editor.replace(Pattern.quote(uniqueKey), uploadedImage);
                // MessageHelper.sendFileUploadToChannel(channel, uploadedImage);
            }
        }

        String pendingPickSummary = getPendingPickSummary(draftManager, draftChoices);
        editor.replace("^" + PICK_SUMMARY_START, pendingPickSummary);

        for (Draftable d : draftManager.getDraftables()) {
            List<Button> buttons = new ArrayList<>(getDraftButtons(draftManager, d, draftChoices));
            buttons.stream().forEach(button -> editor.replace(button.getCustomId(), button));
        }
        if (extraButtons != null && !extraButtons.isEmpty()) {
            for (Button button : extraButtons) {
                editor.replace(button.getCustomId(), button);
            }
        }

        editor.applyToRecentMessages(channel, 15, madeChanges -> {
            // If no changes were able to be made, there's an issue with the'
            // bag channel, perhaps. Just regenerate the whole thing.
            if (!madeChanges) {
                sendPlayerDraftInfo(draftManager, playerUserId, draftChoices, extraButtons);
            }
        });

        // if(event instanceof ButtonInteractionEvent buttonEvent) {
        //     String buttonId = buttonEvent.getCustomId();
        // }

        // getMessageHistory(event, channel)
        //         .queue(editDraftInfo(draftManager, visibleChoiceKeys, pendingPicks, pendingPickSummary),
        // BotLogger::catchRestError);
    }

    // public static void pingCurrentPlayer(
    //         DraftManager draftManager,
    //         String currentPlayerUserID,
    //         List<String> clearMessageHeaders,
    //         List<String> clearAttachments,
    //         List<Button> extraButtons) {
    //     Game game = draftManager.getGame();
    //     String msg = "Nobody is up to draft...";
    //     Player p = game.getPlayer(currentPlayerUserID);
    //     if (p != null) msg = "### " + p.getPing() + " is up to draft!";

    //     List<Button> buttons = new ArrayList<>(extraButtons != null ? extraButtons : List.of());
    //     buttons = MessageHelper.addUndoButtonToList(buttons, game.getName());

    //     MessageChannel channel = game.getMainGameChannel();
    //     if (channel == null) return;
    //     MessageFunction clearOldFunc = clearOldPingsAndButtonsFunc(true, clearMessageHeaders, clearAttachments);
    //     MessageHelper.splitAndSentWithAction(msg, channel, buttons, clearOldFunc);
    // }

    // Produce button message

    private List<MessageTopLevelComponent> getDraftButtonWithText(
            DraftManager draftManager, Draftable draftable, List<DraftChoiceInfo> draftChoices) {

        List<MessageTopLevelComponent> components = new ArrayList<>();

        Map<String, DraftChoiceInfo> choiceInfoByKey = draftChoices.stream()
                .collect(HashMap::new, (m, info) -> m.put(info.draftChoice.getChoiceKey(), info), Map::putAll);

        List<DraftChoice> allDraftChoices = draftable.getAllDraftChoices();
        for (DraftChoice choice : allDraftChoices) {
            if (!choiceInfoByKey.containsKey(choice.getChoiceKey())) {
                // Assumed not visible/legal
                continue;
            }

            DraftChoiceInfo info = choiceInfoByKey.get(choice.getChoiceKey());
            if (!info.isVisible) {
                // No buttons for invisible choices
                continue;
            }

            String buttonCustomId = choice.getButton().getCustomId();

            Button pickButton = null;
            if (info.isPendingPick) {
                pickButton = Buttons.red(buttonCustomId, "Unpick");
            } else if (info.isLegalToPick) {
                pickButton = Buttons.green(buttonCustomId, "Pick");
            } else {
                pickButton = Buttons.gray(buttonCustomId, "Unavailable").withDisabled(true);
            }

            StringBuilder choiceText = new StringBuilder();
            choiceText.append(choice.getIdentifyingEmoji());
            choiceText.append(" ");
            choiceText.append("**").append(choice.getUnformattedName()).append("**");
            choiceText.append(System.lineSeparator()).append("> ").append(choice.getFormattedName());

            Section pickSection = Section.of(pickButton, TextDisplay.of(choiceText.toString()));
            components.add(pickSection);
        }

        return components;
    }

    private List<Button> getDraftButtons(
            DraftManager draftManager, Draftable draftable, List<DraftChoiceInfo> allDraftChoices) {
        // List<DraftChoice> allDraftChoices = draftable.getAllDraftChoices();
        List<DraftChoiceInfo> draftChoiceInfos = allDraftChoices.stream()
                .filter(info -> info.draftChoice().getType().equals(draftable.getType()))
                .filter(info -> info.isVisible())
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (DraftChoiceInfo info : draftChoiceInfos) {
            buttons.add(getDraftButton(info));
        }

        // Append custom buttons
        buttons.addAll(draftable.getCustomChoiceButtons(draftChoiceInfos.stream()
                .map(info -> info.draftChoice().getChoiceKey())
                .toList()));

        return buttons;
    }

    private Button getDraftButton(DraftChoiceInfo info) {
        String buttonCustomId = info.draftChoice.getButton().getCustomId();
        Button pickButton = null;
        if (info.isPendingPick) {
            pickButton = Buttons.red(buttonCustomId, "Unpick");
        } else if (info.isLegalToPick) {
            pickButton = Buttons.green(buttonCustomId, "Pick");
        } else {
            pickButton = Buttons.gray(buttonCustomId, "Unavailable").withDisabled(true);
        }

        return pickButton;
    }

    // Summary generation

    // private static String getSummary(
    //         DraftManager draftManager, List<String> playerOrder, String currentPlayer, String nextPlayer) {
    //     Game game = draftManager.getGame();
    //     List<Draftable> draftables = draftManager.getDraftables();
    //     int padding = String.format("%s", playerOrder.size()).length() + 1;

    //     Map<DraftableType, DraftChoice> defaultChoices = draftables.stream()
    //             .collect(HashMap::new, (m, d) -> m.put(d.getType(), d.getNothingPickedChoice()), Map::putAll);

    //     StringBuilder sb = new StringBuilder(SUMMARY_START);
    //     int pickNum = 1;
    //     for (String userId : playerOrder) {
    //         Player player = game.getPlayer(userId);
    //         PlayerDraftState picks = draftManager.getPlayerStates().get(userId);
    //         if (player == null || picks == null)
    //             throw new IllegalStateException("Player or picks missing for playerID " + userId);

    //         sb.append("\n> `").append(Helper.leftpad(pickNum + ".", padding)).append("` ");
    //         StringBuilder bulletSummary = new StringBuilder();
    //         for (Draftable draftable : draftables) {
    //             List<String> longChoiceNames = new ArrayList<>();
    //             if (picks.getPicks().containsKey(draftable.getType())) {
    //                 List<DraftChoice> draftablePicks = picks.getPicks().get(draftable.getType());
    //                 for (DraftChoice choice : draftablePicks) {
    //                     if (choice.getIdentifyingEmoji() != null) {
    //                         sb.append(choice.getIdentifyingEmoji());
    //                     } else {
    //                         longChoiceNames.add(choice.getFormattedName());
    //                     }
    //                 }
    //             } else if (defaultChoices.containsKey(draftable.getType())) {
    //                 DraftChoice noChoice = defaultChoices.get(draftable.getType());
    //                 if (noChoice.getIdentifyingEmoji() != null) {
    //                     sb.append(noChoice.getIdentifyingEmoji());
    //                 }
    //                 // Skip adding anything if no default emoji
    //             }

    //             if (longChoiceNames.size() > 0) {
    //                 bulletSummary.append("- " + draftable.getDisplayName() + ": " + System.lineSeparator() + "  - ");
    //                 bulletSummary.append(String.join(System.lineSeparator() + "  - ", longChoiceNames));
    //             }
    //         }

    //         if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("*");
    //         if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("**__");
    //         sb.append(player.getUserName());
    //         if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("   <- CURRENTLY DRAFTING");
    //         if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("   <- on deck");
    //         if (currentPlayer != null && userId.equals(currentPlayer)) sb.append("__**");
    //         if (nextPlayer != null && userId.equals(nextPlayer)) sb.append("*");

    //         pickNum++;
    //     }
    //     return sb.toString();
    // }

    private String getPendingPickSummary(DraftManager draftManager, List<DraftChoiceInfo> draftChoices) {

        StringBuilder sb = new StringBuilder();
        sb.append(PICK_SUMMARY_START).append(System.lineSeparator());

        List<DraftChoiceInfo> pendingPicks =
                draftChoices.stream().filter(info -> info.isPendingPick).toList();

        if (pendingPicks.isEmpty()) {
            sb.append("> No pending picks.");
            return sb.toString();
        }

        for (DraftChoiceInfo info : pendingPicks) {
            sb.append("> - ");
            sb.append(
                    info.draftChoice.getIdentifyingEmoji() != null ? info.draftChoice.getIdentifyingEmoji() + " " : "");
            sb.append("**").append(info.draftChoice.getUnformattedName()).append("**");
            sb.append(System.lineSeparator());
        }

        sb.append("Once you've selected enough picks, you can submit your choices.");

        return sb.toString();
    }

    private static String getSectionHeader(String displayName) {
        return "__**" + displayName.toUpperCase() + ":**__";
    }
}
