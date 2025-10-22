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

public class MessageV2Editor {
    private final MessageChannel channel;
    private final List<ReplaceMessagePart> replaceByCustomId = new ArrayList<>();
    private final List<ReplaceMessagePart> replaceByPattern = new ArrayList<>();

    public MessageV2Editor(MessageChannel channel) {
        Objects.requireNonNull(channel, "Channel cannot be null");
        this.channel = channel;
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
         * - TEXT_DISPLAY: any text in the component content
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
        replaceByCustomId.add(new ReplaceMessagePart(oldId, button));
        return this;
    }

    public MessageV2Editor replace(String oldId, StringSelectMenu stringSelectMenu) {
        replaceByCustomId.add(new ReplaceMessagePart(oldId, stringSelectMenu));
        return this;
    }

    public MessageV2Editor replace(String oldId, EntitySelectMenu entitySelectMenu) {
        replaceByCustomId.add(new ReplaceMessagePart(oldId, entitySelectMenu));
        return this;
    }

    /**
     * For media gallery replacement, replace MediaGallery components whose filename contains the provided pattern
     * @param filenamePattern A string to test against each MediaGallery item's filenames.
     * @param mediaGallery A replacement MediaGallery.
     */
    public MessageV2Editor replace(String filenamePattern, MediaGallery mediaGallery) {
        replaceByPattern.add(new ReplaceMessagePart(filenamePattern, mediaGallery));
        return this;
    }

    /**
     * For text replacement, replace ALL TextDisplay components whose content matches with the provided regex string
     * @param contentPattern A string to test against each text component.
     * @param newContent A replacement TextDisplay.
     */
    public MessageV2Editor replace(String contentPattern, TextDisplay newContent) {
        replaceByPattern.add(new ReplaceMessagePart(contentPattern, newContent));
        return this;
    }

    /**
     * Replace text content in a message. It's recommended to send text which may be replaced using MessageV2Builder::appendReplaceableText,
     * otherwise this may unintentionally replace additional text near the intended target.
     * @param contentPattern A string to test against each text component.
     * @param newContent The content to replace with.
     */
    public MessageV2Editor replace(String contentPattern, String newContent) {
        return replace(contentPattern, TextDisplay.of(newContent));
    }

    public void apply() {
        MessagePartComponentReplacer replacer = new MessagePartComponentReplacer(replaceByCustomId, replaceByPattern);
        // MessageHelper.editV2ByType(channel, messageType, replacer);
    }
}
