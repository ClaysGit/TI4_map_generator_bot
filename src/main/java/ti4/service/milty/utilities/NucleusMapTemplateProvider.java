package ti4.service.milty.utilities;

import ti4.image.Mapper;
import ti4.message.BotLogger;
import ti4.model.MapTemplateModel;

public class NucleusMapTemplateProvider {
    public static MapTemplateModel getNucleusMapTemplateForPlayerCount(int playerCount) {
        switch (playerCount) {
            case 6:
                return Mapper.getMapTemplate("6pStandardNucleus");
            default:
                BotLogger.error("Unsupported player count for Nucleus map template: " + playerCount);
                return null;
        }
    }
}
