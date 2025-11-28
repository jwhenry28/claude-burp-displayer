package core;

/**
 * Abstract base class for different types of content in a conversation message.
 */
public abstract class ContentItem {
    public enum ContentType {
        TEXT,
        TOOL_CALL,
        TOOL_RESULT,
        IMAGE,
        THINKING,
        TOOL_DEFINITION
    }

    protected final ContentType type;

    public ContentItem(ContentType type) {
        this.type = type;
    }

    public ContentType getType() {
        return type;
    }

    public abstract String getDisplayText();
}