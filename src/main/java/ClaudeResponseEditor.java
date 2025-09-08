import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ClaudeResponseEditor implements ExtensionProvidedHttpResponseEditor {
    private final JPanel panel;
    private final JPanel contentPanel;
    private final JScrollPane scrollPane;
    private HttpRequestResponse requestResponse;

    public ClaudeResponseEditor() {
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
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setBackground(UIManager.getColor("Panel.background"));
        
        // Initial state
        showNoClaudeMessage();
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
        contentPanel.removeAll();
        
        if (ClaudeUtils.isClaudeMessage(requestResponse)) {
            List<ClaudeUtils.ContentBlock> blocks = ClaudeUtils.parseSSEResponse(requestResponse);
            if (!blocks.isEmpty()) {
                ClaudeUtils.MessageContent message = ClaudeUtils.convertResponseToMessage(blocks);
                JPanel messagePanel = ClaudeUtils.createMessagePanel(message);
                contentPanel.add(messagePanel);
            } else {
                showClaudeResponse();
            }
        } else {
            showNoClaudeMessage();
        }
        
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void showNoClaudeMessage() {
        JLabel label = new JLabel("No Claude Message Detected");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(label);
    }
    
    private void showClaudeResponse() {
        JLabel label = new JLabel("Claude Response");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(label);
    }
    
    @Override
    public HttpResponse getResponse() {
        return requestResponse != null ? requestResponse.response() : null;
    }
}