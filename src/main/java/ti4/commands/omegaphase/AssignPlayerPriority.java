package ti4.commands.omegaphase;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.helpers.omegaPhase.PriorityTrackHelper;

class AssignPlayerPriority extends GameStateSubcommand {
    public AssignPlayerPriority() {
        super(Constants.ASSIGN_PLAYER_PRIORITY, "Assign a player's position on the Priority Track", true, true);
        addOptions(
            new OptionData(OptionType.INTEGER, Constants.PRIORITY_POSITION, "Position on priority track (assign -1 to remove them from the track)", true))
                .addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,
                    "Set stats for another Faction or Color")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        var player = getPlayer();
        var maxPosition = game.getPlayers().size();
        var newPosition = event.getOption(Constants.PRIORITY_POSITION, null, OptionMapping::getAsInt);
        if (newPosition < -1 || newPosition > maxPosition) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Priority position must be between 1 and " + maxPosition + ", or -1 to remove them.");
            return;
        }

        PriorityTrackHelper.AssignPlayerToPriority(game, player, newPosition);
    }
}
