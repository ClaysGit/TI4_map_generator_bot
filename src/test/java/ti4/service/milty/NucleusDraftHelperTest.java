package ti4.service.milty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.testUtils.BaseTi4Test;

public class NucleusDraftHelperTest extends BaseTi4Test {
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
    void convert3pMap() throws Exception {
        Game game = getGame(3);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(3, nucleusTemplate.getPlayerCount());
        assertEquals(6, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void convert4pMap() throws Exception {
        Game game = getGame(4);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(4, nucleusTemplate.getPlayerCount());
        assertEquals(8, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void convert5pMap() throws Exception {
        Game game = getGame(5);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(5, nucleusTemplate.getPlayerCount());
        assertEquals(10, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void convert6pMap() throws Exception {
        Game game = getGame(6);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(6, nucleusTemplate.getPlayerCount());
        assertEquals(12, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void convert7pMap() throws Exception {
        Game game = getGame(7);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(7, nucleusTemplate.getPlayerCount());
        assertEquals(14, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void convert8pMap() throws Exception {
        Game game = getGame(8);
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);

        assertNotNull(nucleusTemplate);
        assertEquals(8, nucleusTemplate.getPlayerCount());
        assertEquals(16, nucleusTemplate.getTemplateTiles().stream().filter(t -> t.getNucleus() != null && t.getNucleus()).count());
        for (int i = 0; i < nucleusTemplate.getPlayerCount(); ++i) {
            int player = i + 1;
            assertEquals(4, nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == player)
                .count(), "Player " + player + " should have exactly two Nucleus tiles");
        }
        for (MapTemplateTile tile : nucleusTemplate.getTemplateTiles()) {
            if (tile.getNucleus() != null && tile.getNucleus()) {
                assertEquals(null, tile.getPlayerNumber());
                assertEquals(null, tile.getHome());
            }
        }
        assertEquals(2, nucleusTemplate.bluePerPlayer());
        assertEquals(1, nucleusTemplate.redPerPlayer());
        assertEquals(3, nucleusTemplate.tilesPerPlayer());
    }

    @Test
    void create3pNucleus() throws Exception {
        int playerCount = 3;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);
        assertNotNull(nucleusTemplate);

        NucleusDraftHelper.createNucleus(game, draftManager, nucleusTemplate, distanceTool);
        Map<String, Tile> tiles = game.getTileMap();
        assertEquals(playerCount * 2 + 1, tiles.values().stream()
            .filter(t -> !t.getTileModel().isHyperlane())
            .filter(t -> !TileHelper.isDraftTile(t.getTileModel()))
            .count());
        for (MapTemplateTile templateTile : nucleusTemplate.getTemplateTiles()) {
            if (templateTile.getNucleus() != null && templateTile.getNucleus()) {
                assertNotNull(tiles.get(templateTile.getPos()), "Nucleus tile should be placed on the map at position: " + templateTile.getPos());
                Tile placedTile = tiles.get(templateTile.getPos());
                assertFalse(TileHelper.isDraftTile(placedTile.getTileModel()));
                assertFalse(draftManager.getAll().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getBlue().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getRed().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
            } else if (templateTile.getPlayerNumber() != null) {
                Tile placedTile = tiles.get(templateTile.getPos());
                assert (TileHelper.isDraftTile(placedTile.getTileModel()));
            }
        }
    }

    @Test
    void create4pNucleus() throws Exception {
        int playerCount = 4;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);
        assertNotNull(nucleusTemplate);

        NucleusDraftHelper.createNucleus(game, draftManager, nucleusTemplate, distanceTool);
        Map<String, Tile> tiles = game.getTileMap();
        assertEquals(playerCount * 2 + 1, tiles.values().stream()
            .filter(t -> !t.getTileModel().isHyperlane())
            .filter(t -> !TileHelper.isDraftTile(t.getTileModel()))
            .count());
        for (MapTemplateTile templateTile : nucleusTemplate.getTemplateTiles()) {
            if (templateTile.getNucleus() != null && templateTile.getNucleus()) {
                assertNotNull(tiles.get(templateTile.getPos()), "Nucleus tile should be placed on the map at position: " + templateTile.getPos());
                Tile placedTile = tiles.get(templateTile.getPos());
                assertFalse(TileHelper.isDraftTile(placedTile.getTileModel()));
                assertFalse(draftManager.getAll().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getBlue().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getRed().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
            } else if (templateTile.getPlayerNumber() != null) {
                Tile placedTile = tiles.get(templateTile.getPos());
                assert (TileHelper.isDraftTile(placedTile.getTileModel()));
            }
        }
    }

    @Test
    void create5pNucleus() throws Exception {
        int playerCount = 5;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);
        assertNotNull(nucleusTemplate);

        NucleusDraftHelper.createNucleus(game, draftManager, nucleusTemplate, distanceTool);
        Map<String, Tile> tiles = game.getTileMap();
        assertEquals(playerCount * 2 + 1, tiles.values().stream()
            .filter(t -> !t.getTileModel().isHyperlane())
            .filter(t -> !TileHelper.isDraftTile(t.getTileModel()))
            .count());
        for (MapTemplateTile templateTile : nucleusTemplate.getTemplateTiles()) {
            if (templateTile.getNucleus() != null && templateTile.getNucleus()) {
                assertNotNull(tiles.get(templateTile.getPos()), "Nucleus tile should be placed on the map at position: " + templateTile.getPos());
                Tile placedTile = tiles.get(templateTile.getPos());
                assertFalse(TileHelper.isDraftTile(placedTile.getTileModel()));
                assertFalse(draftManager.getAll().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getBlue().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getRed().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
            } else if (templateTile.getPlayerNumber() != null) {
                Tile placedTile = tiles.get(templateTile.getPos());
                assert (TileHelper.isDraftTile(placedTile.getTileModel()));
            }
        }
    }

    @Test
    void create6pNucleus() throws Exception {
        int playerCount = 6;
        Game game = getGame(playerCount);
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        MapTemplateModel miltyTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        NucleusDistanceTool distanceTool = new NucleusDistanceTool(game, miltyTemplate);
        MapTemplateModel nucleusTemplate = NucleusDraftHelper.convertMiltyToNucleus(miltyTemplate, distanceTool);
        assertNotNull(nucleusTemplate);

        NucleusDraftHelper.createNucleus(game, draftManager, nucleusTemplate, distanceTool);
        Map<String, Tile> tiles = game.getTileMap();
        assertEquals(playerCount * 2 + 1, tiles.values().stream()
            .filter(t -> !t.getTileModel().isHyperlane())
            .filter(t -> !TileHelper.isDraftTile(t.getTileModel()))
            .count());
        for (MapTemplateTile templateTile : nucleusTemplate.getTemplateTiles()) {
            if (templateTile.getNucleus() != null && templateTile.getNucleus()) {
                assertNotNull(tiles.get(templateTile.getPos()), "Nucleus tile should be placed on the map at position: " + templateTile.getPos());
                Tile placedTile = tiles.get(templateTile.getPos());
                assertFalse(TileHelper.isDraftTile(placedTile.getTileModel()));
                assertFalse(draftManager.getAll().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getBlue().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
                assertFalse(draftManager.getRed().stream().anyMatch(t -> t.getTile().getTileID().equals(placedTile.getTileID())),
                    "Nucleus tile should not be in the draft pool: " + placedTile.getTileModel().getAlias());
            } else if (templateTile.getPlayerNumber() != null) {
                Tile placedTile = tiles.get(templateTile.getPos());
                assert (TileHelper.isDraftTile(placedTile.getTileModel()));
            }
        }
    }

    private static Game getGame(int playerCount) throws Exception {
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
