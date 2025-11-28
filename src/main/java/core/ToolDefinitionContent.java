package core;

/**
 * Represents a tool definition (available tool) in an LLM request.
 */
public class ToolDefinitionContent extends ContentItem {
    private final String name;
    private final String description;
    private final String inputSchema;

    public ToolDefinitionContent(String name, String description, String inputSchema) {
        super(ContentType.TOOL_DEFINITION);
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    @Override
    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(description);
        if (inputSchema != null && !inputSchema.isEmpty()) {
            sb.append("\n\nInput Schema:\n").append(inputSchema);
        }
        return sb.toString();
    }
}
