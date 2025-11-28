package core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single message in an LLM conversation.
 */
public class ConversationMessage {
    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool"),
        TOOLS("tools");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role fromString(String value) {
            for (Role role : Role.values()) {
                if (role.value.equalsIgnoreCase(value)) {
                    return role;
                }
            }
            return USER; // Default fallback
        }
    }

    private final Role role;
    private final List<ContentItem> contentItems;

    public ConversationMessage(Role role) {
        this.role = role;
        this.contentItems = new ArrayList<>();
    }

    public ConversationMessage(String role) {
        this.role = Role.fromString(role);
        this.contentItems = new ArrayList<>();
    }

    public void addContent(ContentItem content) {
        contentItems.add(content);
    }

    public Role getRole() {
        return role;
    }

    public List<ContentItem> getContentItems() {
        return contentItems;
    }

    public boolean hasContent() {
        return !contentItems.isEmpty();
    }
}