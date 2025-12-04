package ui;

import core.*;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic renderer for LLM conversations, provider-agnostic.
 */
public class LLMConversationRenderer {
    private final LLMProvider.ProviderConfig config;

    public LLMConversationRenderer(LLMProvider.ProviderConfig config) {
        this.config = config;
    }

    /**
     * Renders a list of messages into a panel with text areas for search.
     */
    public MessagePanelResult renderMessages(List<ConversationMessage> messages) {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIManager.getColor("Panel.background"));

        List<JTextArea> allTextAreas = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage message = messages.get(i);

            // Add message panel
            MessagePanelResult result = createMessagePanel(message);
            contentPanel.add(result.panel);
            allTextAreas.addAll(result.textAreas);

            // Add separator between messages (except after the last one)
            if (i < messages.size() - 1) {
                JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
                separator.setForeground(UIManager.getColor("Separator.foreground"));
                separator.setBackground(UIManager.getColor("Separator.background"));
                separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                contentPanel.add(Box.createVerticalStrut(5));
                contentPanel.add(separator);
                contentPanel.add(Box.createVerticalStrut(5));
            }
        }

        return new MessagePanelResult(contentPanel, allTextAreas);
    }

    /**
     * Creates a panel for a single conversation message.
     */
    public MessagePanelResult createMessagePanel(ConversationMessage message) {
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messagePanel.setBackground(UIManager.getColor("Panel.background"));
        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<JTextArea> textAreas = new ArrayList<>();

        // Handle system messages differently - make them collapsible
        if (message.getRole() == ConversationMessage.Role.SYSTEM) {
            // Create collapsible system message
            StringBuilder systemContent = new StringBuilder();
            for (ContentItem item : message.getContentItems()) {
                if (item.getType() == ContentItem.ContentType.TEXT) {
                    if (systemContent.length() > 0) {
                        systemContent.append("\n");
                    }
                    systemContent.append(item.getDisplayText());
                }
            }

            UIUtils.CollapsiblePanelResult systemResult = UIUtils.createCollapsiblePanel(
                "System Prompt",
                systemContent.toString(),
                config.systemColor,
                config.systemIcon
            );
            messagePanel.add(systemResult.panel);
            if (systemResult.textArea != null) {
                textAreas.add(systemResult.textArea);
            }
        } else if (message.getRole() == ConversationMessage.Role.TOOLS) {
            // Create collapsible container for all tools
            JLabel toolsLabel = new JLabel("AVAILABLE TOOLS (" + message.getContentItems().size() + ")");
            toolsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            toolsLabel.setForeground(config.toolDefinitionColor);
            toolsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePanel.add(toolsLabel);
            messagePanel.add(Box.createVerticalStrut(5));

            // Each tool definition as a collapsible panel with colored XML
            for (int i = 0; i < message.getContentItems().size(); i++) {
                ContentItem item = message.getContentItems().get(i);
                if (item.getType() == ContentItem.ContentType.TOOL_DEFINITION) {
                    ToolDefinitionContent toolDef = (ToolDefinitionContent) item;
                    // Use colored XML panel for tool definitions (handles <available_skills> etc.)
                    UIUtils.CollapsiblePanelResult toolResult = UIUtils.createColoredXmlCollapsiblePanel(
                        toolDef.getName(),
                        toolDef.getDescription() + (toolDef.getInputSchema() != null && !toolDef.getInputSchema().isEmpty()
                            ? "\n\nInput Schema:\n" + toolDef.getInputSchema() : ""),
                        config.toolDefinitionColor,
                        "🔧"
                    );
                    messagePanel.add(toolResult.panel);
                    if (toolResult.textArea != null) {
                        textAreas.add(toolResult.textArea);
                    }

                    // Add space between tool panels (except after the last one)
                    if (i < message.getContentItems().size() - 1) {
                        messagePanel.add(Box.createVerticalStrut(5));
                    }
                }
            }
        } else {
            // Regular role header with colors
            JLabel roleLabel = new JLabel(message.getRole().getValue().toUpperCase());
            roleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

            Color roleColor = getRoleColor(message.getRole());
            roleLabel.setForeground(roleColor);
            roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePanel.add(roleLabel);
            messagePanel.add(Box.createVerticalStrut(5));

            // Content items
            for (int i = 0; i < message.getContentItems().size(); i++) {
                ContentItem item = message.getContentItems().get(i);

                switch (item.getType()) {
                    case TEXT:
                        JTextArea contentArea = new JTextArea(item.getDisplayText());
                        contentArea.setEditable(false);
                        contentArea.setLineWrap(true);
                        contentArea.setWrapStyleWord(true);
                        contentArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                        contentArea.setBackground(UIManager.getColor("Panel.background"));
                        contentArea.setForeground(UIManager.getColor("Label.foreground"));
                        contentArea.setBorder(null);
                        contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                        messagePanel.add(contentArea);
                        textAreas.add(contentArea);
                        break;

                    case TOOL_CALL:
                        ToolCallContent toolCall = (ToolCallContent) item;
                        UIUtils.CollapsiblePanelResult toolResult = UIUtils.createCollapsiblePanel(
                            "Tool Use: " + toolCall.getToolName(),
                            "ID: " + toolCall.getToolId() + "\nInput: " + toolCall.getToolInput(),
                            config.toolCallColor,
                            config.toolIcon
                        );
                        messagePanel.add(toolResult.panel);
                        if (toolResult.textArea != null) {
                            textAreas.add(toolResult.textArea);
                        }
                        break;

                    case TOOL_RESULT:
                        ToolResultContent toolResultContent = (ToolResultContent) item;
                        UIUtils.CollapsiblePanelResult resultPanel = UIUtils.createCollapsiblePanel(
                            "Tool Result",
                            toolResultContent.getContent(),
                            config.toolResultColor,
                            "📄"
                        );
                        messagePanel.add(resultPanel.panel);
                        if (resultPanel.textArea != null) {
                            textAreas.add(resultPanel.textArea);
                        }
                        break;

                    default:
                        // For other types, just display as text
                        JTextArea defaultArea = new JTextArea(item.getDisplayText());
                        defaultArea.setEditable(false);
                        defaultArea.setLineWrap(true);
                        defaultArea.setWrapStyleWord(true);
                        defaultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                        defaultArea.setBackground(UIManager.getColor("Panel.background"));
                        defaultArea.setForeground(UIManager.getColor("Label.foreground"));
                        defaultArea.setBorder(null);
                        defaultArea.setAlignmentX(Component.LEFT_ALIGNMENT);
                        messagePanel.add(defaultArea);
                        textAreas.add(defaultArea);
                        break;
                }

                // Add space between content items (except after the last one)
                if (i < message.getContentItems().size() - 1) {
                    messagePanel.add(Box.createVerticalStrut(10));
                }
            }
        }

        return new MessagePanelResult(messagePanel, textAreas);
    }

    private Color getRoleColor(ConversationMessage.Role role) {
        switch (role) {
            case USER:
                return config.userColor;
            case ASSISTANT:
                return config.assistantColor;
            case SYSTEM:
                return config.systemColor;
            case TOOL:
                return config.toolCallColor;
            case TOOLS:
                return config.toolDefinitionColor;
            default:
                return UIManager.getColor("Label.foreground");
        }
    }

    /**
     * Result containing a panel and its associated text areas for search.
     */
    public static class MessagePanelResult {
        public final JPanel panel;
        public final List<JTextArea> textAreas;

        public MessagePanelResult(JPanel panel, List<JTextArea> textAreas) {
            this.panel = panel;
            this.textAreas = textAreas;
        }
    }
}