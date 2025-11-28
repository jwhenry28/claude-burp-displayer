package core;

/**
 * Represents text content in a conversation message.
 */
public class TextContent extends ContentItem {
    private final String text;

    public TextContent(String text) {
        super(ContentType.TEXT);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String getDisplayText() {
        return text;
    }
}