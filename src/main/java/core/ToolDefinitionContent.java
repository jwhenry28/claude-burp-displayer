package core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a tool definition (available tool) in an LLM request.
 */
public class ToolDefinitionContent extends ContentItem {
    private final String name;
    private final String description;
    private final String inputSchema;

    // Pattern to match <available_skills>...</available_skills> blocks
    private static final Pattern AVAILABLE_SKILLS_PATTERN =
        Pattern.compile("(<available_skills>)(.*?)(</available_skills>)", Pattern.DOTALL);

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
        sb.append(formatDescription(description));
        if (inputSchema != null && !inputSchema.isEmpty()) {
            sb.append("\n\nInput Schema:\n").append(inputSchema);
        }
        return sb.toString();
    }

    /**
     * Formats the description, applying XML indentation to available_skills blocks.
     */
    private String formatDescription(String desc) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }

        Matcher matcher = AVAILABLE_SKILLS_PATTERN.matcher(desc);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String openTag = matcher.group(1);
            String content = matcher.group(2);
            String closeTag = matcher.group(3);

            String formatted = openTag + "\n" + formatXmlContent(content) + closeTag;
            matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Formats XML content with proper indentation.
     */
    private String formatXmlContent(String content) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 1;
        String[] lines = content.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Check if this is a closing tag
            boolean isClosingTag = trimmed.startsWith("</");
            // Check if this is an opening tag (but not self-closing)
            boolean isOpeningTag = trimmed.startsWith("<") && !trimmed.startsWith("</")
                && !trimmed.endsWith("/>");

            // Decrease indent before closing tags
            if (isClosingTag) {
                indentLevel = Math.max(1, indentLevel - 1);
            }

            // Add indentation
            for (int i = 0; i < indentLevel; i++) {
                sb.append(" ");
            }
            sb.append(trimmed).append("\n");

            // Increase indent after opening tags
            if (isOpeningTag && !trimmed.contains("</")) {
                indentLevel++;
            }
        }

        return sb.toString();
    }
}
