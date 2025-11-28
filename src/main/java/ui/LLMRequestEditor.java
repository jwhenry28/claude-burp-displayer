package ui;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import core.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic request editor that uses the provider registry to display LLM conversations.
 */
public class LLMRequestEditor implements ExtensionProvidedHttpRequestEditor {
    private final JPanel panel;
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;
    private final JPanel searchPanel;
    private final UIUtils.SearchHighlighter searchHighlighter;
    private HttpRequestResponse requestResponse;
    private LLMProvider currentProvider;

    public LLMRequestEditor() {
        panel = new JPanel(new BorderLayout());
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIManager.getColor("Panel.background"));

        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBackground(UIManager.getColor("Panel.background"));

        // Increase scroll sensitivity
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(64);

        // Initialize search functionality
        searchHighlighter = new UIUtils.SearchHighlighter(new ArrayList<>());
        searchPanel = UIUtils.createSearchPanel(searchHighlighter, scrollPane);

        // Make search bar visible by default
        searchPanel.setVisible(true);

        // Add keyboard shortcut for search (Ctrl+F)
        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ctrl F"), "search");
        panel.getActionMap().put("search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchPanel.setVisible(true);
                // Focus on search field
                Component searchField = findSearchField(searchPanel);
                if (searchField != null) {
                    searchField.requestFocus();
                }
            }
        });

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setBackground(UIManager.getColor("Panel.background"));

        // Initial state
        showNoLLMMessage();
    }

    private Component findSearchField(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextField) {
                return component;
            }
            if (component instanceof Container) {
                Component found = findSearchField((Container) component);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public String caption() {
        // Check provider dynamically to handle case where caption() is called before setRequestResponse()
        if (requestResponse != null) {
            LLMProviderRegistry registry = LLMProviderRegistry.getInstance();
            Optional<LLMProvider> providerOpt = registry.findProvider(requestResponse);
            if (providerOpt.isPresent()) {
                return providerOpt.get().getTabCaption();
            }
        } else if (currentProvider != null) {
            return currentProvider.getTabCaption();
        }
        return "LLM";
    }

    @Override
    public Component uiComponent() {
        return panel;
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return true;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.requestResponse = requestResponse;
        updateContent();
    }

    private void updateContent() {
        contentPanel.removeAll();
        List<JTextArea> allTextAreas = new ArrayList<>();

        // Find the appropriate provider
        LLMProviderRegistry registry = LLMProviderRegistry.getInstance();
        Optional<LLMProvider> providerOpt = registry.findProvider(requestResponse);

        if (providerOpt.isPresent()) {
            currentProvider = providerOpt.get();
            List<ConversationMessage> messages = currentProvider.parseRequest(requestResponse);

            if (!messages.isEmpty()) {
                LLMConversationRenderer renderer = new LLMConversationRenderer(currentProvider.getProviderConfig());
                LLMConversationRenderer.MessagePanelResult result = renderer.renderMessages(messages);
                contentPanel.add(result.panel);
                allTextAreas.addAll(result.textAreas);
            } else {
                showProviderMessage(currentProvider.getProviderName());
            }
        } else {
            currentProvider = null;
            showNoLLMMessage();
        }

        // Update search highlighter with new text areas
        searchHighlighter.updateTextAreas(allTextAreas);

        contentPanel.revalidate();
        contentPanel.repaint();

        // Auto-scroll to bottom to show most recent messages
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
    }

    private void showNoLLMMessage() {
        JLabel label = new JLabel("No LLM Message Detected");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(label);
    }

    private void showProviderMessage(String providerName) {
        JLabel label = new JLabel(providerName + " Request");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(label);
    }

    @Override
    public HttpRequest getRequest() {
        return requestResponse != null ? requestResponse.request() : null;
    }
}