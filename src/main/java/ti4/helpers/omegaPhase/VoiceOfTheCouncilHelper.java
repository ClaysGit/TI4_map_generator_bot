package ti4.helpers.omegaPhase;

import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class VoiceOfTheCouncilHelper {
    public static void ResetVoiceOfTheCouncil(Game game) {
        //Clean everything in case it got put somewhere weird
        game.getLaws().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getLawsInfo().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getDiscardAgendas().remove(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getAgendas().remove(Constants.VOICE_OF_THE_COUNCIL_ID);

        game.addLaw(Constants.VOICE_OF_THE_COUNCIL_ID, null);

        if (game.getCustomPublicVP().containsKey(Constants.VOICE_OF_THE_COUNCIL_ID)) {
            game.removeCustomPO(Constants.VOICE_OF_THE_COUNCIL_ID);
        }

        game.addCustomPO(Constants.VOICE_OF_THE_COUNCIL_PO, 1);
    }

    public static void ElectVoiceOfTheCouncil(Game game, Player player) {
        var lawID = game.getLaws().get(Constants.VOICE_OF_THE_COUNCIL_ID);
        if (lawID == null) {
            System.out.println("Law ID is null, cannot elect Voice of the Council.");
            return;
        }
        var poID = game.getRevealedPublicObjectives().get(Constants.VOICE_OF_THE_COUNCIL_PO);
        if (poID == null) {
            System.out.println("Public Objective ID is null, cannot elect Voice of the Council.");
            return;
        }

        var previousElectee = game.getLawsInfo().get(Constants.VOICE_OF_THE_COUNCIL_ID);
        game.getScoredPublicObjectives().remove(Constants.VOICE_OF_THE_COUNCIL_PO);
        game.reviseLaw(lawID, player.getFaction());
        game.scorePublicObjective(player.getUserID(), poID);

        StringBuilder sb = new StringBuilder();
        sb.append("**Voice of the Council**\n");
        if (previousElectee != null) {
            var previousPlayer = game.getPlayerFromColorOrFaction(previousElectee);
            sb.append(previousPlayer.getRepresentation()).append(" is no longer Voice of the Council.\n");
        }
        sb.append(player.getRepresentation()).append(" has been elected as Voice of the Council.");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb.toString());
    }
}
