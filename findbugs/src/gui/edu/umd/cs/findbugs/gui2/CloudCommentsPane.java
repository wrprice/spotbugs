/*
 * Copyright 2010 Keith Lea
 *
 * This file is part of FindBugs-IDEA.
 *
 * FindBugs-IDEA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FindBugs-IDEA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FindBugs-IDEA.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.umd.cs.findbugs.gui2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import edu.umd.cs.findbugs.BugCollection;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.I18N;
import edu.umd.cs.findbugs.L10N;
import edu.umd.cs.findbugs.cloud.Cloud;
import edu.umd.cs.findbugs.cloud.Cloud.UserDesignation;
import edu.umd.cs.findbugs.cloud.CloudPlugin;

import static edu.umd.cs.findbugs.util.Util.nullSafeEquals;

@edu.umd.cs.findbugs.annotations.SuppressWarnings({"SE_TRANSIENT_FIELD_NOT_RESTORED", "SE_BAD_FIELD", "SE_BAD_FIELD_STORE"})
public abstract class CloudCommentsPane extends JPanel {

    private static final String DEFAULT_COMMENT = "Click to add review...";
    private static final String DEFAULT_COMMENT_MULTI_PREFIX = "Click to add review to ";
    private static final String DEFAULT_COMMENT_MULTI = DEFAULT_COMMENT_MULTI_PREFIX + "%d bugs...";
    private static final String DEFAULT_VARIOUS_COMMENTS_COMMENT = "Click to overwrite multiple reviews...";

    private JTextArea cloudReportPane;
    protected JComponent cancelLink;
    protected JComponent signInOutLink;
    private JTextArea commentBox;
    private JButton submitCommentButton;
    private WideComboBox designationCombo;
    private JPanel mainPanel;
    private JScrollPane _cloudReportScrollPane;
    protected JLabel titleLabel;
    protected JTextArea cloudDetailsLabel;
    private JPanel dumbPanelSignInOutLink;
    private JLabel lastSavedLabel;

    protected BugCollection _bugCollection;
    protected BugInstance _bugInstance;
    private BugAspects _bugAspects;

    private boolean dontShowAnnotationConfirmation = false;

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private final Cloud.CloudStatusListener _cloudStatusListener = new MyCloudStatusListener();
    private Cloud lastCloud = null;
    private Font plainCommentFont;
    private String lastCommentText = null;
    private Set<BugInstance> lastConfirmed = Collections.emptySet();

    public CloudCommentsPane() {
        $$$setupUI$$$();

        cloudReportPane.setBackground(this.getBackground());
        cloudReportPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        _cloudReportScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));

//        designationCombo.setPreferredSize(new Dimension(300, 20));
        commentBox.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                commentBoxClicked();
            }
        });
        commentBox.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                updateSaveButton();
            }
        });
        commentBox.setBorder(new EtchedBorder(EtchedBorder.LOWERED));

        dumbPanelSignInOutLink.setPreferredSize(null);

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);

        designationCombo.removeAllItems();
        final List<String> userDesignationKeys = I18N.instance().getUserDesignationKeys(true);
        for (final String designation : userDesignationKeys) {
            designationCombo.addItem(I18N.instance().getUserDesignation(designation));
        }
        designationCombo.addItem(null);
        designationCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component real = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1)
                    return real;
                JPanel panel = new JPanel(new GridBagLayout());
                panel.setBorder(new EmptyBorder(3, 3, 3, 3));
                int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weightx = 1;
                gbc.anchor = GridBagConstraints.WEST;
                panel.add(real, gbc);

                gbc.weightx = 0;
                gbc.anchor = GridBagConstraints.EAST;
                gbc.insets = new Insets(0, 10, 0, 0);
                JLabel label = new JLabel(KeyEvent.getKeyModifiersText(mask) + "-" + (index + 1));
                label.setForeground(Color.GRAY);
//                Font font = label.getFont();
//                label.setFont(font.deriveFont(font.getSize() - 2f));
                panel.add(label, gbc);
                panel.setBackground(real.getBackground());
                return panel;
            }
        });
        designationCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingHeader) {
                    int selectedIndex = designationCombo.getSelectedIndex();
                    if (selectedIndex >= 0)
                        setDesignation(userDesignationKeys.get(selectedIndex));
                }
            }
        });

//        commentEntryPanel.setVisible(false);
        submitCommentButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                submitComment(CloudCommentsPane.this.getSelectedBugs());
            }
        });
        cloudDetailsLabel.setBackground(null);
        cloudDetailsLabel.setBorder(null);
        plainCommentFont = commentBox.getFont().deriveFont(Font.PLAIN);
        cloudReportPane.setFont(plainCommentFont);
//        cloudReportPane.setEditorKit(new HTMLEditorKit());
//        ((HTMLEditorKit) cloudReportPane.getDocument()).getStyleSheet().addRule("body { font-");

        setDefaultComment(DEFAULT_COMMENT);
        commentBox.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) {
                commentBox.setForeground(null);
                commentBox.setFont(plainCommentFont);
                if (isDefaultComment(CloudCommentsPane.this.commentBox.getText())) {
                    resetCommentBoxFont();
                    setCommentText("");
                }
            }

            public void focusLost(FocusEvent e) {
                if (isDefaultComment(CloudCommentsPane.this.commentBox.getText())) {
                    refresh();
                } else if (commentBox.getText().equals(DEFAULT_VARIOUS_COMMENTS_COMMENT)) {
                    refresh();
                } else if (commentBox.getText().equals(lastCommentText)) {
                } else {
                    submitComment(CloudCommentsPane.this.getSelectedBugs());
                    resetCommentBoxFont();
                }
            }
        });
        commentBox.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelClicked();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    submitComment(CloudCommentsPane.this.getSelectedBugs());
                }
            }
        });
        submitCommentButton.setToolTipText("Submit review [Enter]");
        cancelLink.setToolTipText("Cancel [Esc]");
        setCanAddComments(false, false);
        setLastSaved(0);

        updateBugCommentsView();
    }

    private boolean isDefaultComment(String text) {
        return text.equals(DEFAULT_COMMENT) || text.startsWith(DEFAULT_COMMENT_MULTI_PREFIX);
    }

    private void updateSaveButton() {
        boolean changed = commentWasChanged();
        submitCommentButton.setEnabled(changed);
        cancelLink.setEnabled(false/*changed*/);
    }

    private void setCommentText(String t) {
        lastCommentText = t;
        if (!commentBox.getText().equals(t))
            commentBox.setText(t);
    }

    private void resetCommentBoxFont() {
        commentBox.setFont(plainCommentFont);
        commentBox.setForeground(null);
    }

    private void setDefaultComment(String defaultComment) {
        setCommentText(defaultComment);
        commentBox.setForeground(Color.DARK_GRAY);
        commentBox.setFont(plainCommentFont.deriveFont(Font.ITALIC));
    }

    private void createUIComponents() {
        setupLinksOrButtons();
    }

    protected abstract void setupLinksOrButtons();


    private void applyToBugs(boolean background, final BugAction bugAction) {
        Executor executor = background ? backgroundExecutor : new NowExecutor();

        final AtomicInteger shownErrorMessages = new AtomicInteger(0);
        for (final BugInstance bug : getSelectedBugs())
            executor.execute(new Runnable() {
                public void run() {
                    if (shownErrorMessages.get() > 5) {
                        // 5 errors? let's just stop trying.
                        return;
                    }
                    try {
                        bugAction.execute(bug);
                    } catch (Throwable e) {
                        if (shownErrorMessages.addAndGet(1) > 5) {
                            return;
                        }
                        JOptionPane.showMessageDialog(CloudCommentsPane.this,
                                "Error while submitting cloud reviews:\n"
                                        + e.getClass().getSimpleName() + ": " + e.getMessage(),
                                "Review Submission Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
    }

    protected void signInOrOutClicked() {
        if (_bugCollection != null) {
            final Cloud cloud = _bugCollection.getCloud();
            if (cloud.getPlugin().getId().equals("edu.umd.cs.findbugs.cloud.doNothingCloud")) {
                changeClicked();
            }
            switch (cloud.getSigninState()) {
                case SIGNED_OUT:
                case SIGNIN_FAILED:
                case UNAUTHENTICATED:
                    try {
                        cloud.signIn();
                        refresh();
                    } catch (Exception e) {
                        _bugCollection.getProject().getGuiCallback().showMessageDialog(
                                "The FindBugs Cloud could not be contacted at this time.\n\n" + e.getMessage());
                    }
                    break;
                case SIGNED_IN:
                    cloud.signOut();
                    refresh();
                    break;
                default:
            }
        }
    }

    protected void commentBoxClicked() {
        //TODO: use glass pane
        if (commentWasChanged())
            return;
        setCanAddComments(false, true);
        CommentInfo commentInfo = new CommentInfo().invoke();
        boolean sameText = commentInfo.isSameText();
        String txt = commentInfo.getTxt();
        if (!sameText)
            txt = "";
        if (txt == null || txt.trim().length() == 0)
            txt = "";
        resetCommentBoxFont();
        boolean sameTextInBox = commentBox.getText().equals(txt);
        setCommentText(txt);
        int start = commentBox.getSelectionStart();
        int end = commentBox.getSelectionEnd();
        if (!commentBox.hasFocus() && (!sameTextInBox || start != 0 || end != txt.length())) {
            commentBox.setSelectionStart(0);
            commentBox.setSelectionEnd(txt.length());
        }
        updateSaveButton();
    }

    private boolean commentWasChanged() {
        String text = commentBox.getText();
        boolean b = !isDefaultComment(text);
//        boolean b1 = text.trim().equals("");
        boolean b2 = text.equals(DEFAULT_VARIOUS_COMMENTS_COMMENT);
        boolean b3 = text.equals(lastCommentText);
        return b && !b2 && !b3;
    }

    public void setDesignation(final String designationKey) {

        List<BugInstance> selectedBugs = getSelectedBugs();
        if (selectedBugs.size() > 1)
            if (!confirmAnnotation(selectedBugs))
                return;
        final AtomicBoolean stop = new AtomicBoolean(false);
        applyToBugs(false, new BugAction() {
            public void execute(BugInstance bug) {
                if (stop.get())
                    return;
                String oldValue = bug.getUserDesignationKey();
                String key = designationKey;
                if (key.equals(oldValue))
                    return;
                Cloud plugin = _bugCollection != null ? _bugCollection.getCloud() : null;
                if (plugin != null && key.equals("I_WILL_FIX") && plugin.supportsClaims()) {
                    String claimedBy = plugin.claimedBy(bug);
                    if (claimedBy != null && !plugin.getUser().equals(claimedBy)) {
                        int result = JOptionPane.showConfirmDialog(null,
                                bug.getMessage() + "\n"
                                        + claimedBy + " has already said they will fix this issue\n"
                                        + "Do you want to also be listed as fixing this issue?\n"
                                        + "If so, please coordinate with " + claimedBy,
                                "Issue already claimed", JOptionPane.YES_NO_CANCEL_OPTION);
                        if (result == JOptionPane.CANCEL_OPTION) {
                            stop.set(true);
                            return;
                        }
                        if (result != JOptionPane.YES_OPTION)
                            key = "MUST_FIX";
                    }
                }
                changeDesignationOfBug(bug, key);
                refresh();
            }
        });
    }

    private void submitComment(List<BugInstance> selectedBugs) {
        String comment = commentBox.getText();
        if (isDefaultComment(comment) || comment.equals(DEFAULT_VARIOUS_COMMENTS_COMMENT))
            comment = "";
        final int index = designationCombo.getSelectedIndex();
        final String choice;
        if (index == -1) {
            choice = UserDesignation.UNCLASSIFIED.name();
        } else {
            choice = I18N.instance().getUserDesignationKeys(true).get(index);
        }
        if (selectedBugs.size() > 1)
            if (!confirmAnnotation(selectedBugs))
                return;
        setDesignation(choice);
        final String finalComment = comment;
        applyToBugs(true, new BugAction() {
            public void execute(BugInstance bug) {
                bug.setAnnotationText(finalComment, _bugCollection);
                refresh();
                setLastSaved(System.currentTimeMillis());
            }
        });

        refresh();

        setCanAddComments(true, false);
        commentBox.requestFocus();
    }

    private void setLastSaved(long date) {
        if (date > 0)
            lastSavedLabel.setText("saved " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(date)));
        else
            lastSavedLabel.setText("");
    }

    protected void cancelClicked() {
        setDefaultComment(lastCommentText);
//        commentEntryPanel.setVisible(false);
        setCanAddComments(true, false);
    }

    private List<BugInstance> getSelectedBugs() {
        if (_bugInstance != null)
            return Collections.singletonList(_bugInstance);
        if (_bugAspects != null) {
            List<BugInstance> set = new ArrayList<BugInstance>();
            for (BugLeafNode node : _bugAspects.getMatchingBugs(BugSet.getMainBugSet())) {
                set.add(node.getBug());
            }
            return set;
        }
        return Collections.emptyList();
    }

    private boolean hasSelectedBugs() {
        return _bugInstance != null || _bugAspects != null;
    }

    protected void changeClicked() {
        final List<CloudPlugin> plugins = new ArrayList<CloudPlugin>();
        final List<String> descriptions = new ArrayList<String>();
        List<CloudPlugin> clouds = new ArrayList<CloudPlugin>(DetectorFactoryCollection.instance().getRegisteredClouds().values());
        Collections.sort(clouds, new Comparator<CloudPlugin>() {
            public int compare(CloudPlugin o1, CloudPlugin o2) {
                return o1.getDescription().compareToIgnoreCase(o2.getDescription());
            }
        });
        for (final CloudPlugin plugin : clouds) {
            final boolean disabled = isDisabled(plugin);
            if (!disabled && !plugin.isHidden()) {
                descriptions.add(plugin.getDescription());
                plugins.add(plugin);
            }
        }
        showCloudChooser(plugins, descriptions);
    }

    protected abstract boolean isDisabled(CloudPlugin plugin);

    protected abstract void showCloudChooser(List<CloudPlugin> plugins, List<String> descriptions);

    protected void changeCloud(String newCloudId) {
        final String oldCloudId = _bugCollection.getCloud().getPlugin().getId();
        if (!oldCloudId.equals(newCloudId)) {
            _bugCollection.getProject().setCloudId(newCloudId);
            backgroundExecutor.execute(new Runnable() {
                public void run() {
                    _bugCollection.reinitializeCloud();
                    Cloud cloud = _bugCollection.getCloud();
                    if (cloud != null)
                        cloud.waitUntilIssueDataDownloaded();
                    updateCloudListeners(_bugCollection);
                    refresh();
                }
            });
            refresh();
        }
    }

    public void setBugCollection(BugCollection bugCollection) {
        updateCloudListeners(bugCollection);
        _bugCollection = bugCollection;
        _bugInstance = null;
        _bugAspects = null;
        refresh();
    }

    public void setBugInstance(final BugInstance bugInstance) {
        setBugs(bugInstance, null);
    }

    public void setBugAspects(BugAspects aspects) {
        setBugs(null, aspects);
    }

    private void setBugs(BugInstance bugInstance, BugAspects bugAspects) {
        if (_bugInstance == bugInstance && _bugAspects == bugAspects)
            return;
        if (!canNavigateAway())
            return;

        _bugInstance = bugInstance;
        _bugAspects = bugAspects;
        refresh();
    }

    public boolean canNavigateAway() {
        if (commentWasChanged()) {
//            if (getSelectedBugs().size() > 1) {
//                int result = JOptionPane.showOptionDialog(this, "You have unsaved comments.", "Unsaved Comments",
//                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
//                        new String[]{"Continue Editing", "Discard"},
//                        "Continue Editing");
//                boolean discard = result == 1;
//                if (discard)
//                    cancelClicked();
//                else
//                    commentBox.requestFocus();
//                return discard; // return true if user clicked "Discard"
//            } else {
            submitComment(getSelectedBugs());
//            }
            return true;
        } else {
            return true;
        }
    }

    private boolean confirmAnnotation(List<BugInstance> selectedBugs) {

        String[] options = {L10N.getLocalString("dlg.save_btn", "Save"),
                L10N.getLocalString("dlg.dontsave_btn", "Don't Save"),
                L10N.getLocalString("dlg.save_dont_ask_btn", "Save, Always")};
        if (dontShowAnnotationConfirmation)
            return true;
        if (lastConfirmed.equals(new HashSet<BugInstance>(selectedBugs)))
            return true;
        int choice = JOptionPane
                .showOptionDialog(
                        this,
                        MessageFormat.format(L10N.getLocalString("dlg.changing_text_lbl",
                                "This will overwrite any existing reviews of\n" +
                                        "the {0} bugs in this folder and subfolders.\n" +
                                        "Are you sure?"),
                                selectedBugs.size()),
                        L10N.getLocalString("dlg.annotation_change_ttl", "Save Reviews?"),
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        switch (choice) {
            case 0:
                // don't ask for this set of bugs again
                lastConfirmed = new HashSet<BugInstance>(selectedBugs);
                return true;
            case 1:
                return false;
            case 2:
                dontShowAnnotationConfirmation = true;
                return true;
            default:
                return true;
        }

    }

    protected boolean changeDesignationOfBug(final BugInstance bug, final String designationKey) {
        String oldValue = bug.getUserDesignationKey();
        if (designationKey.equals(oldValue))
            return false;
        backgroundExecutor.execute(new Runnable() {
            public void run() {
                bug.setUserDesignationKey(designationKey, _bugCollection);
                refresh();
            }
        });
        return true;
    }

    public void refresh() {
        updateBugCommentsView();
    }

    private void updateCloudListeners(BugCollection newBugCollection) {
        final Cloud newCloud = newBugCollection == null ? null : newBugCollection.getCloud();
        if (_bugCollection != null) {
            //noinspection ObjectEquality
            if (lastCloud != newCloud) {
                if (lastCloud != null) {
                    lastCloud.removeStatusListener(_cloudStatusListener);
                }
            }
        }
        if (lastCloud != newCloud && newCloud != null) {
            lastCloud = newCloud;
            newCloud.addStatusListener(_cloudStatusListener);
        }
    }


    private void updateBugCommentsView() {

        //TODO: fix cancel button
        List<BugInstance> bugs = getSelectedBugs();
        if (_bugCollection == null) {
            signInOutLink.setVisible(false);
            cloudDetailsLabel.setText("");
            cloudReportPane.setText("");
            titleLabel.setText("<html>Reviews");
            return;
        }
        updateHeader();
        final Cloud cloud = _bugCollection.getCloud();
        final CloudPlugin plugin = cloud.getPlugin();
        String details = plugin.getDetails();
        cloudDetailsLabel.setText(details);

        if (bugs.isEmpty()) {
            setCanAddComments(false, false);
            return;
        }

        String report;
        long lastSaved = -1;
        if (bugs.size() > 1) {
            int totalReviews = 0;
            int bugsWithReviews = 0;
            for (BugInstance bug : bugs) {
                long newTs = cloud.getUserTimestamp(bug);
                if (bug.hasSomeUserAnnotation() && newTs > 0 && (lastSaved == -1 || lastSaved < newTs)) {
                    lastSaved = newTs;
                }
                int reviewers = cloud.getNumberReviewers(bug);
                if (reviewers > 0)
                    bugsWithReviews++;
                totalReviews += reviewers;
            }
            report = bugs.size() + " bug" + (bugs.size() == 1 ? "" : "s") + " selected\n";
            report += bugsWithReviews + " reviewed bug" + (bugsWithReviews == 1 ? "" : "s")
                    + " / " + totalReviews + " total review" + (totalReviews == 1 ? "" : "s");
        } else {
            BugInstance bug = bugs.get(0);
            if (bug.hasSomeUserAnnotation()) {
                lastSaved = bug.getUserTimestamp();
            }
            report = cloud.getCloudReport(bug);
        }
        setLastSaved(lastSaved);
        setCloudReportText(report);
        CommentInfo commentInfo = new CommentInfo().invoke();
        boolean sameText = commentInfo.isSameText();
        String txt = commentInfo.getTxt();
        if (!sameText) {
            txt = DEFAULT_VARIOUS_COMMENTS_COMMENT;
            setDefaultComment(txt);
        } else {
            if (txt == null || txt.trim().length() == 0) {
                txt = bugs.size() > 1 ? String.format(DEFAULT_COMMENT_MULTI, bugs.size()) : DEFAULT_COMMENT;
                setDefaultComment(txt);
            } else {
                resetCommentBoxFont();
                setCommentText(txt);
            }
        }

        setCanAddComments(cloud.canStoreUserAnnotation(bugs.get(0)), false);
        updateSaveButton();
    }

    private boolean updatingHeader = false;

    private void updateHeader() {
        final Cloud cloud = _bugCollection.getCloud();
        CloudPlugin plugin = cloud.getPlugin();
        if (hasSelectedBugs()) {
            CommentInfo commentInfo = new CommentInfo().invoke();
            boolean sameDesignation = commentInfo.isSameDesignation();
            String designation = commentInfo.getDesignation();
            if (!sameDesignation)
                designation = UserDesignation.UNCLASSIFIED.name();
            updatingHeader = true;
            designationCombo.setSelectedIndex(I18N.instance().getUserDesignationKeys(true).indexOf(designation));
            updatingHeader = false;
            setCanAddComments(true, true);
        } else {
            setCanAddComments(false, false);
        }

        final Cloud.SigninState state = cloud.getSigninState();
        final String stateStr = state == Cloud.SigninState.NO_SIGNIN_REQUIRED ? "" : "" + state;
        final String userStr = cloud.getUser() == null ? "" : cloud.getUser();
        if (plugin.getId().equals("edu.umd.cs.findbugs.cloud.doNothingCloud"))
            titleLabel.setText("<html><b>Reviews disabled");
        else
            titleLabel.setText("<html><b>Reviews - " + cloud.getCloudName() + "</b>"
                    + "<br><font style='font-size: x-small;color:darkgray'>" + stateStr
                    + (userStr.length() > 0 ? " - " + userStr : ""));
        switch (state) {
            case NO_SIGNIN_REQUIRED:
            case SIGNING_IN:
                signInOutLink.setVisible(false);
                break;
            case SIGNED_OUT:
            case SIGNIN_FAILED:
            case UNAUTHENTICATED:
                setSignInOutText("sign in");
                signInOutLink.setVisible(true);
                break;
            case SIGNED_IN:
                setSignInOutText("sign out");
                signInOutLink.setVisible(true);
                break;
            default:
        }
        if (cloud.getPlugin().getId().equals("edu.umd.cs.findbugs.cloud.doNothingCloud")) {
            setSignInOutText("enable cloud plugin...");
            signInOutLink.setVisible(true);
        }
    }

    private void setCanAddComments(boolean canClick, boolean canEnter) {
        submitCommentButton.setEnabled(canClick || canEnter);
        designationCombo.setEnabled(canClick || canEnter);
        commentBox.setEnabled(canClick || canEnter);
    }

    private void setCloudReportText(final String report) {
        cloudReportPane.setText(report);
    }

    protected abstract void setSignInOutText(String buttonText);

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3), null));
        final JPanel spacer1 = new JPanel();
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 6;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(spacer1, gbc);
        _cloudReportScrollPane = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 6;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        mainPanel.add(_cloudReportScrollPane, gbc);
        cloudReportPane = new JTextArea();
        cloudReportPane.setEditable(false);
        cloudReportPane.setText("<html>\r\n  <head>\r\n    \r\n  </head>\r\n  <body>\r\n  </body>\r\n</html>\r\n");
        _cloudReportScrollPane.setViewportView(cloudReportPane);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setBackground(new Color(-3355444));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 6;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel1, gbc);
        panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(-16751002)), null));
        titleLabel = new JLabel();
        titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 14));
        titleLabel.setForeground(new Color(-16777216));
        titleLabel.setText("FindBugs Cloud - signed in");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel1.add(titleLabel, gbc);
        dumbPanelSignInOutLink = new JPanel();
        dumbPanelSignInOutLink.setLayout(new GridBagLayout());
        dumbPanelSignInOutLink.setOpaque(false);
        dumbPanelSignInOutLink.setPreferredSize(new Dimension(50, 10));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(dumbPanelSignInOutLink, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        dumbPanelSignInOutLink.add(signInOutLink, gbc);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        panel2.setVisible(false);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);
        mainPanel.add(panel2, gbc);
        cloudDetailsLabel = new JTextArea();
        cloudDetailsLabel.setEditable(false);
        cloudDetailsLabel.setFont(new Font(cloudDetailsLabel.getFont().getName(), Font.ITALIC, 10));
        cloudDetailsLabel.setForeground(new Color(-10066330));
        cloudDetailsLabel.setLineWrap(true);
        cloudDetailsLabel.setMaximumSize(new Dimension(100, 50));
        cloudDetailsLabel.setMinimumSize(new Dimension(50, 16));
        cloudDetailsLabel.setOpaque(false);
        cloudDetailsLabel.setPreferredSize(new Dimension(100, 31));
        cloudDetailsLabel.setText("Comments are stored on the FindBugs Cloud at http://findbugs-cloud.appspot.com");
        cloudDetailsLabel.setWrapStyleWord(true);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(cloudDetailsLabel, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 5;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(panel3, gbc);
        designationCombo = new WideComboBox();
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(5, 0, 0, 0);
        panel3.add(designationCombo, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        gbc.gridheight = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(scrollPane1, gbc);
        scrollPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null));
        commentBox = new JTextArea();
        commentBox.setRows(5);
        commentBox.setText(" ");
        scrollPane1.setViewportView(commentBox);
        submitCommentButton = new JButton();
        submitCommentButton.setText("Save");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel3.add(submitCommentButton, gbc);
        lastSavedLabel = new JLabel();
        lastSavedLabel.setFont(new Font(lastSavedLabel.getFont().getName(), Font.ITALIC, 9));
        lastSavedLabel.setText("saved at");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel3.add(lastSavedLabel, gbc);
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 1;
        panel3.add(cancelLink, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    private class MyCloudStatusListener implements Cloud.CloudStatusListener {
        public void handleIssueDataDownloadedEvent() {
        }


        public void handleStateChange(final Cloud.SigninState oldState, final Cloud.SigninState state) {
            updateHeader();
        }


    }

    private interface BugAction {
        void execute(BugInstance bug);
    }

    private static class NowExecutor implements Executor {
        public void execute(Runnable command) {
            command.run();
        }
    }

    private class CommentInfo {
        private String txt;
        private boolean sameText;
        private String designation;
        private boolean sameDesignation;

        public String getTxt() {
            return txt;
        }

        public boolean isSameText() {
            return sameText;
        }

        public String getDesignation() {
            return designation;
        }

        public boolean isSameDesignation() {
            return sameDesignation;
        }

        public CommentInfo invoke() {
            txt = null;
            sameText = true;
            designation = null;
            sameDesignation = true;
            for (BugInstance bug : getSelectedBugs()) {
                String newText = bug.getAnnotationText();
                if (txt == null)
                    txt = newText;
                else {
                    if (!nullSafeEquals(txt, newText))
                        sameText = false;
                }

                String newDesignation = bug.getUserDesignationKey();
                if (designation == null)
                    designation = newDesignation;
                else {
                    if (!nullSafeEquals(designation, newDesignation))
                        sameDesignation = false;
                }
            }
            return this;
        }
    }
}