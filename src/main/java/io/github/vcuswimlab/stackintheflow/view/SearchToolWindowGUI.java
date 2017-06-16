package io.github.vcuswimlab.stackintheflow.view;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowserManager;
import io.github.vcuswimlab.stackintheflow.controller.QueryExecutor;
import io.github.vcuswimlab.stackintheflow.model.JerseyResponse;
import io.github.vcuswimlab.stackintheflow.model.Question;
import io.github.vcuswimlab.stackintheflow.model.personalsearch.PersonalSearchModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * Created by chase on 5/18/17.
 */
public class SearchToolWindowGUI {

    private JButton searchButton;
    private JTextField searchBox;
    private JPanel content;
    private JList<Question> resultsList;
    private JScrollPane resultsScrollPane;
    private JPanel searchJPanel;
    private JEditorPane consoleErrorPane;
    private DefaultListModel<Question> questionListModel;

    private PersonalSearchModel searchModel;

    private List<String> compilerMessages;

    private ScheduledThreadPoolExecutor timer;

    private SearchToolWindowGUI(JButton searchButton,
                                JTextField searchBox,
                                JPanel content,
                                JList<Question> resultsList,
                                JScrollPane resultsScrollPane,
                                JPanel searchJPanel,
                                JEditorPane consoleErrorPane,
                                PersonalSearchModel searchModel) {
        this.searchButton = searchButton;
        this.searchBox = searchBox;
        this.content = content;
        this.resultsList = resultsList;
        this.resultsScrollPane = resultsScrollPane;
        this.searchJPanel = searchJPanel;
        this.consoleErrorPane = consoleErrorPane;

        this.searchModel = searchModel;

        compilerMessages = new ArrayList<>();

        timer = new ScheduledThreadPoolExecutor(1);

        addListeners();
    }

    private void addListeners() {
        consoleErrorPane.setVisible(false);

        searchButton.addActionListener(e -> executeQuery(searchBox.getText(), false));
        searchBox.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    searchButton.doClick();
                }
            }
        });
        resultsList.setListData(new Question[0]);
        questionListModel = new DefaultListModel<>();
        resultsList.setModel(questionListModel);
        QuestionRenderer renderer = new QuestionRenderer(resultsList);
        resultsList.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                renderer.setWidth(resultsList.getWidth());
            }
        });
        resultsList.setCellRenderer(renderer);
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                JList<String> list = (JList<String>) evt.getSource();
                if (evt.getClickCount() == 1 && evt.getButton() == MouseEvent.BUTTON1) {
                    if (handleSingleClick(evt, list)) return;
                }

                if (evt.getClickCount() == 2 && evt.getButton() == MouseEvent.BUTTON1) {
                    handleDoubleClick(evt, list);
                }
            }

            private void handleDoubleClick(MouseEvent evt, JList<String> list) {
                int index = list.locationToIndex(evt.getPoint());
                if (index < 0 || index >= questionListModel.size()) {
                    return;
                }

                openBrowser(questionListModel.get(index).getLink());
            }

            private boolean handleSingleClick(MouseEvent evt, JList<String> list) {
                int index = list.locationToIndex(evt.getPoint());
                if (index < 0 || index >= questionListModel.size()) {
                    return false;
                }

                Question question = questionListModel.get(index);

                // If they are expanding the question, increase the tags in the search model
                if (!question.isExpanded()) {
                    searchModel.increaseTags(question.getTags());
                }

                question.toggleExpanded();
                refreshListView();
                return true;
            }
        });

        consoleErrorPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                executeQuery(compilerMessages.get(0), true);
                consoleErrorPane.setVisible(false);
            }
        });
    }

    private void refreshListView() {
        updateList(listModelToList());
    }

    @NotNull
    private List<Question> listModelToList() {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < questionListModel.size(); i++) {
            questions.add(questionListModel.get(i));
        }
        return questions;
    }

    public void executeQuery(String query, boolean backoff) {

        Future<List<Question>> questionListFuture = timer.submit(() -> {
            String searchQuery = query;

            JerseyResponse jerseyResponse = QueryExecutor.executeQuery(searchQuery);
            List<Question> questionList = jerseyResponse.getItems();

            if (backoff && questionList.isEmpty()) {

                Deque<String> queryStack = new ArrayDeque<>();
                queryStack.addAll(Arrays.asList(searchQuery.split("\\s")));

                while (questionList.isEmpty()) {
                    queryStack.pop();
                    searchQuery = queryStack.stream().collect(Collectors.joining(" "));
                    jerseyResponse = QueryExecutor.executeQuery(searchQuery);
                    questionList = jerseyResponse.getItems();
                }
            }

            setSearchBoxContent(searchQuery);
            return searchModel.rankQuesitonList(questionList);
        });

        try {
            updateList(questionListFuture.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void updateList(List<Question> elements) {
        if (elements == null) {
            return;
        }

        questionListModel.clear();
        for (Question element : elements) {
            questionListModel.addElement(element);
        }

        if (elements.isEmpty()) {
            questionListModel.addElement(new Question(null, "Sorry, your search returned no results :(", "", "", "http://www.stackoverflow.com"));
        }
    }

    private void setSearchBoxContent(String content) {
        searchBox.setText(content);
    }

    public void setConsoleError(List<String> compilerMessages) {
        this.compilerMessages = compilerMessages;

        if (!compilerMessages.isEmpty()) {
            HTMLEditorKit kit = new HTMLEditorKit();
            HTMLDocument doc = new HTMLDocument();
            consoleErrorPane.setEditorKit(kit);
            consoleErrorPane.setDocument(doc);
            String fontStartBlockLink = "<font color=\"" + EditorFonts.getHyperlinkColorHex() + "\">";
            String fontStartBlockDefault = "<font color=\"" + EditorFonts.getPrimaryFontColorHex() + "\">";

            try {
                kit.insertHTML(doc, 0, fontStartBlockDefault + "search for: " + "</font><a href=\"\"><u>" +
                                fontStartBlockLink + compilerMessages.get(0) +
                                "</font></u></a>",
                        0, 0, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            consoleErrorPane.setVisible(true);
        } else {
            consoleErrorPane.setVisible(false);
        }
    }

    private void openBrowser(String url) {
        BrowserLauncher.getInstance().browse(url, WebBrowserManager.getInstance().getFirstActiveBrowser());
    }

    public JPanel getContentPanel() {
        return content;
    }

    public static class SearchToolWindowGUIBuilder {

        private JButton searchButton;
        private JTextField searchBox;
        private JPanel content;
        private JList<Question> resultsList;
        private JScrollPane resultsScrollPane;
        private JPanel searchJPanel;
        private JEditorPane consoleErrorPane;
        private PersonalSearchModel searchModel;

        public SearchToolWindowGUIBuilder setSearchButton(JButton searchButton) {
            this.searchButton = searchButton;
            return this;
        }

        public SearchToolWindowGUIBuilder setSearchBox(JTextField searchBox) {
            this.searchBox = searchBox;
            return this;
        }

        public SearchToolWindowGUIBuilder setContent(JPanel content) {
            this.content = content;
            return this;
        }

        public SearchToolWindowGUIBuilder setResultsList(JList<Question> resultsList) {
            this.resultsList = resultsList;
            return this;
        }

        public SearchToolWindowGUIBuilder setResultsScrollPane(JScrollPane resultsScrollPane) {
            this.resultsScrollPane = resultsScrollPane;
            return this;
        }

        public SearchToolWindowGUIBuilder setSearchJPanel(JPanel searchJPanel) {
            this.searchJPanel = searchJPanel;
            return this;
        }

        public SearchToolWindowGUIBuilder setConsoleErrorPane(JEditorPane consoleErrorPane) {
            this.consoleErrorPane = consoleErrorPane;
            return this;
        }

        public SearchToolWindowGUIBuilder setSearchModel(PersonalSearchModel searchModel) {
            this.searchModel = searchModel;
            return this;
        }

        public SearchToolWindowGUI build() {
            return new SearchToolWindowGUI(
                    searchButton,
                    searchBox,
                    content,
                    resultsList,
                    resultsScrollPane,
                    searchJPanel,
                    consoleErrorPane,
                    searchModel
            );
        }
    }
}