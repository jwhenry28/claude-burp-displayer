package core;

import burp.api.montoya.http.message.HttpRequestResponse;
import java.awt.Color;
import java.util.List;

/**
 * Interface for LLM providers to implement their specific parsing and configuration logic.
 */
public interface LLMProvider {
    /**
     * Checks if the given request/response is for this LLM provider.
     */
    boolean isProviderMessage(HttpRequestResponse requestResponse);

    /**
     * Parses the request body into a list of conversation messages.
     */
    List<ConversationMessage> parseRequest(HttpRequestResponse requestResponse);

    /**
     * Parses the response body into a conversation message.
     * Handles provider-specific formats like SSE, JSON, etc.
     */
    ConversationMessage parseResponse(HttpRequestResponse requestResponse);

    /**
     * Returns the display name of this provider.
     */
    String getProviderName();

    /**
     * Returns the tab caption for this provider's messages.
     */
    String getTabCaption();

    /**
     * Returns provider-specific configuration.
     */
    ProviderConfig getProviderConfig();

    /**
     * Provider configuration for UI customization.
     */
    public static class ProviderConfig {
        public final Color userColor;
        public final Color assistantColor;
        public final Color systemColor;
        public final Color toolCallColor;
        public final Color toolResultColor;
        public final Color toolDefinitionColor;
        public final String userIcon;
        public final String assistantIcon;
        public final String systemIcon;
        public final String toolIcon;

        public ProviderConfig(
            Color userColor,
            Color assistantColor,
            Color systemColor,
            Color toolCallColor,
            Color toolResultColor,
            Color toolDefinitionColor,
            String userIcon,
            String assistantIcon,
            String systemIcon,
            String toolIcon
        ) {
            this.userColor = userColor;
            this.assistantColor = assistantColor;
            this.systemColor = systemColor;
            this.toolCallColor = toolCallColor;
            this.toolResultColor = toolResultColor;
            this.toolDefinitionColor = toolDefinitionColor;
            this.userIcon = userIcon;
            this.assistantIcon = assistantIcon;
            this.systemIcon = systemIcon;
            this.toolIcon = toolIcon;
        }
    }
}