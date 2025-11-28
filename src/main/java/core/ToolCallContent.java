package core;

/**
 * Represents a tool call in a conversation message.
 */
public class ToolCallContent extends ContentItem {
    private final String toolId;
    private final String toolName;
    private final String toolInput;

    public ToolCallContent(String toolId, String toolName, String toolInput) {
        super(ContentType.TOOL_CALL);
        this.toolId = toolId;
        this.toolName = toolName;
        this.toolInput = toolInput;
    }

    public String getToolId() {
        return toolId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolInput() {
        return toolInput;
    }

    @Override
    public String getDisplayText() {
        return String.format("Tool Call: %s\nID: %s\nInput: %s", toolName, toolId, toolInput);
    }
}