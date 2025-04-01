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

        for(var i = 0; i < players.size(); i++) {
            int priority = i + 1;
            if(priorityMap.containsKey(priority)) {
                var player = priorityMap.get(priority);
                sb += String.format("%d. %s\n", priority, player.getRepresentation());
            } else {
                sb += String.format("%d.\n", priority);
            }
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb);
        // Full game player state
        game.getPlayers().values().stream()
            .forEach(p -> System.out.println("Player: " + p.getRepresentation() + ", Priority: " + p.getPriorityPosition()));
    }

    /*
     * Priority is 1-indexed, so the first player gets priority 1, the second player
     * gets priority 2, etc.
     */
    public static void AssignPlayerToPriority(Game game, Player player, int priority) {
        // Full game player state
        System.out.println("Start of AssignPlayerToPriority");
        game.getPlayers().values().stream()
            .forEach(p -> System.out.println("Player: " + p.getRepresentation() + ", Priority: " + p.getPriorityPosition()));
        if(priority < 1) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Priority must be a positive integer.");
            return;
        }
        var players = game.getPlayers().values();
        if(priority > players.size()) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Priority cannot exceed the number of players.");
            return;
        }
        // Ensure player exists in the game
        if(players.stream().noneMatch(p -> p.getUserID() == player.getUserID())) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Player not found in the game.");
            return;
        }
        // If another player already has this priority value, clear it
        var existingIndex = players.stream()
            .filter(p -> p.hasPriorityPosition() && p.getPriorityPosition() == priority)
            .findFirst();
        if(existingIndex.isPresent()) {
            var existingPlayer = existingIndex.get();
            existingPlayer.setPriorityPosition(-1); // Clear the existing player's priority
            MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                    existingPlayer.getRepresentation() + " has been removed from position " + priority + " on the priority track.");
        }
        // Assign the player's priority
        player.setPriorityPosition(priority);
        System.out.println("End of AssignPlayerToPriority");
        System.out.println("Assigned player " + player.getRepresentation() + " to priority " + player.getPriorityPosition());
        // Full game player state
        game.getPlayers().values().stream()
            .forEach(p -> System.out.println("Player: " + p.getRepresentation() + ", Priority: " + p.getPriorityPosition()));
        MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                player.getRepresentation() + " has been " + (existingIndex.isPresent() ? "moved" : "added") +
                        " to position " + priority + " on the priority track.");
    }

    public static void ClearPriorityTrack(Game game) {
        var players = game.getPlayers().values();
        for(var player : players) {
            player.setPriorityPosition(-1);
        }

        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "The priority track has been cleared.");
        // Full game player state
        game.getPlayers().values().stream()
            .forEach(p -> System.out.println("Player: " + p.getRepresentation() + ", Priority: " + p.getPriorityPosition()));
    }
}
