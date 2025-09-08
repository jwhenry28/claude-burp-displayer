import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ClaudeUtils {
    public static boolean isClaudeMessage(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return false;
        }

        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();

        if (request == null) {
            return false;
        }

        // Check if request method is POST
        if (!request.method().equals("POST")) {
            return false;
        }

        // Check if request host is api.anthropic.com
        if (!request.httpService().host().equals("api.anthropic.com")) {
            return false;
        }

        // Check if request content type is application/json
        String contentType = request.headerValue("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            return false;
        }

        // Check if request URL starts with /v1/messages
        if (!request.path().startsWith("/v1/messages")) {
            return false;
        }

        // Check if response type is text/event-stream (with optional charset)
        if (response != null) {
            String responseContentType = response.headerValue("Content-Type");
            if (responseContentType != null && 
                responseContentType.toLowerCase().startsWith("text/event-stream")) {
                return true;
            }
        }

        return false;
    }
    
    public static class MessageContent {
        public String role;
        public List<ContentItem> contentItems;
        
        public MessageContent(String role) {
            this.role = role;
            this.contentItems = new ArrayList<>();
        }
    }

    public static class ContentItem {
        public String type;
        public String text;
        public String toolName;
        public String toolId;
        public String toolUseId;
        public String toolInput;
        public String toolContent;
        
        public ContentItem(String type) {
            this.type = type;
        }
        
        public static ContentItem createText(String text) {
            ContentItem item = new ContentItem("text");
            item.text = text;
            return item;
        }
        
        public static ContentItem createToolUse(String id, String name, String input) {
            ContentItem item = new ContentItem("tool_use");
            item.toolId = id;
            item.toolName = name;
            item.toolInput = input;
            return item;
        }
        
        public static ContentItem createToolResult(String toolUseId, String content) {
            ContentItem item = new ContentItem("tool_result");
            item.toolUseId = toolUseId;
            item.toolContent = content;
            return item;
        }
    }
    
    public static class ContentBlock {
        public String type;
        public String content;
        public String toolName;
        public String toolId;
        public String toolInput;
        
        public ContentBlock(String type) {
            this.type = type;
            this.content = "";
        }
    }
    
    public static List<ContentBlock> parseSSEResponse(HttpRequestResponse requestResponse) {
        List<ContentBlock> blocks = new ArrayList<>();
        
        if (requestResponse == null || requestResponse.response() == null) {
            return blocks;
        }
        
        HttpResponse response = requestResponse.response();
        String responseBody = response.bodyToString();
        
        if (responseBody == null || responseBody.isEmpty()) {
            return blocks;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<Integer, ContentBlock> activeBlocks = new HashMap<>();
            
            String[] lines = responseBody.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                
                if (!line.startsWith("data:")) {
                    continue;
                }
                
                String jsonStr = line.substring(5).trim();
                if (jsonStr.isEmpty()) {
                    continue;
                }
                
                try {
                    JsonNode data = mapper.readTree(jsonStr);
                    String eventType = data.has("type") ? data.get("type").asText() : "";
                    
                    if ("content_block_start".equals(eventType)) {
                        int index = data.has("index") ? data.get("index").asInt() : 0;
                        JsonNode contentBlock = data.get("content_block");
                        
                        if (contentBlock != null) {
                            String blockType = contentBlock.has("type") ? contentBlock.get("type").asText() : "";
                            ContentBlock block = new ContentBlock(blockType);
                            
                            if ("tool_use".equals(blockType)) {
                                block.toolId = contentBlock.has("id") ? contentBlock.get("id").asText() : "";
                                block.toolName = contentBlock.has("name") ? contentBlock.get("name").asText() : "";
                            }
                            
                            activeBlocks.put(index, block);
                        }
                        
                    } else if ("content_block_delta".equals(eventType)) {
                        int index = data.has("index") ? data.get("index").asInt() : 0;
                        JsonNode delta = data.get("delta");
                        
                        if (delta != null && activeBlocks.containsKey(index)) {
                            ContentBlock block = activeBlocks.get(index);
                            
                            if (delta.has("partial_json")) {
                                block.content += delta.get("partial_json").asText();
                            }
                            
                            if (delta.has("text")) {
                                block.content += delta.get("text").asText();
                            }
                            
                            if (delta.has("thinking")) {
                                block.content += delta.get("thinking").asText();
                            }
                        }
                        
                    } else if ("content_block_stop".equals(eventType)) {
                        int index = data.has("index") ? data.get("index").asInt() : 0;
                        
                        if (activeBlocks.containsKey(index)) {
                            ContentBlock block = activeBlocks.remove(index);
                            if ("tool_use".equals(block.type)) {
                                block.toolInput = block.content;
                            }
                            blocks.add(block);
                        }
                    }
                    
                } catch (Exception e) {
                    // Skip invalid JSON lines
                }
            }
            
        } catch (Exception e) {
            // Return empty list on error
        }
        
        return blocks;
    }
    
    public static List<MessageContent> extractRequestMessages(HttpRequestResponse requestResponse) {
        List<MessageContent> messages = new ArrayList<>();
        
        if (!isClaudeMessage(requestResponse)) {
            return messages;
        }

        HttpRequest request = requestResponse.request();
        String bodyString = request.bodyToString();
        
        if (bodyString == null || bodyString.isEmpty()) {
            return messages;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bodyString);
            
            // Handle system message first if it exists
            JsonNode systemArray = root.get("system");
            if (systemArray != null && systemArray.isArray()) {
                MessageContent systemMessage = new MessageContent("system");
                
                for (JsonNode systemItem : systemArray) {
                    if (systemItem.has("type")) {
                        String type = systemItem.get("type").asText();
                        if ("text".equals(type) && systemItem.has("text")) {
                            String text = systemItem.get("text").asText();
                            systemMessage.contentItems.add(ContentItem.createText(text));
                        }
                    } else if (systemItem.has("text")) {
                        String text = systemItem.get("text").asText();
                        systemMessage.contentItems.add(ContentItem.createText(text));
                    }
                }
                
                if (!systemMessage.contentItems.isEmpty()) {
                    messages.add(systemMessage);
                }
            }
            
            JsonNode messagesArray = root.get("messages");
            
            if (messagesArray == null || !messagesArray.isArray()) {
                return messages;
            }
            
            for (JsonNode messageNode : messagesArray) {
                String role = messageNode.has("role") ? messageNode.get("role").asText() : "unknown";
                MessageContent messageContent = new MessageContent(role);
                
                JsonNode contentNode = messageNode.get("content");
                if (contentNode != null) {
                    if (contentNode.isArray()) {
                        // Handle array of content items
                        for (JsonNode contentItem : contentNode) {
                            if (contentItem.has("type")) {
                                String type = contentItem.get("type").asText();
                                
                                if ("text".equals(type) && contentItem.has("text")) {
                                    String text = contentItem.get("text").asText();
                                    messageContent.contentItems.add(ContentItem.createText(text));
                                    
                                } else if ("tool_use".equals(type)) {
                                    String id = contentItem.has("id") ? contentItem.get("id").asText() : "unknown";
                                    String name = contentItem.has("name") ? contentItem.get("name").asText() : "unknown";
                                    String input = contentItem.has("input") ? contentItem.get("input").toString() : "{}";
                                    messageContent.contentItems.add(ContentItem.createToolUse(id, name, input));
                                    
                                } else if ("tool_result".equals(type)) {
                                    String toolUseId = contentItem.has("tool_use_id") ? contentItem.get("tool_use_id").asText() : "unknown";
                                    String content = contentItem.has("content") ? contentItem.get("content").asText() : "";
                                    messageContent.contentItems.add(ContentItem.createToolResult(toolUseId, content));
                                }
                            } else if (contentItem.has("text")) {
                                String text = contentItem.get("text").asText();
                                messageContent.contentItems.add(ContentItem.createText(text));
                            }
                        }
                    } else if (contentNode.isTextual()) {
                        // Handle simple string content
                        String text = contentNode.asText();
                        messageContent.contentItems.add(ContentItem.createText(text));
                    }
                }
                
                messages.add(messageContent);
            }
            
        } catch (Exception e) {
            // Return empty list on error
        }
        
        return messages;
    }
    
    public static MessageContent convertResponseToMessage(List<ContentBlock> blocks) {
        MessageContent message = new MessageContent("assistant");
        
        for (ContentBlock block : blocks) {
            if ("text".equals(block.type)) {
                message.contentItems.add(ContentItem.createText(block.content));
            } else if ("tool_use".equals(block.type)) {
                message.contentItems.add(ContentItem.createToolUse(block.toolId, block.toolName, block.toolInput));
            }
        }
        
        return message;
    }
    
    public static class MessagePanelResult {
        public final JPanel panel;
        public final List<JTextArea> textAreas;
        
        public MessagePanelResult(JPanel panel, List<JTextArea> textAreas) {
            this.panel = panel;
            this.textAreas = textAreas;
        }
    }
    
    public static MessagePanelResult createMessagePanel(MessageContent message) {
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messagePanel.setBackground(UIManager.getColor("Panel.background"));
        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        List<JTextArea> textAreas = new ArrayList<>();
        
        // Handle system messages differently - make them collapsible
        if (message.role.equals("system")) {
            // Create collapsible system message
            StringBuilder systemContent = new StringBuilder();
            for (ContentItem item : message.contentItems) {
                if ("text".equals(item.type)) {
                    if (systemContent.length() > 0) {
                        systemContent.append("\n");
                    }
                    systemContent.append(item.text);
                }
            }
            
            CollapsibleToolPanelResult systemResult = createCollapsibleToolPanel(
                "🎯 System Prompt",
                systemContent.toString(),
                new Color(255, 140, 0) // Dark Orange
            );
            messagePanel.add(systemResult.panel);
            if (systemResult.textArea != null) {
                textAreas.add(systemResult.textArea);
            }
        } else {
            // Regular role header with colors that work with different themes
            JLabel roleLabel = new JLabel(message.role.toUpperCase());
            roleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            Color roleColor;
            if (message.role.equals("user")) {
                roleColor = new Color(70, 130, 180); // Steel Blue
            } else {
                roleColor = new Color(138, 43, 226); // Blue Violet
            }
            roleLabel.setForeground(roleColor);
            roleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePanel.add(roleLabel);
            messagePanel.add(Box.createVerticalStrut(5));
        }
        
        // Content items (skip for system messages as they're already handled)
        if (!message.role.equals("system")) {
            for (int i = 0; i < message.contentItems.size(); i++) {
                ContentItem item = message.contentItems.get(i);
                
                if ("text".equals(item.type)) {
                // Regular text content
                JTextArea contentArea = new JTextArea(item.text);
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
                
            } else if ("tool_use".equals(item.type)) {
                // Collapsible tool use
                CollapsibleToolPanelResult toolResult = createCollapsibleToolPanel(
                    "🔧 Tool Use: " + item.toolName, 
                    "ID: " + item.toolId + "\nInput: " + item.toolInput,
                    new Color(70, 130, 180)
                );
                messagePanel.add(toolResult.panel);
                if (toolResult.textArea != null) {
                    textAreas.add(toolResult.textArea);
                }
                
            } else if ("tool_result".equals(item.type)) {
                // Collapsible tool result
                CollapsibleToolPanelResult toolResult = createCollapsibleToolPanel(
                    "📄 Tool Result", 
                    item.toolContent,
                    new Color(138, 43, 226)
                );
                messagePanel.add(toolResult.panel);
                if (toolResult.textArea != null) {
                    textAreas.add(toolResult.textArea);
                }
            }
            
            // Add space between content items (except after the last one)
            if (i < message.contentItems.size() - 1) {
                messagePanel.add(Box.createVerticalStrut(10));
            }
        }
        }
        
        return new MessagePanelResult(messagePanel, textAreas);
    }

    public static class CollapsibleToolPanelResult {
        public final JPanel panel;
        public final JTextArea textArea;
        
        public CollapsibleToolPanelResult(JPanel panel, JTextArea textArea) {
            this.panel = panel;
            this.textArea = textArea;
        }
    }
    
    public static CollapsibleToolPanelResult createCollapsibleToolPanel(String title, String content, Color titleColor) {
        JPanel containerPanel = new JPanel();
        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));
        containerPanel.setBackground(UIManager.getColor("Panel.background"));
        containerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Create clickable header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIManager.getColor("Panel.background"));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(titleColor, 1),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel titleLabel = new JLabel("▶ " + title);
        titleLabel.setForeground(titleColor);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        titleLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Create content panel (initially hidden)
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(UIManager.getColor("Panel.background"));
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(titleColor.darker(), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        contentPanel.setVisible(false);
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JTextArea contentArea = new JTextArea(content);
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        contentArea.setBackground(UIManager.getColor("Panel.background"));
        contentArea.setForeground(UIManager.getColor("Label.foreground"));
        contentArea.setBorder(null);
        
        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setPreferredSize(new Dimension(400, Math.min(content.length() / 4 + 50, 200)));
        scrollPane.setBorder(null);
        scrollPane.setBackground(UIManager.getColor("Panel.background"));
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Add click listener to toggle visibility
        headerPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean isVisible = contentPanel.isVisible();
                contentPanel.setVisible(!isVisible);
                titleLabel.setText((isVisible ? "▶ " : "▼ ") + title);
                containerPanel.revalidate();
                containerPanel.repaint();
            }
        });
        
        containerPanel.add(headerPanel);
        containerPanel.add(contentPanel);
        
        return new CollapsibleToolPanelResult(containerPanel, contentArea);
    }
    
    public static class HighlightInfo {
        public final JTextArea textArea;
        public final int start;
        public final int end;
        
        public HighlightInfo(JTextArea textArea, int start, int end) {
            this.textArea = textArea;
            this.start = start;
            this.end = end;
        }
    }
    
    public static class SearchHighlighter {
        private final List<JTextArea> textAreas;
        private final Highlighter.HighlightPainter painter;
        private final Highlighter.HighlightPainter currentPainter;
        private final List<HighlightInfo> highlightInfos;
        private int currentIndex = -1;
        
        public SearchHighlighter(List<JTextArea> textAreas) {
            this.textAreas = textAreas;
            this.painter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
            this.currentPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.ORANGE);
            this.highlightInfos = new ArrayList<>();
        }
        
        public void clearHighlights() {
            for (JTextArea textArea : textAreas) {
                textArea.getHighlighter().removeAllHighlights();
            }
            highlightInfos.clear();
            currentIndex = -1;
        }
        
        public int searchAndHighlight(String searchText, boolean useRegex, boolean caseSensitive) {
            clearHighlights();
            
            if (searchText == null || searchText.trim().isEmpty()) {
                return 0;
            }
            
            Pattern pattern;
            try {
                if (useRegex) {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(searchText, flags);
                } else {
                    String escapedText = Pattern.quote(searchText);
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(escapedText, flags);
                }
            } catch (PatternSyntaxException e) {
                return 0; // Invalid regex
            }
            
            for (JTextArea textArea : textAreas) {
                String text = textArea.getText();
                if (text == null || text.isEmpty()) continue;
                
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    highlightInfos.add(new HighlightInfo(textArea, matcher.start(), matcher.end()));
                }
            }
            
            // Apply highlights with all in yellow initially
            applyHighlights();
            
            // Set to -1 so the first navigateToNext() call will set it to 0
            currentIndex = -1;
            
            return highlightInfos.size();
        }
        
        private void applyHighlights() {
            // Clear existing highlights
            for (JTextArea textArea : textAreas) {
                textArea.getHighlighter().removeAllHighlights();
            }
            
            // Apply highlights with appropriate colors
            for (int i = 0; i < highlightInfos.size(); i++) {
                HighlightInfo info = highlightInfos.get(i);
                Highlighter.HighlightPainter paintToUse = (i == currentIndex) ? currentPainter : painter;
                
                try {
                    info.textArea.getHighlighter().addHighlight(info.start, info.end, paintToUse);
                } catch (BadLocationException e) {
                    // Skip this highlight
                }
            }
        }
        
        public void navigateToNext(JScrollPane scrollPane) {
            if (highlightInfos.isEmpty()) return;
            
            currentIndex = (currentIndex + 1) % highlightInfos.size();
            applyHighlights();
            scrollToHighlight(scrollPane, currentIndex);
        }
        
        public void navigateToPrevious(JScrollPane scrollPane) {
            if (highlightInfos.isEmpty()) return;
            
            currentIndex = currentIndex <= 0 ? highlightInfos.size() - 1 : currentIndex - 1;
            applyHighlights();
            scrollToHighlight(scrollPane, currentIndex);
        }
        
        public int getCurrentIndex() {
            return currentIndex;
        }
        
        public int getTotalMatches() {
            return highlightInfos.size();
        }
        
        private void scrollToHighlight(JScrollPane scrollPane, int index) {
            if (index < 0 || index >= highlightInfos.size()) return;
            
            HighlightInfo info = highlightInfos.get(index);
            
            // Check if the text area is inside a collapsed panel and expand it if needed
            expandCollapsedPanelIfNeeded(info.textArea);
            
            try {
                // Calculate the position to scroll to
                Rectangle rect = info.textArea.modelToView(info.start);
                if (rect != null) {
                    // Convert to parent coordinates
                    Point location = SwingUtilities.convertPoint(info.textArea, rect.getLocation(), scrollPane.getViewport().getView());
                    scrollPane.getViewport().setViewPosition(new Point(0, Math.max(0, location.y - 50)));
                }
            } catch (BadLocationException e) {
                // Skip scrolling for this highlight
            }
        }
        
        private void expandCollapsedPanelIfNeeded(JTextArea textArea) {
            // Walk up the component hierarchy to find collapsible panels
            Component current = textArea;
            while (current != null) {
                current = current.getParent();
                
                // Look for the contentPanel that might be invisible
                if (current instanceof JPanel && !current.isVisible()) {
                    JPanel contentPanel = (JPanel) current;
                    
                    // Check if this panel has a sibling header panel with the characteristic structure
                    Container parent = contentPanel.getParent();
                    if (parent != null) {
                        Component[] siblings = parent.getComponents();
                        for (Component sibling : siblings) {
                            if (sibling instanceof JPanel && sibling != contentPanel) {
                                JPanel potentialHeader = (JPanel) sibling;
                                
                                // Look for the title label with ▶ or ▼
                                JLabel titleLabel = findTitleLabel(potentialHeader);
                                if (titleLabel != null && titleLabel.getText().contains("▶")) {
                                    // This is a collapsed panel, expand it
                                    contentPanel.setVisible(true);
                                    String titleText = titleLabel.getText().substring(2); // Remove ▶ 
                                    titleLabel.setText("▼ " + titleText);
                                    parent.revalidate();
                                    parent.repaint();
                                    return; // Found and expanded, no need to continue up the hierarchy
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private JLabel findTitleLabel(Container container) {
            for (Component component : container.getComponents()) {
                if (component instanceof JLabel) {
                    JLabel label = (JLabel) component;
                    String text = label.getText();
                    if (text != null && (text.startsWith("▶") || text.startsWith("▼"))) {
                        return label;
                    }
                }
                if (component instanceof Container) {
                    JLabel found = findTitleLabel((Container) component);
                    if (found != null) return found;
                }
            }
            return null;
        }
    }
    
    public static JPanel createSearchPanel(SearchHighlighter highlighter, JScrollPane scrollPane) {
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(UIManager.getColor("Panel.background"));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Search input field
        JTextField searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(200, 25));
        
        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        optionsPanel.setBackground(UIManager.getColor("Panel.background"));
        
        JCheckBox regexCheckBox = new JCheckBox("Regex");
        JCheckBox caseSensitiveCheckBox = new JCheckBox("Case sensitive");
        regexCheckBox.setBackground(UIManager.getColor("Panel.background"));
        caseSensitiveCheckBox.setBackground(UIManager.getColor("Panel.background"));
        
        // Navigation buttons
        JButton prevButton = new JButton("↑");
        JButton nextButton = new JButton("↓");
        JButton closeButton = new JButton("✕");
        
        prevButton.setPreferredSize(new Dimension(30, 25));
        nextButton.setPreferredSize(new Dimension(30, 25));
        closeButton.setPreferredSize(new Dimension(30, 25));
        
        // Results label
        JLabel resultsLabel = new JLabel("");
        resultsLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        // Helper method to update results label
        Runnable updateResultsLabel = () -> {
            int total = highlighter.getTotalMatches();
            int current = highlighter.getCurrentIndex();
            String searchText = searchField.getText();
            
            if (total > 0) {
                resultsLabel.setText((current + 1) + "/" + total + " matches");
            } else if (searchText.isEmpty()) {
                resultsLabel.setText("");
            } else {
                resultsLabel.setText("No matches");
            }
        };
        
        // Search functionality
        DocumentListener searchListener = new DocumentListener() {
            private void performSearch() {
                String searchText = searchField.getText();
                boolean useRegex = regexCheckBox.isSelected();
                boolean caseSensitive = caseSensitiveCheckBox.isSelected();
                
                int results = highlighter.searchAndHighlight(searchText, useRegex, caseSensitive);
                
                if (results > 0) {
                    highlighter.navigateToNext(scrollPane);
                }
                
                updateResultsLabel.run();
            }
            
            public void insertUpdate(DocumentEvent e) { performSearch(); }
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            public void changedUpdate(DocumentEvent e) { performSearch(); }
        };
        
        ActionListener optionsListener = e -> {
            String searchText = searchField.getText();
            if (!searchText.isEmpty()) {
                boolean useRegex = regexCheckBox.isSelected();
                boolean caseSensitive = caseSensitiveCheckBox.isSelected();
                
                int results = highlighter.searchAndHighlight(searchText, useRegex, caseSensitive);
                
                if (results > 0) {
                    highlighter.navigateToNext(scrollPane);
                }
                
                updateResultsLabel.run();
            }
        };
        
        searchField.getDocument().addDocumentListener(searchListener);
        regexCheckBox.addActionListener(optionsListener);
        caseSensitiveCheckBox.addActionListener(optionsListener);
        
        // Navigation button actions
        nextButton.addActionListener(e -> {
            highlighter.navigateToNext(scrollPane);
            updateResultsLabel.run();
        });
        prevButton.addActionListener(e -> {
            highlighter.navigateToPrevious(scrollPane);
            updateResultsLabel.run();
        });
        closeButton.addActionListener(e -> searchPanel.setVisible(false));
        
        // Keyboard shortcuts
        searchField.addKeyListener(new KeyListener() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        highlighter.navigateToPrevious(scrollPane);
                    } else {
                        highlighter.navigateToNext(scrollPane);
                    }
                    updateResultsLabel.run();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchPanel.setVisible(false);
                }
            }
            public void keyTyped(KeyEvent e) {}
            public void keyReleased(KeyEvent e) {}
        });
        
        // Layout components
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UIManager.getColor("Panel.background"));
        leftPanel.add(searchField, BorderLayout.CENTER);
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        rightPanel.setBackground(UIManager.getColor("Panel.background"));
        rightPanel.add(prevButton);
        rightPanel.add(nextButton);
        rightPanel.add(resultsLabel);
        rightPanel.add(closeButton);
        
        optionsPanel.add(regexCheckBox);
        optionsPanel.add(caseSensitiveCheckBox);
        
        searchPanel.add(leftPanel, BorderLayout.WEST);
        searchPanel.add(optionsPanel, BorderLayout.CENTER);
        searchPanel.add(rightPanel, BorderLayout.EAST);
        
        // Initially hidden
        searchPanel.setVisible(false);
        
        return searchPanel;
    }
}