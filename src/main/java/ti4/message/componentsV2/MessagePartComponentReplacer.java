package ti4.message.componentsV2;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.IReplaceable;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.message.MessageHelper.TrackingComponentReplacer;
import ti4.message.MessageV2Editor.MessagePartType;
import ti4.message.MessageV2Editor.ReplaceMessagePart;
import ti4.message.logging.BotLogger;

public class MessagePartComponentReplacer implements TrackingComponentReplacer {
    private final Map<String, ReplaceMessagePart> replacements;
    private boolean madeChanges = false;

    public MessagePartComponentReplacer(List<ReplaceMessagePart> replacements) {
        this.replacements =
                replacements.stream().collect(Collectors.toMap(ReplaceMessagePart::getReplaceKey, Function.identity()));
    }

    public void startingChanges() {
        madeChanges = false;
    }

    /**
     * @return true if any changes were made, false otherwise.
     */
    public Boolean finishedChanges() {
        return madeChanges;
    }

    /**
     * The apply method of the ComponentReplacer interface is called as part
     * of the MessageComponentTree.replace method. It is called for each component
     * in the message's component tree. If the input component is returned, nothing happens.
     * If null is returned, the component is removed. If a different component is returned,
     * the component is replaced.
     *
     * This also populates the changedMessages set when any action is performed; this
     * allows the caller to know if any changes were made at all. (to help save on PATCH
     * requests)
     */
    @Override
    public Component apply(Component curComponent) {
        ReplaceMessagePart replacement = tryGetReplacementByCustomId(curComponent);
        if (replacement == null) {
            replacement = tryGetReplacementByString(curComponent);
        }
        if (replacement == null && curComponent instanceof IReplaceable) {
            Component validReplacement = getValidReplacement(curComponent);
            if (validReplacement != curComponent) {
                madeChanges = true;
            }
            return validReplacement;
        }
        if (replacement == null) {
            return curComponent;
        }
        if (!canReplace(curComponent, replacement.asComponent())) {
            BotLogger.warning("Cannot replace component of type "
                    + curComponent.getClass().getName() + " with type "
                    + replacement.asComponent().getClass().getName());
            return curComponent;
        }
        madeChanges = true;
        return replacement.asComponent();
    }

    /**
     * Because this "replacer" also removes components, we need to
     * pre-emptively replace containers that would be made empty by child removal.
     * The replacement logic doesn't handle this natively.
     * @param curComponent A component that is IReplaceable
     * @return the input component if it would still be valid, otherwise null
     */
    private Component getValidReplacement(Component curComponent) {
        if (curComponent == null) {
            return null;
        }

        List<? extends Component> children =
                switch (curComponent) {
                    case ActionRow actionRow -> actionRow.getComponents();
                    case Container container -> container.getComponents();
                    case Section section -> section.getContentComponents();
                    case Label label -> label.getChild() == null ? List.of() : List.of(label.getChild());
                    default ->
                        throw new IllegalArgumentException("Unknown IReplaceable component type: "
                                + curComponent.getClass().getName());
                };

        if (children.isEmpty()) {
            return null;
        }

        // In all cases, we just need to ensure that at least one
        // child is still present.
        for (Component child : children) {
            Component replacement = apply(child);
            if (replacement != null) {
                return curComponent;
            }
        }

        return null;
    }

    private ReplaceMessagePart tryGetReplacementByString(Component curComponent) {
        if (curComponent == null) {
            return null;
        }
        for (ReplaceMessagePart replacement : replacements.values()) {
            if (isCustomIdPartType(replacement.getType())) {
                // This replacement is for a custom ID type; skip it.
                continue;
            }
            if (replacement.getType() == MessagePartType.TEXT_DISPLAY
                    && curComponent instanceof TextDisplay textDisplay) {
                if (matchText(textDisplay, replacement.getReplaceKey())) {
                    return replacement;
                }
            }
            if (replacement.getType() == MessagePartType.MEDIA_GALLERY
                    && curComponent instanceof MediaGallery mediaGallery) {
                if (matchText(mediaGallery, replacement.getReplaceKey())) {
                    return replacement;
                }
            }
        }

        return null;
    }

    private ReplaceMessagePart tryGetReplacementByCustomId(Component curComponent) {
        String curId = getCustomId(curComponent);
        if (curId == null) {
            return null;
        }
        ReplaceMessagePart replacement = replacements.getOrDefault(curId, null);
        if (replacement == null) {
            return null;
        }
        if (!isCustomIdPartType(replacement.getType())) {
            // Prevent accidental matches against parts that don't use custom IDs.
            return null;
        }
        return replacement;
    }

    private static boolean isCustomIdPartType(MessagePartType type) {
        return switch (type) {
            case BUTTON, STRING_SELECT, ENTITY_SELECT -> true;
            default -> false;
        };
    }

    private static String getCustomId(Component component) {
        return switch (component) {
            case Button button -> button.getCustomId();
            case StringSelectMenu stringSelectMenu -> stringSelectMenu.getCustomId();
            case EntitySelectMenu entitySelectMenu -> entitySelectMenu.getCustomId();
            default -> null;
        };
    }

    private static boolean matchText(TextDisplay textDisplay, String startsWith) {
        if (textDisplay == null || startsWith == null) {
            return false;
        }
        String content = textDisplay.getContent();
        if (content == null) {
            return false;
        }
        return content.startsWith(startsWith);
    }

    private static boolean matchText(MediaGallery mediaGallery, String contains) {
        if (mediaGallery == null || contains == null) {
            return false;
        }
        Predicate<MediaGalleryItem> matchFunc = (item) -> {
            return item.getUrl() != null && item.getUrl().contains(contains);
        };
        return mediaGallery.getItems().stream().anyMatch(matchFunc);
    }

    private static boolean canReplace(Component current, Component replacement) {
        if (current == null || replacement == null) {
            return true;
        }
        if (!current.getClass().equals(replacement.getClass())) {
            return false;
        }
        return true;
    }
}
