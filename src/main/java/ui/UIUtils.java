package ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for UI components and functionality used across the extension.
 */
public class UIUtils {

    /**
     * Creates a collapsible panel with a clickable header.
     */
    public static CollapsiblePanelResult createCollapsiblePanel(String title, String content, Color titleColor, String icon) {
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

        JLabel titleLabel = new JLabel("▶ " + icon + " " + title);
        titleLabel.setForeground(titleColor);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
                titleLabel.setText((isVisible ? "▶ " : "▼ ") + icon + " " + title);
                containerPanel.revalidate();
                containerPanel.repaint();
            }
        });

        containerPanel.add(headerPanel);
        containerPanel.add(contentPanel);

        return new CollapsiblePanelResult(containerPanel, contentArea);
    }

    public static class CollapsiblePanelResult {
        public final JPanel panel;
        public final JTextArea textArea;

        public CollapsiblePanelResult(JPanel panel, JTextArea textArea) {
            this.panel = panel;
            this.textArea = textArea;
        }
    }

    /**
     * Search highlighter for text areas.
     */
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

        public void updateTextAreas(List<JTextArea> newTextAreas) {
            this.textAreas.clear();
            this.textAreas.addAll(newTextAreas);
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

        private static class HighlightInfo {
            public final JTextArea textArea;
            public final int start;
            public final int end;

            public HighlightInfo(JTextArea textArea, int start, int end) {
                this.textArea = textArea;
                this.start = start;
                this.end = end;
            }
        }
    }

    /**
     * Creates a search panel for searching through text areas.
     */
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

        searchField.getDocument().addDocumentListener(searchListener);
        regexCheckBox.addActionListener(e -> {
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
        });
        caseSensitiveCheckBox.addActionListener(e -> {
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
        });

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