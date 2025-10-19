package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;

public class MessageV2Editor {
    private final MessageChannel channel;
    private final GameMessageType messageType;
    private final List<ReplaceMessagePart> replace = new ArrayList<>();

    public MessageV2Editor(MessageChannel channel, GameMessageType messageType) {
        Objects.requireNonNull(channel, "Channel cannot be null");
        this.channel = channel;
        this.messageType = messageType;
    }

    public enum MessagePartType {
        TEXT_DISPLAY,
        BUTTON,
        STRING_SELECT,
        ENTITY_SELECT,
        MEDIA_GALLERY
    }

    public static class ReplaceMessagePart {
        private final Object part;

        @Getter
        private final MessagePartType type;

        /**
         * The key to identify what's being replaced. Use
         * depends on the type:
         * - BUTTONS, STRING_SELECT, ENTITY_SELECT: the custom ID of the component
         * - TEXT_DISPLAY: the starting text the component content
         * - MEDIA_GALLERY: a part of the file name
         */
        @Getter
        private final String replaceKey;

        public ReplaceMessagePart(String oldCustomId, Button part) {
            this.part = part;
            this.type = MessagePartType.BUTTON;
            this.replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldCustomId, StringSelectMenu part) {
            this.part = part;
            this.type = MessagePartType.STRING_SELECT;
            this.replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldCustomId, EntitySelectMenu part) {
            this.part = part;
            this.type = MessagePartType.ENTITY_SELECT;
            this.replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldLineStartsWith, TextDisplay part) {
            this.part = part;
            this.type = MessagePartType.TEXT_DISPLAY;
            this.replaceKey = oldLineStartsWith;
        }

        public ReplaceMessagePart(String oldItemUrlPart, MediaGallery part) {
            this.part = part;
            this.type = MessagePartType.MEDIA_GALLERY;
            this.replaceKey = oldItemUrlPart;
        }

        public Component asComponent() {
            return (Component) part;
        }
    }

    public MessageV2Editor replace(String oldId, Button button) {
        replace.add(new ReplaceMessagePart(oldId, button));
        return this;
    }

    public MessageV2Editor replace(String oldId, StringSelectMenu stringSelectMenu) {
        replace.add(new ReplaceMessagePart(oldId, stringSelectMenu));
        return this;
    }

    public MessageV2Editor replace(String oldId, EntitySelectMenu entitySelectMenu) {
        replace.add(new ReplaceMessagePart(oldId, entitySelectMenu));
        return this;
    }

    public MessageV2Editor replace(String partialFilename, MediaGallery mediaGallery) {
        replace.add(new ReplaceMessagePart(partialFilename, mediaGallery));
        return this;
    }

    /**
     * TODO: Should this be a regex match instead?
     * For text replacement, replace ALL TextDisplay components whose content starts with the provided string
     * @param componentStartsWith A string to test against the start of each TextDisplay. (text displays don't have convenient IDs to look up)
     * @param newContent A replacement line of text.
     */
    public MessageV2Editor replace(String componentStartsWith, TextDisplay newContent) {
        replace.add(new ReplaceMessagePart(componentStartsWith, newContent));
        return this;
    }

    public void apply() {
        // MessagePartComponentReplacer replacer = new MessagePartComponentReplacer(replace);
        // MessageHelper.editV2ByType(channel, messageType, replacer);
    }
}
