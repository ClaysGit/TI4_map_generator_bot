package ti4.helpers.omegaPhase;

import java.util.stream.Collectors;

import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PriorityTrackHelper {
    public static void PrintPriorityTrack(Game game) {
        var sb = "**Priority Track**\n";

        var players = game.getPlayers().values();
        var priorityMap = players.stream()
            .filter(p -> p.hasPriorityPosition())
            .collect(Collectors.toMap(Player::getPriorityPosition, (player) -> player));

        for (var i = 0; i < players.size(); i++) {
            int priority = i + 1;
            if (priorityMap.containsKey(priority)) {
                var player = priorityMap.get(priority);
                sb += String.format("%d. %s\n", priority, player.getRepresentation());
            } else {
                sb += String.format("%d.\n", priority);
            }
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb);
    }

    /*
     * Priority is 1-indexed, so the first player gets priority 1, the second player
     * gets priority 2, etc.
     */
    public static void AssignPlayerToPriority(Game game, Player player, int priority) {
        if (priority < -1) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Priority must be between 1 and the number of players (or just -1).");
            return;
        }
        var players = game.getPlayers().values();
        if (priority > players.size()) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Priority cannot exceed the number of players.");
            return;
        }
        // Ensure player exists in the game
        if (players.stream().noneMatch(p -> p.getUserID() == player.getUserID())) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Player not found in the game.");
            return;
        }

        var messageOutput = "";

        // If another player already has this priority value, clear it
        var existingIndex = players.stream()
            .filter(p -> p.hasPriorityPosition() && p.getPriorityPosition() == priority)
            .findFirst();
        if (existingIndex.isPresent()) {
            var existingPlayer = existingIndex.get();
            existingPlayer.setPriorityPosition(-1); // Clear the existing player's priority
            messageOutput += existingPlayer.getRepresentation() + " has been removed from position " + priority + " on the priority track.\n";
        }

        if (priority > 0) {
            // Assign the player's priority
            player.setPriorityPosition(priority);
            messageOutput += player.getRepresentation() + " has been assigned to position " + priority + " on the priority track.";
        }

        if (messageOutput.isEmpty()) {
            // If no message was generated, it means the player was to be removed, but was already off the track
            messageOutput = player.getRepresentation() + " is not on the priority track.";
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), messageOutput);
    }

    public static void ClearPriorityTrack(Game game) {
        var players = game.getPlayers().values();
        for (var player : players) {
            player.setPriorityPosition(-1);
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "The priority track has been cleared.");
    }
}
