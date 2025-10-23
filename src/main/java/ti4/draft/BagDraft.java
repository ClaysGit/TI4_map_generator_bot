package ti4.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.buttons.Buttons;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

public abstract class BagDraft {
    private final Game owner;

    public static BagDraft GenerateDraft(String draftType, Game game) {
        if ("franken".equals(draftType)) {
            return new FrankenDraft(game);
        }
        if ("powered_franken".equals(draftType)) {
            return new PoweredFrankenDraft(game);
        }
        if ("onepick_franken".equals(draftType)) {
            return new OnePickFrankenDraft(game);
        }
        if ("poweredonepick_franken".equals(draftType)) {
            return new PoweredOnePickFrankenDraft(game);
        }
        if ("twilights_fall".equals(draftType)) {
            return new TwilightsFallFrankenDraft(game);
        }
        return null;
    }

    BagDraft(Game owner) {
        this.owner = owner;
    }

    public abstract int getItemLimitForCategory(DraftItem.Category category);

    public abstract String getSaveString();

    public abstract List<DraftBag> generateBags(Game game);

    public abstract int getBagSize();

    public int getPicksFromFirstBag() {
        return 3;
    }

    public int getPicksFromNextBags() {
        return 2;
    }

    public boolean isDraftStageComplete() {
        List<Player> players = owner.getRealPlayers();

        for (Player p : players) {
            if (!p.getCurrentDraftBag().Contents.isEmpty()
                    || !p.getDraftQueue().Contents.isEmpty()) {
                if (p.getDraftHand().Contents.size() != owner.getFrankenBagSize()) {
                    return false;
                }
            }
            if (p.getDraftHand().Contents.size() != owner.getFrankenBagSize()) {
                return false;
            }
        }
        return true;
    }

    public void passBags() {
        List<Player> players = owner.getRealPlayers();
        DraftBag firstPlayerBag = players.getFirst().getCurrentDraftBag();
        for (int i = 0; i < players.size() - 1; i++) {
            giveBagToPlayer(players.get(i + 1).getCurrentDraftBag(), players.get(i));
        }
        giveBagToPlayer(firstPlayerBag, players.getLast());
    }

    public void giveBagToPlayer(DraftBag bag, Player player) {
        player.setCurrentDraftBag(bag);
        boolean newBagCanBeDraftedFrom = false;
        for (DraftItem item : bag.Contents) {
            if (item.isDraftable(player)) {
                newBagCanBeDraftedFrom = true;
                break;
            }
        }
        player.setReadyToPassBag(!newBagCanBeDraftedFrom);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you have been passed a new draft bag!",
                Buttons.gray(FrankenDraftBagService.ACTION_NAME + "show_bag", "Click here to show your current bag"));
    }

    public boolean allPlayersReadyToPass() {
        for (Player p : owner.getRealPlayers()) {
            if (!playerHasDraftableItemInBag(p) && !playerHasItemInQueue(p)) {
                setPlayerReadyToPass(p, true);
            }
        }
        return owner.getRealPlayers().stream().allMatch(Player::isReadyToPassBag);
    }

    public boolean playerHasDraftableItemInBag(Player player) {
        return player.getCurrentDraftBag().Contents.stream().anyMatch(draftItem -> draftItem.isDraftable(player));
    }

    public void setPlayerReadyToPass(Player player, boolean ready) {
        if (ready && !player.isReadyToPassBag()) {
            player.setReadyToPassBag(true);
            FrankenDraftBagService.updateDraftStatusMessage(owner);
        }
        player.setReadyToPassBag(ready);
    }

    public String getLongBagRepresentation(DraftBag bag, Game game) {
        StringBuilder sb = new StringBuilder();
        for (DraftItem.Category cat : DraftItem.Category.values()) {
            if (this instanceof FrankenDraft) {
                if (FrankenDraft.getItemLimitForCategory(cat, game) > 0) {
                    sb.append(FrankenDraftBagService.getLongCategoryRepresentation(this, bag, cat, game));
                }
            } else {
                if (this.getItemLimitForCategory(cat) > 0) {
                    sb.append(FrankenDraftBagService.getLongCategoryRepresentation(this, bag, cat, game));
                }
            }
        }
        sb.append("**Total Cards: ").append(bag.Contents.size()).append("**\n");
        return sb.toString();
    }

    public boolean playerHasItemInQueue(Player p) {
        return !p.getDraftQueue().Contents.isEmpty();
    }

    @JsonIgnore
    public String getDraftStatusMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("### __Draft Status__:\n");
        for (Player player : owner.getRealPlayers()) {
            sb.append("> ");
            if (player.isReadyToPassBag()) {
                sb.append("✅");
            } else {
                sb.append("❌");
            }
            if (owner.getRealPlayers().size() > 10) {
                sb.append(player.getFactionEmoji());
            } else {
                sb.append(player.getRepresentationNoPing());
            }
            sb.append(" (")
                    .append(player.getDraftHand().Contents.size())
                    .append("/")
                    .append(owner.getFrankenBagSize())
                    .append(")");
            sb.append("\n");
        }
        return sb.toString();
    }
}
