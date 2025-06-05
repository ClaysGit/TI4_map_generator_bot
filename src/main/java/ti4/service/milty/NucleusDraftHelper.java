package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.BotLogger.LogMessageOrigin;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.TileModel.TileBack;

@UtilityClass
public class NucleusDraftHelper {
    // When looking for wormholes to build with, look for tiles with these types.
    private static final List<String> wormholeTypes = List.of("alpha", "beta");

    /**
     * Convert a map template made for Milty drafting to one for Nucleus drafting. Also
     * adds Nucleus placeholder tiles to the template.
     * @param game The game which the draft applies to.
     * @param miltyTemplate A map template made for Milty drafting.
     * @return The Nucleus map template model.
     */
    public static MapTemplateModel convertMiltyToNucleus(Game game, MapTemplateModel miltyTemplate, NucleusDistanceTool distanceTool) {
        // For each tile that's part of a player's slice, if it's not directly touching their home system:
        // - Unset the player/slice parameters
        // - Set the nucleus parameter to true
        Map<Integer, MapTemplateTile> playerNumberToHomeSystem = miltyTemplate.getTemplateTiles().stream()
            .filter(t -> t.getHome() == true && t.getPlayerNumber() != null)
            .collect(Collectors.toMap(MapTemplateTile::getPlayerNumber, t -> t));
        for (MapTemplateTile templateTile : miltyTemplate.getTemplateTiles()) {
            // Skip non-player tiles
            if (templateTile.getPlayerNumber() == null || templateTile.getStaticTileId() != null) continue;
            // Skip home systems
            if (templateTile.getHome() != true) continue;

            // Check if the tile is one move away from the home system for the player
            MapTemplateTile homeTile = playerNumberToHomeSystem.get(templateTile.getPlayerNumber());
            if (homeTile == null) {
                BotLogger.warning(new LogMessageOrigin(game), "Nucleus draft: missing home system for player " + templateTile.getPlayerNumber());
                continue; // no home system for this player
            }

            Integer homeDistance = distanceTool.getNattyDistance(templateTile.getPos(), homeTile.getPos());
            if (homeDistance != null && homeDistance > 1) {
                // Convert to a nucleus tile
                templateTile.setPlayerNumber(null);
                templateTile.setMiltyTileIndex(null);
                templateTile.setNucleus(true);
            }
        }

        // Fix tiles per player
        int oldBluePerPlayer = miltyTemplate.getBluePerPlayer();
        int oldTilesPerPlayer = miltyTemplate.tilesPerPlayer();
        int newTilesPerPlayer = miltyTemplate.getTemplateTiles().stream()
            .filter(t -> t.getPlayerNumber() == 1 && t.getMiltyTileIndex() != null)
            .mapToInt(t -> 1)
            .sum();
        float oldBluePerPlayerRatio = (float) oldBluePerPlayer / oldTilesPerPlayer;
        int newBluePerPlayer = (int) Math.ceil(newTilesPerPlayer * oldBluePerPlayerRatio);
        // Remember to exclude the home system tile from the count
        int newRedPerPlayer = (newTilesPerPlayer - 1) - newBluePerPlayer;
        miltyTemplate.setBluePerPlayer(newBluePerPlayer);
        miltyTemplate.setRedPerPlayer(newRedPerPlayer);
        miltyTemplate.setTilesPerPlayer(newTilesPerPlayer);

        // Fix emulated tiles
        List<String> emulatedTiles = miltyTemplate.getSliceEmulateTiles();
        String emulatedHomeSystem = emulatedTiles.stream()
            .filter(p -> miltyTemplate.getTemplateTiles().stream()
                .anyMatch(t -> t.getPos().equals(p) && t.getHome() == true))
            .findFirst().orElse(emulatedTiles.get(0));
        List<String> newEmulatedTiles = new ArrayList<>(List.of(emulatedHomeSystem));
        for (int i = 1; i < emulatedTiles.size(); ++i) {
            String tilePos = emulatedTiles.get(i);
            if (tilePos.equals(emulatedHomeSystem)) continue;
            if (distanceTool.getNattyDistance(emulatedHomeSystem, tilePos) <= 1) {
                newEmulatedTiles.add(tilePos);
            }
        }
        miltyTemplate.setSliceEmulateTiles(newEmulatedTiles);

        return miltyTemplate;
    }

    public static void createNucleus(Game game, MiltyDraftManager draftManager, MapTemplateModel nucleusTemplate, NucleusDistanceTool distanceTool) {
        //Initial thoughts...
        // - Randomize desired wormholes
        int desiredWormholes = getDesiredWormholeCount(game, draftManager, nucleusTemplate);
        // - Pick wormholes
        //   - If 2-3 wormholes, pick all the same type
        //   - If 4+ wormholes, pick alternating types
        Map<String, Integer> wormholesPerType = getWormholesPerType(draftManager, desiredWormholes);
        Map<String, List<MiltyDraftTile>> selectedWormholes = new HashMap<>();
        List<MiltyDraftTile> allWormholeTiles = getWormholeTiles(draftManager.getAll());
        for (Entry<String, Integer> wormholesForType : wormholesPerType.entrySet()) {
            String wormholeType = wormholesForType.getKey();
            int count = wormholesForType.getValue();
            List<MiltyDraftTile> availableWormholes = allWormholeTiles.stream()
                .filter(t -> t.getTile().getWormholes().stream().map(w -> w.toString()).anyMatch(wormholeType::equals))
                .collect(Collectors.toList());

            //I'm assuming that custom content gets weird with wormholes, so need to include somewhat redundant checks.
            if (availableWormholes.size() < count) {
                BotLogger.warning(new LogMessageOrigin(game),
                    "Not enough wormholes of type " + wormholeType + " available for Nucleus draft! Wanted " + count + ", got " + availableWormholes.size() + ".");
            }

            //TODO: Put tiles with multiple wormholes last in the list, so they're only used if needed.
            Collections.shuffle(availableWormholes);
            selectedWormholes.put(wormholeType, availableWormholes.stream()
                .limit(count)
                .collect(Collectors.toList()));
            allWormholeTiles.removeAll(selectedWormholes.get(wormholeType)); //remove from the pool, in case some tiles have multiple wormholes
        }
        // - Place wormholes in the nucleus
        //   - For each type...
        //   - Pick far away spots.
        //     - For each wormhole of the type...
        //     - Shuffle the available positions in the nucleus and pick up to the number needed
        //     - Check their distances, getting the minimum distance between tiles
        //     - Repeat 50 times, and ultimately use the collection with the highest min distance
        // - Remove wormholes from the draft pool
        List<MapTemplateTile> nucleusTiles = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true)
            .collect(Collectors.toList());
        int redBackedWormholes = 0;
        for (String wormholeType : wormholesPerType.keySet()) {
            List<MiltyDraftTile> wormholesToPlace = selectedWormholes.get(wormholeType);
            if (wormholesToPlace == null || wormholesToPlace.isEmpty()) continue;
            int numberToPlace = wormholesToPlace.size();
            List<MapTemplateTile> bestTileChoices = new ArrayList<>();
            Integer bestTileChoicesMinDistance = null;
            for (int attempt = 0; attempt < 50; ++attempt) {
                List<MapTemplateTile> chosenTiles = new ArrayList<>(nucleusTiles.subList(0, numberToPlace));
                Integer chosenTilesMinDistance = null;
                for (int i = 0; i < numberToPlace; i++) {
                    for (int j = i + 1; j < numberToPlace; j++) {
                        MapTemplateTile tile1 = chosenTiles.get(i);
                        MapTemplateTile tile2 = chosenTiles.get(j);
                        Integer dist = distanceTool.getNattyDistance(tile1.getPos(), tile2.getPos());
                        if (chosenTilesMinDistance == null || dist < chosenTilesMinDistance) {
                            chosenTilesMinDistance = dist;
                        }
                    }
                }
                if (chosenTilesMinDistance != null && (bestTileChoicesMinDistance == null || chosenTilesMinDistance > bestTileChoicesMinDistance)) {
                    bestTileChoices = new ArrayList<>(chosenTiles);
                    bestTileChoicesMinDistance = chosenTilesMinDistance;
                }
            }

            // Remove the chosen tile set from the remaining nucleus tiles and place wormholes in them
            nucleusTiles.removeAll(bestTileChoices);
            if (bestTileChoices != null && !bestTileChoices.isEmpty()) {
                Collections.shuffle(wormholesToPlace);
                for (MiltyDraftTile wormholeTile : wormholesToPlace) {
                    if (bestTileChoices.isEmpty()) break; //no more tiles to place
                    game.setTile(new Tile(wormholeTile.getTile().getTileID(), bestTileChoices.get(0).getPos()));
                    bestTileChoices.remove(0);
                    if (wormholeTile.getTierList() == TierList.red) {
                        redBackedWormholes++;
                    }
                }
            }
        }
        // - Determine how many red and blue tiles are still needed after wormhole placement
        int nucleusTileCount = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true)
            .mapToInt(t -> 1)
            .sum();
        int maxRedTiles = (int) Math.ceil(nucleusTileCount / 2.0);
        int minRedTiles = maxRedTiles - 1;
        List<Integer> redTileCounts = IntStream.rangeClosed(minRedTiles, maxRedTiles)
            .boxed().collect(Collectors.toList());
        Collections.shuffle(redTileCounts);
        int intendedRedTileCount = redTileCounts.get(0);
        int actualRedTileCount = Math.max(intendedRedTileCount, redBackedWormholes);
        int actualBlueTileCount = nucleusTileCount - actualRedTileCount;
        int redTilesPlaced = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true)
            .map(t -> game.getTileByPosition(t.getPos()))
            .filter(t -> t != null && t.getTileModel().getTileBack() == TileBack.RED)
            .mapToInt(t -> 1).sum();
        int blueTilesPlaced = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true)
            .map(t -> game.getTileByPosition(t.getPos()))
            .filter(t -> t != null && t.getTileModel().getTileBack() == TileBack.BLUE)
            .mapToInt(t -> 1).sum();
        int redTilesNeeded = actualRedTileCount - redTilesPlaced;
        int blueTilesNeeded = actualBlueTileCount - blueTilesPlaced;

        // - Fill the remaining nucleus positions randomly
        //   - Draw the remaining needed blue tiles
        List<MiltyDraftTile> availableBlues = draftManager.getBlue();
        Collections.shuffle(availableBlues);
        List<MiltyDraftTile> blueTilesToPlace = availableBlues.stream()
            .limit(Math.max(0, blueTilesNeeded))
            .collect(Collectors.toList());
        //   - Draw the remaining needed red tiles
        List<MiltyDraftTile> availableReds = draftManager.getRed();
        Collections.shuffle(availableReds);
        List<MiltyDraftTile> redTilesToPlace = availableReds.stream()
            .limit(Math.max(0, redTilesNeeded))
            .collect(Collectors.toList());
        //   - Place the red tiles
        //     - For each red tile with an anomaly... (placed first to ensure we can space them out)
        //       - Place in the first available position that is at least 1 space away from any other red tile
        //       - If unplaced, place in the first available position that is at least 1 space away from any other anomaly
        //       - If unplaced, place anywhere
        List<MiltyDraftTile> redsWithAnomaly = redTilesToPlace.stream()
            .filter(t -> t.getTile().isAnomaly())
            .collect(Collectors.toList());
        for (MiltyDraftTile redTile : redsWithAnomaly) {
            String bestPosition = getBestRedTilePosition(game, nucleusTemplate, distanceTool, true);
            if (bestPosition == null) {
                BotLogger.warning(new LogMessageOrigin(game), "Unable to find placement for next red tile.");
                break; //no valid position found
            }

            game.setTile(new Tile(redTile.getTile().getTileID(), bestPosition));
        }
        //     - For each red tile without an anomaly...
        //       - Place in the first available position that is at least 1 space away from any other anomaly
        //       - If unplaced, place anywhere
        List<MiltyDraftTile> redsWithoutAnomaly = redTilesToPlace.stream()
            .filter(t -> !t.getTile().isAnomaly())
            .collect(Collectors.toList());
        for (MiltyDraftTile redTile : redsWithoutAnomaly) {
            String bestPosition = getBestRedTilePosition(game, nucleusTemplate, distanceTool, false);
            if (bestPosition == null) {
                BotLogger.warning(new LogMessageOrigin(game), "Unable to find placement for next red tile.");
                break; //no valid position found
            }

            game.setTile(new Tile(redTile.getTile().getTileID(), bestPosition));
        }
        //   - Place blue tiles anywhere (no attempt to balance resources)
        for (MiltyDraftTile blueTile : blueTilesToPlace) {
            List<String> availablePositions = nucleusTemplate.getTemplateTiles().stream()
                .filter(t -> t.getNucleus() == true && game.getTileByPosition(t.getPos()) == null)
                .map(MapTemplateTile::getPos)
                .collect(Collectors.toList());
            if (availablePositions.isEmpty()) {
                BotLogger.warning(new LogMessageOrigin(game), "Unable to find placement for next blue tile.");
                break; //no valid position found
            }
            Collections.shuffle(availablePositions);
            String bluePosition = availablePositions.get(0);

            game.setTile(new Tile(blueTile.getTile().getTileID(), bluePosition));
        }
        // - Remove the nucleus tiles from the draft pool
        removeNucleusTilesFromDraft(game, draftManager);
    }

    private static int getDesiredWormholeCount(Game game, MiltyDraftManager draftManager, MapTemplateModel nucleusTemplate) {
        int absoluteMinWormholes = 2;
        List<MiltyDraftTile> blueWormholes = getWormholeTiles(draftManager.getBlue());
        List<MiltyDraftTile> redWormholes = getWormholeTiles(draftManager.getRed());
        int maxWormholes = blueWormholes.size() + redWormholes.size();
        if (maxWormholes < absoluteMinWormholes) {
            return 0;
        }

        //The red wormhole max is the smaller of red wormholes and red tiles needed
        int nucleusTileCount = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true)
            .mapToInt(t -> 1)
            .sum();
        int maxRedTiles = (int) Math.ceil(nucleusTileCount / 2.0);
        int maxRedWormholes = Math.min(maxRedTiles, redWormholes.size());
        int maxBlueWormholes = Math.min((nucleusTileCount - maxRedTiles), blueWormholes.size());

        //Example is 2-4 wormholes for 6 players
        // Let's assume that's (playerCount-4) to (playerCount-2) wormholes.
        int suggestedMin = nucleusTemplate.getPlayerCount() - 4;
        int suggestedMax = nucleusTemplate.getPlayerCount() - 2;
        int actualMin = Math.max(suggestedMin, absoluteMinWormholes);
        int actualMax = Math.min(suggestedMax, maxRedWormholes + maxBlueWormholes);

        // get array of possible numbers, min to max inclusive:
        List<Integer> possibleAmounts = IntStream.rangeClosed(actualMin, actualMax)
            .boxed().collect(Collectors.toList());
        Collections.shuffle(possibleAmounts);
        return possibleAmounts.get(0);
    }

    private static Map<String, Integer> getWormholesPerType(MiltyDraftManager draftManager, Integer desiredWormholes) {
        Map<String, Integer> wormholesPerType = new HashMap<>();
        if (desiredWormholes < 2) return wormholesPerType;

        List<String> randomizedTypes = new ArrayList<>(wormholeTypes);
        Collections.shuffle(randomizedTypes);

        // Get available tile counts per type
        Map<String, Integer> availableWormholeCounts = new HashMap<>();
        for (String type : wormholeTypes) {
            draftManager.getAll().stream()
                .filter(t -> t.getTile().getWormholes().stream().map(w -> w.toString()).anyMatch(w -> w.equals(type)))
                .forEach(t -> availableWormholeCounts.put(type, availableWormholeCounts.getOrDefault(type, 0) + 1));
        }

        Integer totalWormholes = 0;
        for (String type : randomizedTypes) {
            if (availableWormholeCounts.getOrDefault(type, 0) < 2)
                continue;
            wormholesPerType.put(type, 2);
            totalWormholes += 2;

            if (availableWormholeCounts.getOrDefault(type, 0) == 2)
                randomizedTypes.remove(type); // remove type if we used all available

            if (totalWormholes >= desiredWormholes) break;
        }

        while (totalWormholes < desiredWormholes) {
            Collections.shuffle(randomizedTypes);
            String type = randomizedTypes.get(0);
            wormholesPerType.put(type, wormholesPerType.getOrDefault(type, 0) + 1);
            totalWormholes += 1;

            if (availableWormholeCounts.getOrDefault(type, 0) <= wormholesPerType.get(type))
                randomizedTypes.remove(type); // remove type if we used all available
            if (randomizedTypes.isEmpty())
                break; // no more types to add
        }

        return wormholesPerType;
    }

    private static List<MiltyDraftTile> getWormholeTiles(List<MiltyDraftTile> draftTiles) {
        //TODO: Are there other wormhole types we want to include? These would be natural types, not gamma or delta.
        return draftTiles.stream()
            .filter(t -> t.getTile().getWormholes().stream().map(w -> w.toString()).anyMatch(wormholeTypes::contains))
            .toList();
    }

    private static String getBestRedTilePosition(Game game, MapTemplateModel nucleusTemplate, NucleusDistanceTool distanceTool, boolean isAnomaly) {
        List<String> redTilePositions = game.getTileMap().values().stream()
            .filter(t -> t.getTileModel().getTileBack() == TileBack.RED)
            .map(t -> t.getPosition())
            .collect(Collectors.toList());
        List<String> anomalyTilePositions = game.getTileMap().values().stream()
            .filter(t -> t.getTileModel().isAnomaly())
            .map(t -> t.getPosition())
            .collect(Collectors.toList());

        List<String> availablePositions = nucleusTemplate.getTemplateTiles().stream()
            .filter(t -> t.getNucleus() == true && game.getTileByPosition(t.getPos()) == null)
            .map(MapTemplateTile::getPos)
            .collect(Collectors.toList());
        if (availablePositions.isEmpty()) {
            BotLogger.warning(new LogMessageOrigin(game), "No available positions for red tile placement in Nucleus draft!");
            return null; // no available positions
        }

        Collections.shuffle(availablePositions);

        // First try to find a position that is at least 2 moves away from any other red tiles
        for (String pos : availablePositions) {
            boolean tooClose = false;
            for (String redPos : redTilePositions) {
                Integer dist = distanceTool.getNattyDistance(pos, redPos);
                if (dist != null && dist < 2) {
                    tooClose = true;
                    break; // too close to another red tile
                }
            }

            if (!tooClose) {
                return pos;
            }
        }

        // For anomalies that couldn't be placed away, try to find a position that is at least 2 moves away from any other anomalies
        if (isAnomaly) {
            for (String pos : availablePositions) {
                boolean tooClose = false;
                for (String redPos : anomalyTilePositions) {
                    Integer dist = distanceTool.getNattyDistance(pos, redPos);
                    if (dist != null && dist < 2) {
                        tooClose = true;
                        break; // too close to another red tile
                    }
                }

                if (!tooClose) {
                    return pos;
                }
            }
        }

        // If spacing fails, just place in the first available position
        return availablePositions.get(0);
    }

    public static void removeNucleusTilesFromDraft(Game game, MiltyDraftManager draftManager) {
        //Really just remove all map tiles
        game.getTileMap().values().forEach(tile -> {
            draftManager.getAll().removeIf(t -> t.getTile().getTileID().equals(tile.getTileID()));
            draftManager.getBlue().removeIf(t -> t.getTile().getTileID().equals(tile.getTileID()));
            draftManager.getRed().removeIf(t -> t.getTile().getTileID().equals(tile.getTileID()));
        });
    }
}
