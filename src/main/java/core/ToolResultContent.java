package core;

/**
 * Represents a tool result in a conversation message.
 */
public class ToolResultContent extends ContentItem {
    private final String toolUseId;
    private final String content;

    public ToolResultContent(String toolUseId, String content) {
        super(ContentType.TOOL_RESULT);
        this.toolUseId = toolUseId;
        this.content = content;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String getDisplayText() {
        return content;
    }
}