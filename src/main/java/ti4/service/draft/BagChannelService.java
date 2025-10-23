package ti4.service.draft;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.spring.jda.JdaService;

@UtilityClass
public class BagChannelService {
    private static final Pattern FORWARD_SLASH_PATTERN = Pattern.compile("/");

    public ThreadChannel regenerateBagChannel(Game owner, Player player) {
        TextChannel actionsChannel = owner.getMainGameChannel();
        if (actionsChannel == null) {
            BotLogger.warning(
                    new LogOrigin(player),
                    "`Helper.getBagChannel`: actionsChannel is null for game, or community game private channel not set: "
                            + owner.getName());
            return null;
        }

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-"
                + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-"
                    + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("") + "-private";
        }

        ThreadChannel existingChannel = findExistingBagChannel(player, threadName);

        if (existingChannel != null) {
            if (existingChannel.isArchived()) {
                existingChannel.getManager().setArchived(false).submit().join();
            }

            // Clear out all messages from the existing thread
            existingChannel
                    .getHistory()
                    .retrievePast(100)
                    .submit()
                    .thenAccept(m -> {
                        if (m.size() > 1) {
                            existingChannel.deleteMessages(m).submit().join();
                        }
                    })
                    .join();
            return existingChannel;
        }

        // CREATE NEW THREAD
        // Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!owner.isFowMode());
        if (owner.getName().contains("pbd100") || owner.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = ((TextChannel) player.getCorrectChannel())
                .createThreadChannel(threadName, isPrivateChannel)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction = threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel =
                threadAction.complete(); // Must `complete` if we're using this channel as part of an interaction that
        // saves
        // the
        // game
        player.setBagInfoThreadID(threadChannel.getId());
        return threadChannel;
    }

    public ThreadChannel findExistingBagChannel(Game owner, Player player) {
        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-"
                + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-"
                    + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("") + "-private";
        }
        return findExistingBagChannel(player, threadName);
    }

    private ThreadChannel findExistingBagChannel(Player player, String threadName) {
        TextChannel actionsChannel = (TextChannel) player.getCorrectChannel();
        // ATTEMPT TO FIND BY ID
        String bagInfoThread = player.getBagInfoThreadID();
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = JdaService.jda.getThreadChannelById(bagInfoThread);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                // Must `complete` if we're using this channel as part of an interaction that
                // saves the game
                List<ThreadChannel> hiddenThreadChannels =
                        actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(player),
                    "`Player.getBagInfoThread`: Could not find existing Bag Info thead using ID: " + bagInfoThread
                            + " for potential thread name: " + threadName,
                    e);
        }

        // ATTEMPT TO FIND BY NAME
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = JdaService.jda.getThreadChannelById(bagInfoThread);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                // Must `complete` if we're using this channel as part of an interaction that
                // saves the game
                List<ThreadChannel> hiddenThreadChannels =
                        actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(
                    new LogOrigin(player),
                    "`Player.getBagInfoThread`: Could not find existing Bag Info thead using name: " + threadName,
                    e);
        }
        return null;
    }
}
