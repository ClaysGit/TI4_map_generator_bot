package ti4.service.milty;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.service.milty.MiltyService.DraftSpec;
import ti4.settings.GlobalSettings;
import ti4.testUtils.BaseTi4Test;

public class GenerateSliceServiceTest extends BaseTi4Test {
    @Test
    void generateFor6p() throws Exception {
        int playerCount = 6;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        DraftSpec draftSpec = getDraftSpec(game, playerCount);

        GenerateSlicesService.generateSlices(null, draftManager, draftSpec);
    }

    @Test
    void generateFor6pNucleus() throws Exception {
        int playerCount = 6;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();

        //Nucleus draft spec
        DraftSpec draftSpec = getDraftSpec(game, playerCount);
        draftSpec.setGenerateNucleus(true);
        draftSpec.setDraftSeats(true);
        draftSpec.minTot = 4;
        draftSpec.maxTot = 9;

        //Convert Milty template to Nucleus template
        MapTemplateModel miltyTemplate = draftSpec.getTemplate();
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);
        draftSpec.setTemplate(nucleusTemplate);

        boolean success = GenerateSlicesService.generateSlices(null, draftManager, draftSpec);
        assert (success);
    }

    private static DraftSpec getDraftSpec(Game game, int playerCount) {
        DraftSpec specs = new DraftSpec(game);
        specs.setTemplate(Mapper.getMapTemplate(game.getMapTemplateID()));
        specs.numFactions = playerCount + 1;
        specs.numSlices = playerCount + 1;
        specs.anomaliesCanTouch = false;
        specs.extraWHs = true;
        specs.minLegend = 1;
        specs.maxLegend = 2;
        specs.minTot = 9;
        specs.maxTot = 13;
        return specs;
    }

    private static Game getGame(int playerCount) throws Exception {
        GlobalSettings.setSetting(GlobalSettings.ImplementedSettings.READY_TO_RECEIVE_COMMANDS.toString(), true);
        Game game = new Game();
        game.setName("testGame");
        for (int i = 1; i <= playerCount; i++) {
            String playerId = "p" + i;
            createPlayer(game, playerId, "blue");
        }
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.init(game);
        draftManager.setMapTemplate(mapTemplate.getID());
        draftManager.setPlayers(new ArrayList<>(game.getPlayers().keySet()));
        game.setMapTemplateID(mapTemplate.getID());
        MiltyDraftHelper.buildPartialMap(game, null);

        return game;
    }

    private static Player createPlayer(Game game, String userId, String color) {
        var player = game.addPlayer(userId, color);
        return player;
    }
}
