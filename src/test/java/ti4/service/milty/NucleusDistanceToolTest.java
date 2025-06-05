package ti4.service.milty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.MapTemplateModel;
import ti4.model.WormholeModel.Wormhole;
import ti4.testUtils.BaseTi4Test;

public class NucleusDistanceToolTest extends BaseTi4Test {
    @Test
    // This test is a canary to ensure other tests are only failing on their unique logic.
    void instantiateTest() throws Exception {
        Game game = getGame(6);
        assertEquals("18", game.getTileByPosition("000").getTileModel().getAlias());
        assertEquals("blue5", game.getTileByPosition("101").getTileModel().getAlias());
        assertEquals("blue2", game.getTileByPosition("201").getTileModel().getAlias());
        assertEquals("blueblank", game.getTileByPosition("301").getTileModel().getAlias());
    }

    @Test
    void trivialDistance() throws Exception {
        Game game = getGame(6);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);
        assertEquals(0, distanceTool.getNattyDistance("000", "000"));
        assertEquals(0, distanceTool.getNattyDistance("101", "101"));
        assertEquals(0, distanceTool.getNattyDistance("301", "301"));
    }

    @Test
    void easyDistance() throws Exception {
        Game game = getGame(6);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);
        assertEquals(1, distanceTool.getNattyDistance("000", "101"));
        assertEquals(1, distanceTool.getNattyDistance("101", "000"));
        assertEquals(1, distanceTool.getNattyDistance("201", "301"));
    }

    @Test
    void farDistance() throws Exception {
        Game game = getGame(6);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);
        assertEquals(3, distanceTool.getNattyDistance("000", "301"));
        assertEquals(2, distanceTool.getNattyDistance("101", "104"));
        assertEquals(3, distanceTool.getNattyDistance("211", "104"));
    }

    @Test
    void complexDistance() throws Exception {
        Game game = getGame(6);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);
        assertEquals(4, distanceTool.getNattyDistance("211", "206"));
        assertEquals(5, distanceTool.getNattyDistance("211", "309"));
        assertEquals(5, distanceTool.getNattyDistance("202", "313"));
    }

    @Test
    void hyperlaneDistance() throws Exception {
        Game game = getGame(8);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);

        // Hyperlane in the distance calc
        assertEquals(1, distanceTool.getNattyDistance("000", "207")); // Passes through 1 hyperlane
        assertEquals(2, distanceTool.getNattyDistance("208", "202")); // Passes through 2 hyperlanes
        assertEquals(4, distanceTool.getNattyDistance("424", "103")); // Passes through 1 hyperlanes
        assertEquals(3, distanceTool.getNattyDistance("212", "203")); // Passes through 0 or 2 hyperlanes

        // Distances involving hyperlane tiles should be somewhat normal.
        assertEquals(0, distanceTool.getNattyDistance("104", "104")); // Is a hyperlane
        assertEquals(1, distanceTool.getNattyDistance("000", "104")); // Distance to a hyperlane
    }

    @Test
    void hyperlaneAnomalyDistance() throws Exception {
        Game game = getGame(8);
        MapTemplateModel mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, mapTemplate);

        game.setTile(new Tile("41", "103")); // Grav Rift
        assertEquals(true, game.getTileByPosition("103").isGravityRift());
        game.setTile(new Tile("39", "302")); // Empty Alpha
        game.setTile(new Tile("79", "209")); // Asteroid Alpha
        assertEquals(true, game.getTileByPosition("302").getWormholes().stream().anyMatch(w -> w == Wormhole.ALPHA));
        assertEquals(true, game.getTileByPosition("209").getWormholes().stream().anyMatch(w -> w == Wormhole.ALPHA));

        // Hyperlane in the distance calc, with anomalies
        assertEquals(2, distanceTool.getNattyDistance("206", "202")); // Passes through 1 hyperlane, 1 grav rift

        // Hyperlane in the distance calc, ignoring wormholes
        assertEquals(4, distanceTool.getNattyDistance("302", "209")); // Passes through 1 hyperlane, ignoring wormholes
        assertEquals(5, distanceTool.getNattyDistance("302", "313")); // Passes through 1 hyperlane, ignoring wormholes
    }

    private static Game getGame(int playerCount) throws Exception {
        Game game = new Game();
        game.setName("testGame");
        createPlayer(game, "p1", "blue");
        createPlayer(game, "p2", "blue");
        createPlayer(game, "p3", "blue");
        createPlayer(game, "p4", "blue");
        createPlayer(game, "p5", "blue");
        createPlayer(game, "p6", "blue");
        if (playerCount > 6) {
            createPlayer(game, "p7", "blue");
        }
        if (playerCount > 7) {
            createPlayer(game, "p8", "blue");
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
