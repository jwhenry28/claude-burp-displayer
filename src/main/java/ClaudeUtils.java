import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            JsonNode messagesArray = root.get("messages");
            
            if (messagesArray == null || !messagesArray.isArray()) {
                return messages;
            }
            
            for (JsonNode messageNode : messagesArray) {
                String role = messageNode.has("role") ? messageNode.get("role").asText() : "unknown";
                MessageContent messageContent = new MessageContent(role);
                
                JsonNode contentArray = messageNode.get("content");
                if (contentArray != null && contentArray.isArray()) {
                    for (JsonNode contentItem : contentArray) {
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
    
    public static JPanel createMessagePanel(MessageContent message) {
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messagePanel.setBackground(UIManager.getColor("Panel.background"));
        messagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Role header with colors that work with different themes
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
        
        // Content items
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
                
            } else if ("tool_use".equals(item.type)) {
                // Collapsible tool use
                JPanel toolPanel = createCollapsibleToolPanel(
                    "🔧 Tool Use: " + item.toolName, 
                    "ID: " + item.toolId + "\nInput: " + item.toolInput,
                    new Color(70, 130, 180)
                );
                messagePanel.add(toolPanel);
                
            } else if ("tool_result".equals(item.type)) {
                // Collapsible tool result
                JPanel toolPanel = createCollapsibleToolPanel(
                    "📄 Tool Result", 
                    item.toolContent,
                    new Color(138, 43, 226)
                );
                messagePanel.add(toolPanel);
            }
            
            // Add space between content items (except after the last one)
            if (i < message.contentItems.size() - 1) {
                messagePanel.add(Box.createVerticalStrut(10));
            }
        }
        
        return messagePanel;
    }

    public static JPanel createCollapsibleToolPanel(String title, String content, Color titleColor) {
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
        
        return containerPanel;
    }
}