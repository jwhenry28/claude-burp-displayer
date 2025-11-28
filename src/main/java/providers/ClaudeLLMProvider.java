package providers;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM provider implementation for Claude (Anthropic) API.
 */
public class ClaudeLLMProvider implements LLMProvider {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProviderConfig config;

    public ClaudeLLMProvider() {
        this.config = new ProviderConfig(
            new Color(70, 130, 180),   // userColor - Steel Blue
            new Color(138, 43, 226),    // assistantColor - Blue Violet
            new Color(255, 140, 0),     // systemColor - Dark Orange
            new Color(70, 130, 180),    // toolCallColor - Steel Blue
            new Color(138, 43, 226),    // toolResultColor - Blue Violet
            new Color(34, 139, 34),     // toolDefinitionColor - Forest Green
            "",                         // userIcon
            "",                         // assistantIcon
            "🎯",                       // systemIcon
            "🔧"                        // toolIcon
        );
    }

    @Override
    public boolean isProviderMessage(HttpRequestResponse requestResponse) {
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

        // If response is present, verify it's either text/event-stream (streaming) or application/json (non-streaming)
        // If no response yet, accept based on request criteria alone
        if (response != null) {
            String responseContentType = response.headerValue("Content-Type");
            // If response exists but isn't one of the expected types, reject it
            if (responseContentType == null) {
                return false;
            }
            String contentTypeLower = responseContentType.toLowerCase();
            if (!contentTypeLower.startsWith("text/event-stream") &&
                !contentTypeLower.startsWith("application/json")) {
                return false;
            }
        }

        // All request criteria met (and response criteria met if response exists)
        return true;
    }

    @Override
    public List<ConversationMessage> parseRequest(HttpRequestResponse requestResponse) {
        List<ConversationMessage> messages = new ArrayList<>();

        if (!isProviderMessage(requestResponse)) {
            return messages;
        }

        HttpRequest request = requestResponse.request();
        String bodyString = request.bodyToString();

        if (bodyString == null || bodyString.isEmpty()) {
            return messages;
        }

        try {
            JsonNode root = mapper.readTree(bodyString);

            // Handle system message first if it exists
            JsonNode systemArray = root.get("system");
            if (systemArray != null && systemArray.isArray()) {
                ConversationMessage systemMessage = new ConversationMessage(ConversationMessage.Role.SYSTEM);

                for (JsonNode systemItem : systemArray) {
                    if (systemItem.has("type")) {
                        String type = systemItem.get("type").asText();
                        if ("text".equals(type) && systemItem.has("text")) {
                            String text = systemItem.get("text").asText();
                            systemMessage.addContent(new TextContent(text));
                        }
                    } else if (systemItem.has("text")) {
                        String text = systemItem.get("text").asText();
                        systemMessage.addContent(new TextContent(text));
                    }
                }

                if (systemMessage.hasContent()) {
                    messages.add(systemMessage);
                }
            }

            // Handle tools array if it exists (available tools for the model)
            JsonNode toolsArray = root.get("tools");
            if (toolsArray != null && toolsArray.isArray() && toolsArray.size() > 0) {
                ConversationMessage toolsMessage = new ConversationMessage(ConversationMessage.Role.TOOLS);

                for (JsonNode toolNode : toolsArray) {
                    String name = toolNode.has("name") ? toolNode.get("name").asText() : "unknown";
                    String description = toolNode.has("description") ? toolNode.get("description").asText() : "";
                    String inputSchema = toolNode.has("input_schema") ? toolNode.get("input_schema").toPrettyString() : "";

                    toolsMessage.addContent(new ToolDefinitionContent(name, description, inputSchema));
                }

                if (toolsMessage.hasContent()) {
                    messages.add(toolsMessage);
                }
            }

            JsonNode messagesArray = root.get("messages");

            if (messagesArray == null || !messagesArray.isArray()) {
                return messages;
            }

            for (JsonNode messageNode : messagesArray) {
                String role = messageNode.has("role") ? messageNode.get("role").asText() : "unknown";
                ConversationMessage message = new ConversationMessage(role);

                JsonNode contentNode = messageNode.get("content");
                if (contentNode != null) {
                    if (contentNode.isArray()) {
                        // Handle array of content items
                        for (JsonNode contentItem : contentNode) {
                            parseContentItem(contentItem, message);
                        }
                    } else if (contentNode.isTextual()) {
                        // Handle simple string content
                        String text = contentNode.asText();
                        message.addContent(new TextContent(text));
                    }
                }

                messages.add(message);
            }

        } catch (Exception e) {
            // Return empty list on error
        }

        return messages;
    }

    private void parseContentItem(JsonNode contentItem, ConversationMessage message) {
        if (contentItem.has("type")) {
            String type = contentItem.get("type").asText();

            if ("text".equals(type) && contentItem.has("text")) {
                String text = contentItem.get("text").asText();
                message.addContent(new TextContent(text));

            } else if ("tool_use".equals(type)) {
                String id = contentItem.has("id") ? contentItem.get("id").asText() : "unknown";
                String name = contentItem.has("name") ? contentItem.get("name").asText() : "unknown";
                String input = contentItem.has("input") ? contentItem.get("input").toString() : "{}";
                message.addContent(new ToolCallContent(id, name, input));

            } else if ("tool_result".equals(type)) {
                String toolUseId = contentItem.has("tool_use_id") ? contentItem.get("tool_use_id").asText() : "unknown";
                String content = contentItem.has("content") ? contentItem.get("content").asText() : "";
                message.addContent(new ToolResultContent(toolUseId, content));
            }
        } else if (contentItem.has("text")) {
            String text = contentItem.get("text").asText();
            message.addContent(new TextContent(text));
        }
    }

    @Override
    public ConversationMessage parseResponse(HttpRequestResponse requestResponse) {
        ConversationMessage message = new ConversationMessage(ConversationMessage.Role.ASSISTANT);

        if (requestResponse == null || requestResponse.response() == null) {
            return message;
        }

        HttpResponse response = requestResponse.response();
        String responseBody = response.bodyToString();

        if (responseBody == null || responseBody.isEmpty()) {
            return message;
        }

        // Determine if this is a streaming (SSE) or non-streaming (JSON) response
        // Check if body starts with SSE format (lines starting with "data:")
        String trimmedBody = responseBody.trim();
        if (trimmedBody.startsWith("data:") || trimmedBody.startsWith("event:")) {
            // Parse as SSE (streaming response)
            return parseSSEResponse(responseBody);
        } else {
            // Parse as JSON (non-streaming response)
            return parseJSONResponse(responseBody);
        }
    }

    private ConversationMessage parseJSONResponse(String responseBody) {
        ConversationMessage message = new ConversationMessage(ConversationMessage.Role.ASSISTANT);

        try {
            JsonNode root = mapper.readTree(responseBody);

            // Parse the content array from the JSON response
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray()) {
                for (JsonNode contentItem : contentArray) {
                    parseContentItem(contentItem, message);
                }
            }

        } catch (Exception e) {
            // Return message with any content parsed so far
        }

        return message;
    }

    private ConversationMessage parseSSEResponse(String responseBody) {
        ConversationMessage message = new ConversationMessage(ConversationMessage.Role.ASSISTANT);

        try {
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
                        handleContentBlockStart(data, activeBlocks);
                    } else if ("content_block_delta".equals(eventType)) {
                        handleContentBlockDelta(data, activeBlocks);
                    } else if ("content_block_stop".equals(eventType)) {
                        handleContentBlockStop(data, activeBlocks, message);
                    }

                } catch (Exception e) {
                    // Skip invalid JSON lines
                }
            }

        } catch (Exception e) {
            // Return message with any content parsed so far
        }

        return message;
    }

    private void handleContentBlockStart(JsonNode data, Map<Integer, ContentBlock> activeBlocks) {
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
    }

    private void handleContentBlockDelta(JsonNode data, Map<Integer, ContentBlock> activeBlocks) {
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
    }

    private void handleContentBlockStop(JsonNode data, Map<Integer, ContentBlock> activeBlocks, ConversationMessage message) {
        int index = data.has("index") ? data.get("index").asInt() : 0;

        if (activeBlocks.containsKey(index)) {
            ContentBlock block = activeBlocks.remove(index);
            if ("text".equals(block.type)) {
                message.addContent(new TextContent(block.content));
            } else if ("tool_use".equals(block.type)) {
                message.addContent(new ToolCallContent(block.toolId, block.toolName, block.content));
            }
        }
    }

    @Override
    public String getProviderName() {
        return "Claude";
    }

    @Override
    public String getTabCaption() {
        return "Claude";
    }

    @Override
    public ProviderConfig getProviderConfig() {
        return config;
    }

    /**
     * Helper class for parsing SSE response blocks.
     */
    private static class ContentBlock {
        public String type;
        public String content;
        public String toolName;
        public String toolId;

        public ContentBlock(String type) {
            this.type = type;
            this.content = "";
        }
    }
}