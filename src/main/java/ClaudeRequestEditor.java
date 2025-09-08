import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class ClaudeRequestEditor implements ExtensionProvidedHttpRequestEditor {
    private final JPanel panel;
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;
    private final JPanel searchPanel;
    private final ClaudeUtils.SearchHighlighter searchHighlighter;
    private HttpRequestResponse requestResponse;

    public ClaudeRequestEditor() {
        panel = new JPanel(new BorderLayout());
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIManager.getColor("Panel.background"));
        
        scrollPane = new JScrollPane(contentPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBackground(UIManager.getColor("Panel.background"));
        
        // Increase scroll sensitivity to match other BurpSuite tabs
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(64);
        
        // Initialize search functionality
        searchHighlighter = new ClaudeUtils.SearchHighlighter(new ArrayList<>());
        searchPanel = ClaudeUtils.createSearchPanel(searchHighlighter, scrollPane);
        
        // Make search bar visible by default for testing
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
        showNoClaudeMessage();
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
        return "Claude";
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
        updateLabel();
    }

    private void updateLabel() {
        List<ClaudeUtils.MessageContent> messages = ClaudeUtils.extractRequestMessages(requestResponse);
        
        contentPanel.removeAll();
        
        if (messages.isEmpty()) {
            showNoClaudeMessage();
        } else {
            displayMessages(messages);
        }
        
        contentPanel.revalidate();
        contentPanel.repaint();
        
        // Auto-scroll to bottom to show most recent messages
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
    }
    
    private void showNoClaudeMessage() {
        JLabel label = new JLabel("No Claude Message Detected");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(label);
    }
    
    private void displayMessages(List<ClaudeUtils.MessageContent> messages) {
        List<JTextArea> allTextAreas = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            ClaudeUtils.MessageContent message = messages.get(i);
            
            // Add message panel
            ClaudeUtils.MessagePanelResult result = ClaudeUtils.createMessagePanel(message);
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
        
        // Update search highlighter with new text areas
        updateSearchTextAreas(allTextAreas);
    }
    
    private void updateSearchTextAreas(List<JTextArea> textAreas) {
        // Clear existing highlights
        searchHighlighter.clearHighlights();
        // Update the search highlighter's text areas list using reflection or a new method
        try {
            java.lang.reflect.Field field = searchHighlighter.getClass().getDeclaredField("textAreas");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<JTextArea> existingTextAreas = (List<JTextArea>) field.get(searchHighlighter);
            existingTextAreas.clear();
            existingTextAreas.addAll(textAreas);
        } catch (Exception e) {
            // Reflection failed, search won't work but won't crash
        }
    }

    @Override
    public HttpRequest getRequest() {
        return requestResponse != null ? requestResponse.request() : null;
    }
}