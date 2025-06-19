import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.datatransfer.*;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.TreeExpansionEvent;

public class Main extends JFrame {
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private JTree directoryTree;
    private DefaultTreeModel treeModel;
    private JPanel fileViewPanel;
    private File currentFile;
    private JPanel lastHoveredPanel = null;
    private JPanel lastClickedPanel = null;
    private final Color HOVER_COLOR = new Color(255, 255, 255, 50);
    public static final Color CLICK_COLOR = new Color(147, 112, 219);
    private File clipboardFile = null;
    private boolean cutMode = false;
    private JButton themeButton;
    private JButton newButton;
    private JButton sortButton;
    private JButton viewButton;
    private static final String DUMMY_NODE = "Loading...";
    private JComboBox<String> searchTypeCombo;
    private final java.util.Set<File> multiSelectedFiles = new java.util.HashSet<>();

    private static final Color LIGHT_BG = Color.WHITE;
    public static final Color DARK_BG = new Color(49, 49, 49);
    private static final Color LIGHT_TEXT = Color.BLACK;
    private static final Color DARK_TEXT = Color.WHITE;
    private boolean isDarkTheme = true;
    private static boolean CURRENT_DARK_THEME = true;
    private File selectedFile = null;
    private File displayedDirectory;
    private SwingWorker<List<File>, Void> currentSearchWorker;
    private Timer searchDelayTimer;

    private String currentViewMode = "large-icons";
    private String currentSortCriteria = "name";
    private String currentFileTypeFilter = "";

    // COROL
    private Color folderBackgroundColor = DARK_BG;
    public static final Color treeBackgroundColor = new Color(32, 32, 32);
    public static final Color topBackgroundColor = new Color(189, 181, 213);
    public static final Color panelHoverColor = new Color(189, 181, 213);
    public static final Color panelSelectionColor = new Color(0, 100, 100);
    public static final Color textSelectionColor = new Color(0, 255, 255);
    public static final Color propertiesColor = new Color(35, 35, 35);

    private List<File> navigationHistory = new ArrayList<>();
    private int historyIndex = -1;

    private JButton backButton;
    private JButton forwardButton;
    private JTextField searchField;
    private JProgressBar progressBar;
    private JPanel actionPanel;
    private JPopupMenu newMenu;
    private JPopupMenu sortMenu;
    private JPopupMenu viewMenu;

    // STATUS PANEL
    private JPanel statusPanel;
    private JLabel statusLabel;
    private int selectedCount = 0;

    public Main() {
        super("File Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(folderBackgroundColor);

        UIManager.put("Component.arrowType", "chevron");
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("ProgressBar.arc", 10);

        FlatDarkLaf.setup();

        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // PANEL SA TopPanel and actionPanel
        JPanel topContainerPanel = new JPanel();
        topContainerPanel.setLayout(new BorderLayout());
        topContainerPanel.add(topPanel, BorderLayout.NORTH);

        // ACTIONPAENL
        JPanel actionPanel = createActionPanel();
        topContainerPanel.add(actionPanel, BorderLayout.SOUTH);

        add(topContainerPanel, BorderLayout.NORTH);

        fileViewPanel = new JPanel();
        fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        fileViewPanel.setBackground(folderBackgroundColor);

        enableFilePanelDragAndDrop();
        showLoadingEffect();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("My Computer");
        treeModel = new DefaultTreeModel(root);
        directoryTree = new JTree(treeModel);
        directoryTree.setCellRenderer(new FileTreeCellRenderer());
        directoryTree.setRowHeight(28);

        UIManager.put("Tree.leftChildIndent", 24);
        UIManager.put("Tree.rightChildIndent", 8);
        JScrollPane treeScroll = new JScrollPane(directoryTree);
        treeScroll.setPreferredSize(new Dimension(250, getHeight()));
        treeScroll.getViewport().setBackground(Color.WHITE);

        directoryTree.addMouseMotionListener(new MouseMotionAdapter() {
            private TreePath lastHoverPath;

            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath hoverPath = directoryTree.getPathForLocation(e.getX(), e.getY());
                if (hoverPath != null && !hoverPath.equals(lastHoverPath)) {
                    if (lastHoverPath != null) {
                        directoryTree.repaint(directoryTree.getPathBounds(lastHoverPath));
                    }

                    lastHoverPath = hoverPath;
                    directoryTree.repaint(directoryTree.getPathBounds(hoverPath));
                } else if (hoverPath == null && lastHoverPath != null) {

                    directoryTree.repaint(directoryTree.getPathBounds(lastHoverPath));
                    lastHoverPath = null;
                }
            }

        });

        directoryTree.setDragEnabled(true);
        directoryTree.setDropMode(DropMode.ON);
        directoryTree.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop())
                    return false;
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                TreePath path = dl.getPath();
                if (path == null)
                    return false;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                return userObject instanceof File && ((File) userObject).isDirectory();
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support))
                    return false;
                try {
                    JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                    TreePath path = dl.getPath();
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    File targetDir = (File) node.getUserObject();

                    Transferable t = support.getTransferable();
                    java.util.List<File> fileList = (java.util.List<File>) t
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File fileToMove : fileList) {
                        File newFile = new File(targetDir, fileToMove.getName());
                        Files.move(fileToMove.toPath(), newFile.toPath());
                    }
                    displayFiles(targetDir);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(Main.this, "The file already exists.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
        updateThemeColors(true);

        JScrollPane fileScroll = new JScrollPane(fileViewPanel);
        fileScroll.getViewport().setBackground(folderBackgroundColor);

        fileScroll.getVerticalScrollBar().setUnitIncrement(30);
        fileScroll.getHorizontalScrollBar().setUnitIncrement(30);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(fileScroll, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightPanel);
        mainSplit.setDividerLocation(250);

        add(mainSplit, BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);

        directoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getChildCount() == 1 && DUMMY_NODE.equals(node.getChildAt(0).toString())) {

                    node.removeAllChildren();
                    File dir = (File) node.getUserObject();
                    File[] subFolders = fileSystemView.getFiles(dir, true);
                    for (File f : subFolders) {
                        if (f.isDirectory()) {
                            DefaultMutableTreeNode child = new DefaultMutableTreeNode(f);
                            if (hasSubFolders(f)) {
                                child.add(new DefaultMutableTreeNode(DUMMY_NODE));
                            }
                            node.add(child);
                        }
                    }
                    treeModel.reload(node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        directoryTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) directoryTree
                        .getLastSelectedPathComponent();
                if (selectedNode != null) {
                    Object userObject = selectedNode.getUserObject();
                    if (userObject instanceof File) {
                        File selectedDir = (File) userObject;
                        currentFile = selectedDir;
                        displayFiles(selectedDir);
                        clearSelectionEffect();
                        updateHistory(selectedDir);
                    }
                }
            }
        });

        setVisible(true);

        SwingUtilities.invokeLater(() -> {
            createTree(root);

            File desktop = fileSystemView.getHomeDirectory();
            DefaultMutableTreeNode desktopNode = findNodeForFile(root, desktop);

            if (desktopNode != null) {
                TreePath desktopPath = new TreePath(desktopNode.getPath());
                directoryTree.setSelectionPath(desktopPath);
                directoryTree.scrollPathToVisible(desktopPath);
                displayFiles(desktop);
                updateHistory(desktop);
            }
        });

    }

    private JPanel createStatusPanel() {
        statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(folderBackgroundColor);
        statusLabel = new JLabel();
        statusLabel.setForeground(Color.WHITE);
        statusPanel.add(statusLabel);
        return statusPanel;
    }

    private DefaultMutableTreeNode findNodeForFile(DefaultMutableTreeNode root, File target) {
        if (root.getUserObject() instanceof File) {
            File nodeFile = (File) root.getUserObject();
            if (nodeFile.equals(target)) {
                return root;
            }
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            DefaultMutableTreeNode result = findNodeForFile(child, target);
            if (result != null)
                return result;
        }
        return null;
    }

    private JPanel createActionPanel() {
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionPanel.setBackground(folderBackgroundColor);

        newButton = createStyledButton("New", "/icons/new.png", folderBackgroundColor);
        newMenu = new JPopupMenu();
        JMenuItem newFileItem = new JMenuItem("File");
        newFileItem.addActionListener(e -> createNewFileOrFolder(true));
        newMenu.add(newFileItem);
        JMenuItem newFolderItem = new JMenuItem("Folder");
        newFolderItem.addActionListener(e -> createNewFileOrFolder(false));
        newMenu.add(newFolderItem);
        newButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                newMenu.show(newButton, e.getX(), e.getY());
            }

        });

        // COPY
        JButton copyButton = createStyledButton("", "/icons/copy.png", folderBackgroundColor);
        copyButton.addActionListener(e -> {
            if (selectedFile != null) {
                clipboardFile = selectedFile;
                cutMode = false;

                List<File> files = Collections.singletonList(selectedFile);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new FileTransferable(files), null);
                JOptionPane.showMessageDialog(this,
                        (selectedFile.isDirectory() ? "Folder" : "File") + " copied to clipboard.",
                        "Copy",
                        JOptionPane.INFORMATION_MESSAGE);
                System.out.println("Copied: " + clipboardFile.getAbsolutePath());
            }
        });
        // CUT
        JButton cutButton = createStyledButton("", "/icons/cut.png", folderBackgroundColor);
        cutButton.addActionListener(e -> {
            if (selectedFile != null) {
                clipboardFile = selectedFile;
                cutMode = true;
                List<File> files = Collections.singletonList(selectedFile);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new FileTransferable(files), null);
                JOptionPane.showMessageDialog(this,
                        (selectedFile.isDirectory() ? "Folder" : "File") + " cut to clipboard.",
                        "Cut",
                        JOptionPane.INFORMATION_MESSAGE);
                System.out.println("Cut: " + clipboardFile.getAbsolutePath());
            }
        });

        JButton pasteButton = createStyledButton("", "/icons/paste.png", folderBackgroundColor);
        pasteButton.addActionListener(e -> {
            File targetDir = null;
            if (selectedFile != null && selectedFile.isDirectory()) {
                targetDir = selectedFile;
            } else if (currentFile != null && currentFile.isDirectory()) {
                targetDir = currentFile;
            } else if (displayedDirectory != null && displayedDirectory.isDirectory()) {
                targetDir = displayedDirectory;
            } else {
                targetDir = fileSystemView.getHomeDirectory();
            }

            if (clipboardFile != null && targetDir != null && targetDir.isDirectory()) {
                File targetFile = new File(targetDir, clipboardFile.getName());
                if (targetFile.exists()) {
                    JOptionPane.showMessageDialog(this,
                            "The file " + clipboardFile.getName() + " already exists in the target directory.\n"
                                    + "The file names are the same. Please rename or choose a different location.",
                            "Paste Error",
                            JOptionPane.ERROR_MESSAGE);

                    String newName = JOptionPane.showInputDialog(this,
                            "Enter a new name:", clipboardFile.getName());
                    if (newName == null || newName.trim().isEmpty()) {
                        return; // User cancelled or entered invalid name
                    }
                    File renamedTargetFile = new File(targetDir, newName.trim());
                    if (renamedTargetFile.exists()) {
                        JOptionPane.showMessageDialog(this,
                                "A file or folder with the new name also exists. Paste cancelled.",
                                "Paste Error",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    targetFile = renamedTargetFile;
                }
                try {
                    if (cutMode) {
                        Files.move(clipboardFile.toPath(), targetFile.toPath());
                        clipboardFile = null;
                        cutMode = false;
                        JOptionPane.showMessageDialog(this,
                                (targetFile.isDirectory() ? "Folder" : "File") + " moved successfully.",
                                "Paste",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        if (clipboardFile.isDirectory()) {
                            targetFile.mkdir();
                        } else {
                            Files.copy(clipboardFile.toPath(), targetFile.toPath());
                        }
                        JOptionPane.showMessageDialog(this,
                                (targetFile.isDirectory() ? "Folder" : "File") + " pasted successfully!!",
                                "Paste",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    displayFiles(targetDir);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error during paste operation.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a directory to paste into.",
                        "Paste Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton renameButton = createStyledButton("", "/icons/rename.png", folderBackgroundColor);
        renameButton.addActionListener(e -> {
            if (selectedFile != null) {
                renameFile(selectedFile);
            }
        });

        JButton deleteButton = createStyledButton("", "/icons/delete.png", folderBackgroundColor);
        deleteButton.addActionListener(e -> {
            if (currentFile != null) {
                deleteFile(currentFile);
            }
        });

        sortButton = createStyledButton("Sort", "/icons/sort.png", folderBackgroundColor);
        sortMenu = new JPopupMenu();

        JCheckBoxMenuItem sortByName = new JCheckBoxMenuItem("Name");
        sortByName.addActionListener(e -> {
            clearSortSelections(sortMenu);
            sortByName.setSelected(true);
            currentSortCriteria = "name";
            currentFileTypeFilter = "";
            applyCurrentSortAndFilter();
        });
        sortMenu.add(sortByName);

        JCheckBoxMenuItem sortByDateModified = new JCheckBoxMenuItem("Date Modified");
        sortByDateModified.addActionListener(e -> {
            clearSortSelections(sortMenu);
            sortByDateModified.setSelected(true);
            currentSortCriteria = "dateModified";
            currentFileTypeFilter = "";
            applyCurrentSortAndFilter();
        });
        sortMenu.add(sortByDateModified);

        JMenu typeSubmenu = new JMenu("Type");

        String[] types = { ".txt", ".pdf", ".java", ".png", ".jpg" };
        ButtonGroup typeGroup = new ButtonGroup();
        for (String type : types) {
            JCheckBoxMenuItem typeItem = new JCheckBoxMenuItem(type);
            typeItem.addActionListener(e -> {
                clearSortSelections(sortMenu);
                typeItem.setSelected(true);
                filterFilesByExactType(type);
            });
            typeSubmenu.add(typeItem);
            typeGroup.add(typeItem);
        }

        sortMenu.add(typeSubmenu);

        sortButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sortMenu.show(sortButton, e.getX(), e.getY());
            }
        });

        actionPanel.add(sortButton);

        viewButton = createStyledButton("View", "/icons/view.png", folderBackgroundColor);
        viewMenu = new JPopupMenu();

        // SA LARGE ICONS
        JCheckBoxMenuItem largeIconsItem = new JCheckBoxMenuItem("Large Icons");
        largeIconsItem.addActionListener(e -> {
            clearViewSelections(viewMenu);
            largeIconsItem.setSelected(true);
            System.out.println("Large Icons view selected.");
            currentViewMode = "large-icons";
            applyCurrentViewMode();
        });
        viewMenu.add(largeIconsItem);

        // SA DETAILS
        JCheckBoxMenuItem detailsItem = new JCheckBoxMenuItem("Details");
        detailsItem.addActionListener(e -> {
            clearViewSelections(viewMenu);
            detailsItem.setSelected(true);
            System.out.println("Details view selected.");
            currentViewMode = "details";
            applyCurrentViewMode();
        });
        viewMenu.add(detailsItem);

        viewButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                viewMenu.show(viewButton, e.getX(), e.getY());
            }
        });

        actionPanel.add(newButton);
        actionPanel.add(cutButton);
        actionPanel.add(copyButton);
        actionPanel.add(pasteButton);
        actionPanel.add(renameButton);
        actionPanel.add(deleteButton);
        actionPanel.add(sortButton);
        actionPanel.add(viewButton);

        return actionPanel;
    }

    private void clearViewSelections(JPopupMenu menu) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JCheckBoxMenuItem) {
                ((JCheckBoxMenuItem) component).setSelected(false);
            }
        }
    }

    private void clearSortSelections(JPopupMenu menu) {
        for (Component component : menu.getComponents()) {
            if (component instanceof JCheckBoxMenuItem) {
                ((JCheckBoxMenuItem) component).setSelected(false);
            } else if (component instanceof JMenu) {
                JMenu submenu = (JMenu) component;
                for (Component subComponent : submenu.getMenuComponents()) {
                    if (subComponent instanceof JCheckBoxMenuItem) {
                        ((JCheckBoxMenuItem) subComponent).setSelected(false);
                    }
                }
            }
        }
    }

    private void applyCurrentViewMode() {
        if (currentFile != null) {
            displayFiles(currentFile);
        }
    }

    private void updateThemeColors(boolean isDark) {
        isDarkTheme = isDark;
        Color bg = isDark ? DARK_BG : LIGHT_BG;
        Color fg = isDark ? DARK_TEXT : LIGHT_TEXT;
        CURRENT_DARK_THEME = isDark;

        folderBackgroundColor = isDark ? DARK_BG : LIGHT_BG;

        for (Component c : getContentPane().getComponents()) {
            if (c instanceof JPanel panel) {
                for (Component sub : panel.getComponents()) {
                    if (sub instanceof JPanel subPanel && subPanel.getLayout() instanceof FlowLayout) {
                        subPanel.setBackground(bg);
                        for (Component subSub : subPanel.getComponents()) {
                            if (subSub instanceof JButton btn) {
                                btn.setBackground(bg);
                                btn.setForeground(isDark ? DARK_TEXT : Color.BLACK);
                            }
                            if (subSub instanceof JLabel lbl) {
                                lbl.setForeground(fg);
                            }
                            if (newMenu != null)
                                updateMenuTextColors(newMenu, fg);
                            if (sortMenu != null)
                                updateMenuTextColors(sortMenu, fg);
                            if (viewMenu != null)
                                updateMenuTextColors(viewMenu, fg);
                        }
                    }
                }
            }

        }

        // Directory tree (left panel)
        directoryTree.setBackground(bg);
        directoryTree.setForeground(fg);
        directoryTree.setCellRenderer(new FileTreeCellRenderer());

        // File view panel (right panel)
        fileViewPanel.setBackground(bg);
        fileViewPanel.setForeground(fg);

        for (Component comp : fileViewPanel.getComponents()) {
            if (comp instanceof JPanel filePanel) {
                filePanel.setBackground(bg);
                for (Component inner : filePanel.getComponents()) {
                    if (inner instanceof JLabel lbl) {
                        lbl.setForeground(fg);
                    }
                }
            }
        }

        if (statusPanel != null) {
            statusPanel.setBackground(bg);
            for (Component c : statusPanel.getComponents()) {
                if (c instanceof JLabel lbl) {
                    lbl.setForeground(fg);
                }
            }
        }

        directoryTree.setCellRenderer(new FileTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                Color fg = isDarkTheme ? DARK_TEXT : LIGHT_TEXT;
                Component comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                comp.setForeground(fg);
                return comp;
            }
        });

        SwingUtilities.updateComponentTreeUI(this);
    }

    private boolean hasSubFolders(File dir) {
        File[] files = dir.listFiles();
        if (files == null)
            return false;
        for (File f : files) {
            if (f.isDirectory())
                return true;
        }
        return false;
    }

    private void updateMenuTextColors(JPopupMenu menu, Color fg) {
        for (Component comp : menu.getComponents()) {
            if (comp instanceof JMenuItem item) {
                item.setForeground(fg);
                if (item instanceof JMenu submenu) {
                    for (Component subComp : submenu.getMenuComponents()) {
                        if (subComp instanceof JMenuItem subItem) {
                            subItem.setForeground(fg);
                        }
                    }
                }
            }
        }
    }

    private void showLoadingEffect() {
        fileViewPanel.removeAll();
        lastClickedPanel = null;
        lastHoveredPanel = null;
        fileViewPanel.setLayout(new BorderLayout());

        JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        loadingPanel.setOpaque(false);

        JLabel loadingLabel = new JLabel("Loading...");
        loadingLabel.setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        loadingPanel.add(loadingLabel);

        fileViewPanel.add(loadingPanel, BorderLayout.NORTH);

        fileViewPanel.revalidate();
        fileViewPanel.repaint();
    }

    private void sortFilesBy(String criteria) {
        if (currentFile == null || !currentFile.isDirectory())
            return;

        File[] files = fileSystemView.getFiles(currentFile, true);

        Arrays.sort(files, (f1, f2) -> {

            switch (criteria) {
                case "name":
                    return f1.getName().compareToIgnoreCase(f2.getName());
                case "dateModified":
                    return Long.compare(f2.lastModified(), f1.lastModified());
                default:
                    return 0;
            }
        });

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            displaySortedLargeIconsView(Arrays.asList(files));
            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        return (lastIndex == -1) ? "" : name.substring(lastIndex + 1).toLowerCase();
    }

    private void displaySortedLargeIconsView(List<File> sortedFiles) {
        fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        for (File file : sortedFiles) {
            JPanel filePanel = createFilePanel(file);
            fileViewPanel.add(filePanel);
        }
    }

    private void displayLargeIconsView(File directory) {
        if (fileViewPanel == null || directory == null)
            return;

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
            fileViewPanel.setBackground(folderBackgroundColor);

            File[] files = fileSystemView.getFiles(directory, true);
            for (File file : files) {
                JPanel filePanel = createFilePanel(file);
                fileViewPanel.add(filePanel);
            }

            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private void addFilePanelBackgroundMenu() {
        fileViewPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    Component clicked = fileViewPanel.getComponentAt(e.getPoint());
                    // Only show menu if background (not a file/folder panel) is clicked
                    if (clicked == fileViewPanel) {
                        showBackgroundMenu(e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private void showBackgroundMenu(int x, int y) {
        JPopupMenu backgroundMenu = new JPopupMenu();

        JMenu newMenu = new JMenu("New");
        JMenuItem newFileItem = new JMenuItem("File");
        newFileItem.addActionListener(e -> createNewFileOrFolder(true));
        JMenuItem newFolderItem = new JMenuItem("Folder");
        newFolderItem.addActionListener(e -> createNewFileOrFolder(false));
        newMenu.add(newFileItem);
        newMenu.add(newFolderItem);
        backgroundMenu.add(newMenu);

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> {
            for (ActionListener al : Arrays.asList(createActionPanel().getComponents()).stream()
                    .filter(c -> c instanceof JButton && ((JButton) c).getActionCommand().equals("Paste"))
                    .flatMap(c -> Arrays.stream(((JButton) c).getActionListeners()))
                    .toArray(ActionListener[]::new)) {
                al.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null));
            }
        });
        backgroundMenu.add(pasteItem);

        backgroundMenu.show(fileViewPanel, x, y);
    }

    private void enableFilePanelDragAndDrop() {
        fileViewPanel.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop())
                    return false;
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support))
                    return false;
                try {
                    Transferable t = support.getTransferable();
                    java.util.List<File> droppedFiles = (java.util.List<File>) t
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    File targetDir = currentFile != null && currentFile.isDirectory() ? currentFile
                            : fileSystemView.getHomeDirectory();

                    for (File fileToMove : droppedFiles) {
                        if (fileToMove.equals(targetDir))
                            continue;
                        File newFile = new File(targetDir, fileToMove.getName());
                        if (!fileToMove.equals(newFile)) {
                            Files.move(fileToMove.toPath(), newFile.toPath());
                        }
                    }
                    displayFiles(targetDir);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(Main.this, "Drag-and-drop move failed.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
    }

    private void displayDetailsView(File directory) {
        if (fileViewPanel == null || directory == null)
            return;

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            fileViewPanel.setLayout(new BorderLayout());
            fileViewPanel.setBackground(folderBackgroundColor);

            // TABLEEE
            String[] columnNames = { "Icon", "Name", "Date Modified", "Type", "Size" };
            File[] files = fileSystemView.getFiles(directory, true);
            Object[][] data = new Object[files.length][5];

            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                data[i][0] = fileSystemView.getSystemIcon(file);
                data[i][1] = file.getName();
                data[i][2] = new Date(file.lastModified());
                data[i][3] = file.isDirectory() ? "Folder" : getFileExtension(file);
                data[i][4] = file.isDirectory() ? "" : formatFileSize(file.length());
            }

            JTable detailsTable = new JTable(new javax.swing.table.DefaultTableModel(data, columnNames) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            });

            final int[] lastHoveredRow = { -1 };
            final int[] lastClickedRow = { -1 };

            detailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                        boolean hasFocus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                    if (row == lastClickedRow[0]) {
                        setBackground(CLICK_COLOR);
                        setForeground(Color.WHITE);
                    } else if (row == lastHoveredRow[0]) {
                        setBackground(panelHoverColor);
                        setForeground(Color.BLACK);
                    } else {
                        setBackground(folderBackgroundColor);
                        setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
                    }

                    if (column == 0) {
                        setHorizontalAlignment(CENTER);
                        setIcon(value instanceof Icon ? (Icon) value : null);
                        setText("");
                    } else {
                        setHorizontalAlignment(LEFT);
                        setIcon(null);
                        setText(value == null ? "" : value.toString());
                    }

                    return this;
                }
            });

            detailsTable.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = detailsTable.rowAtPoint(e.getPoint());
                    if (row != lastHoveredRow[0]) {
                        if (lastHoveredRow[0] != -1) {
                            Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true).union(
                                    detailsTable.getCellRect(lastHoveredRow[0], detailsTable.getColumnCount() - 1,
                                            true));
                            detailsTable.repaint(r);
                        }
                        lastHoveredRow[0] = row;
                        if (row != -1) {
                            Rectangle r = detailsTable.getCellRect(row, 0, true).union(
                                    detailsTable.getCellRect(row, detailsTable.getColumnCount() - 1, true));
                            detailsTable.repaint(r);
                        }
                    }
                }
            });

            detailsTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (lastHoveredRow[0] != -1) {
                        Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true).union(
                                detailsTable.getCellRect(lastHoveredRow[0], detailsTable.getColumnCount() - 1, true));
                        detailsTable.repaint(r);
                        lastHoveredRow[0] = -1;
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = detailsTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        File file = files[row];
                        lastClickedRow[0] = row;
                        detailsTable.repaint();

                        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                            openFileOrDirectory(file);
                        } else if (SwingUtilities.isRightMouseButton(e)) {
                            Point p = e.getLocationOnScreen();
                            createContextMenu(file, p.x, p.y);
                        }
                    }
                }
            });

            detailsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
            detailsTable.getColumnModel().getColumn(0).setMinWidth(36);
            detailsTable.getColumnModel().getColumn(0).setMaxWidth(48);

            detailsTable.setBackground(folderBackgroundColor);
            detailsTable.setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
            detailsTable.setGridColor(Color.DARK_GRAY);
            detailsTable.setRowHeight(32);

            JTableHeader header = detailsTable.getTableHeader();
            header.setBackground(topBackgroundColor);
            header.setForeground(Color.BLACK);
            header.setFont(new Font("SansSerif", Font.BOLD, 12));
            header.setReorderingAllowed(false);

            JScrollPane scrollPane = new JScrollPane(detailsTable);
            scrollPane.getViewport().setBackground(folderBackgroundColor);

            fileViewPanel.add(scrollPane, BorderLayout.CENTER);

            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private boolean fileOrFolderExists(File parentDir, String name) {
        File[] files = parentDir.listFiles();
        if (files == null)
            return false;
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void createNewFileOrFolder(boolean isFile) {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this,
                    "No directory selected. Please select a directory to create the new item.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!currentFile.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "The selected item is not a directory. Please select a valid directory.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String type = isFile ? "File" : "Folder";
        String name = JOptionPane.showInputDialog(this, "Enter " + type + " Name:", "Create New " + type,
                JOptionPane.PLAIN_MESSAGE);

        if (name != null && !name.trim().isEmpty()) {
            File newItem = new File(currentFile, name.trim());
            try {
                boolean success;
                if (isFile) {
                    success = newItem.createNewFile();
                } else {
                    success = newItem.mkdir();
                }

                if (success) {
                    if (isFile) {
                        String content = JOptionPane.showInputDialog(this, "Enter content for the new file:",
                                "File Content", JOptionPane.PLAIN_MESSAGE);
                        if (content != null && !content.isEmpty()) {
                            try {
                                Files.writeString(newItem.toPath(), content);
                            } catch (IOException e) {
                                JOptionPane.showMessageDialog(this, "Failed to write content to the file.", "Error",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                    JOptionPane.showMessageDialog(this, type + " created successfully.", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    displayFiles(currentFile);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Failed to create " + type + ". It may already exist or the name is invalid.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "An error occurred while creating the " + type + ".", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, type + " creation canceled or invalid name provided.", "Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    public JPanel createTopPanel() {
        themeButton = new JButton();
        themeButton.setFocusPainted(false);
        themeButton.setBackground(panelHoverColor);
        themeButton.setBorderPainted(false);
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.setBackground(panelHoverColor);
        topPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        searchDelayTimer = new Timer(250, e -> doPerformSearch());
        searchDelayTimer.setRepeats(false);

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(panelHoverColor);

        toolbar.add(Box.createHorizontalStrut(16));

        // Back button
        backButton = createStyledButton("", "/icons/back.png", panelHoverColor);
        backButton.addActionListener(e -> navigateBack());
        toolbar.add(backButton);

        toolbar.add(Box.createHorizontalStrut(25));

        // Forward button
        forwardButton = createStyledButton("", "/icons/forward.png", panelHoverColor);
        forwardButton.addActionListener(e -> navigateForward());
        toolbar.add(forwardButton);

        toolbar.add(Box.createHorizontalStrut(16));

        // Refresh button
        JButton refreshButton = createStyledButton("", "/icons/refresh.png", panelHoverColor);
        refreshButton.addActionListener(e -> {
            File desktop = fileSystemView.getHomeDirectory();
            displayFiles(desktop);
            updateHistory(desktop);

            selectDirectoryInTree(desktop);

            clearSelectionEffect();
        });
        toolbar.add(refreshButton);

        toolbar.add(Box.createHorizontalStrut(13));

        // Theme beh
        JButton themeButton = new JButton();
        themeButton.setFocusPainted(false);
        themeButton.setBackground(panelHoverColor);
        themeButton.setBorderPainted(false);

        try {
            java.net.URL iconURL = getClass().getResource("/icons/theme.png");
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                Image scaledIcon = icon.getImage().getScaledInstance(29, 29, Image.SCALE_SMOOTH);
                themeButton.setIcon(new ImageIcon(scaledIcon));
            }
        } catch (Exception e) {
        }

        JPopupMenu themeMenu = new JPopupMenu();
        JMenuItem lightItem = new JMenuItem("Light");
        JMenuItem darkItem = new JMenuItem("Dark");

        lightItem.addActionListener(e -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
                updateThemeColors(false);
                SwingUtilities.updateComponentTreeUI(SwingUtilities.getWindowAncestor(themeButton));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        darkItem.addActionListener(e -> {
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
                updateThemeColors(true);
                SwingUtilities.updateComponentTreeUI(SwingUtilities.getWindowAncestor(themeButton));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        themeMenu.add(lightItem);
        themeMenu.add(darkItem);

        themeButton.addActionListener(e -> {
            themeMenu.show(themeButton, 0, themeButton.getHeight());
        });

        toolbar.add(themeButton);

        searchField = new JTextField(20) {

            private final String placeholder = "Search";

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(Color.LIGHT_GRAY);
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    g2.drawString(placeholder, 10, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
                    g2.dispose();
                }
            }
        };

        searchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                searchField.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                searchField.repaint();
            }
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchField.repaint();
            }
        });

        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                searchField.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                searchField.repaint();
            }
        });

        searchField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        searchField.setForeground(Color.BLACK);
        searchField.setCaretColor(Color.BLACK);
        searchField.setBackground(Color.WHITE);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                restartSearchTimer();
            }
        });

        searchField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });

        String[] fileTypes = { "All", ".txt", ".pdf", ".java", ".png", ".jpg", ".docx", };
        searchTypeCombo = new JComboBox<>(fileTypes);
        searchTypeCombo.setPreferredSize(new Dimension(70, 28));
        searchTypeCombo.setFocusable(false);
        searchTypeCombo.addActionListener(e -> {
            String selectedType = (String) searchTypeCombo.getSelectedItem();
            if ("All".equalsIgnoreCase(selectedType)) {
                currentFileTypeFilter = "";
            } else {
                currentFileTypeFilter = selectedType;
            }
            applyCurrentSortAndFilter();
        });
        searchField.setPreferredSize(new Dimension(180, 27));
        searchField.setMaximumSize(new Dimension(180, 27));

        searchTypeCombo.setPreferredSize(new Dimension(80, 26));
        searchTypeCombo.setMaximumSize(new Dimension(80, 26));

        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
        searchPanel.setBackground(panelHoverColor);
        searchPanel.add(searchField);
        searchPanel.add(Box.createHorizontalGlue());
        searchPanel.add(searchTypeCombo);

        topPanel.add(toolbar, BorderLayout.WEST);
        searchField.setFocusable(true);
        topPanel.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        topPanel.add(searchPanel, BorderLayout.EAST);

        return topPanel;
    }

    private JButton createStyledButton(String text, String iconPath, Color bgColor) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE); // COLOR TO SA TEXT
        button.setFocusPainted(false);

        // SA ICON
        try {
            if (iconPath != null) {
                java.net.URL imgURL = getClass().getResource(iconPath);
                if (imgURL != null) {
                    ImageIcon icon = new ImageIcon(imgURL);
                    Image scaledIcon = icon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH); // Larger icon
                    button.setIcon(new ImageIcon(scaledIcon));
                    button.setIconTextGap(8);
                } else {
                    throw new IllegalArgumentException("Icon not found: " + iconPath);
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading icon: " + e.getMessage());
        }

        if (text != null && !text.isEmpty()) {
            button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            button.setPreferredSize(new Dimension(80, 36));
        } else {
            button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            button.setPreferredSize(new Dimension(36, 36));
        }

        return button;
    }

    private void restartSearchTimer() {
        if (searchDelayTimer.isRunning()) {
            searchDelayTimer.restart();
        } else {
            searchDelayTimer.start();
        }
    }

    private void doPerformSearch() {
        String searchText = searchField.getText().trim();
        if (currentSearchWorker != null && !currentSearchWorker.isDone()) {
            currentSearchWorker.cancel(true);
        }
        if (searchText.isEmpty()) {
            if (currentFile != null) {
                SwingUtilities.invokeLater(() -> displayFiles(currentFile));
            }
            return;
        }
        performSearch(searchText);
    }

    private void updateHistory(File file) {
        if (historyIndex + 1 < navigationHistory.size()) {
            navigationHistory = navigationHistory.subList(0, historyIndex + 1);
        }
        navigationHistory.add(file);
        historyIndex++;
        updateButtonState();
    }

    private void navigateBack() {
        if (historyIndex > 0) {
            historyIndex--;
            File previousFile = navigationHistory.get(historyIndex);
            displayFiles(previousFile);
            selectDirectoryInTree(previousFile);
            updateButtonState();
        }
    }

    private void navigateForward() {
        if (historyIndex < navigationHistory.size() - 1) {
            historyIndex++;
            File nextFile = navigationHistory.get(historyIndex);
            displayFiles(nextFile);
            selectDirectoryInTree(nextFile);
            updateButtonState();
        }
    }

    private void updateButtonState() {
        backButton.setEnabled(historyIndex > 0);
        forwardButton.setEnabled(historyIndex < navigationHistory.size() - 1);
    }

    private void performSearch(String searchText) {
        File currentDir = (historyIndex >= 0 && historyIndex < navigationHistory.size())
                ? navigationHistory.get(historyIndex)
                : new File(System.getProperty("user.home"));

        String selectedType = (String) searchTypeCombo.getSelectedItem();
        boolean filterByType = selectedType != null && !"All".equals(selectedType);

        fileViewPanel.removeAll();
        lastClickedPanel = null;
        lastHoveredPanel = null;
        JLabel searchingLabel = new JLabel("Searching...");
        searchingLabel.setForeground(Color.LIGHT_GRAY);
        searchingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        fileViewPanel.setLayout(new BorderLayout());
        fileViewPanel.add(searchingLabel, BorderLayout.CENTER);
        fileViewPanel.revalidate();
        fileViewPanel.repaint();

        currentSearchWorker = new SwingWorker<>() {
            @Override
            protected List<File> doInBackground() {
                List<File> results = searchFiles(currentDir, searchText);
                if (filterByType) {
                    results = results.stream()
                            .filter(f -> f.getName().toLowerCase().endsWith(selectedType.toLowerCase()))
                            .toList();
                }
                return results;
            }

            @Override
            protected void done() {
                if (isCancelled())
                    return;
                try {
                    fileViewPanel.removeAll();
                    lastClickedPanel = null;
                    lastHoveredPanel = null;
                    List<File> searchResults = get();
                    if (searchResults.isEmpty()) {
                        JLabel noResultsLabel = new JLabel("No results found for \"" + searchText + "\".");
                        noResultsLabel.setForeground(Color.LIGHT_GRAY);
                        noResultsLabel.setHorizontalAlignment(SwingConstants.CENTER);
                        fileViewPanel.setLayout(new BorderLayout());
                        fileViewPanel.add(noResultsLabel, BorderLayout.CENTER);
                    } else {
                        if ("details".equals(currentViewMode)) {
                            displayFilteredDetailsView(searchResults);
                        } else {
                            fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
                            for (File file : searchResults) {
                                JPanel filePanel = createFilePanel(file);
                                fileViewPanel.add(filePanel);
                            }
                        }
                    }
                    fileViewPanel.revalidate();
                    fileViewPanel.repaint();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        currentSearchWorker.execute();
    }

    private List<File> searchFiles(File directory, String searchText) {
        return searchFiles(directory, searchText, 0, 5); // Limit to 5 levels deep
    }

    private List<File> searchFiles(File directory, String searchText, int depth, int maxDepth) {
        List<File> results = new ArrayList<>();
        if (depth > maxDepth)
            return results;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isHidden())
                    continue;

                if (file.getName().toLowerCase().contains(searchText.toLowerCase())) {
                    results.add(file);
                }

                if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
                    results.addAll(searchFiles(file, searchText, depth + 1, maxDepth));
                }
            }
        }
        return results;
    }

    private void selectDirectoryInTree(File directory) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        DefaultMutableTreeNode node = findNodeForFile(root, directory);
        if (node != null) {
            TreePath path = new TreePath(node.getPath());
            directoryTree.setSelectionPath(path);
            directoryTree.scrollPathToVisible(path);
        }
    }

    private void applyCurrentSortAndFilter() {
        if (currentFile != null) {
            displayFiles(currentFile);
        }
    }

    private File[] filterFilesByType(File[] files) {
        if (currentFileTypeFilter.isEmpty() || "All".equalsIgnoreCase(currentFileTypeFilter)) {
            return files;
        }
        return Arrays.stream(files)
                .filter(file -> !file.isDirectory()
                        && file.getName().toLowerCase().endsWith(currentFileTypeFilter.toLowerCase()))
                .toArray(File[]::new);
    }

    private void sortFiles(File[] files) {
        if ("name".equals(currentSortCriteria)) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        } else if ("dateModified".equals(currentSortCriteria)) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        } else if ("type".equals(currentSortCriteria)) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory())
                    return -1;
                if (!f1.isDirectory() && f2.isDirectory())
                    return 1;
                if (!f1.isDirectory() && !f2.isDirectory()) {
                    String ext1 = getFileExtension(f1);
                    String ext2 = getFileExtension(f2);
                    return ext1.compareToIgnoreCase(ext2);
                }
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
        }
    }

    private void filterFilesByExactType(String fileType) {
        if (currentFile == null || !currentFile.isDirectory())
            return;

        File[] files = fileSystemView.getFiles(currentFile, true);

        List<File> filteredFiles = Arrays.stream(files)
                .filter(file -> !file.isDirectory() && file.getName().toLowerCase().endsWith(fileType.toLowerCase()))
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            if ("details".equals(currentViewMode)) {
                displayFilteredDetailsView(filteredFiles);
            } else {
                displayFilteredLargeIconsView(filteredFiles);
            }
            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private void filterFilesByType(String fileType) {
        if (currentFile == null || !currentFile.isDirectory())
            return;

        File[] files = fileSystemView.getFiles(currentFile, true);

        List<File> filteredFiles = Arrays.stream(files)
                .filter(file -> file.isDirectory() || file.getName().toLowerCase().endsWith(fileType.toLowerCase()))
                .sorted((f1, f2) -> {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                })
                .toList();

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            if ("details".equals(currentViewMode)) {
                displayFilteredDetailsView(filteredFiles);
            } else {
                displayFilteredLargeIconsView(filteredFiles);
            }
            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private void displayFilteredDetailsView(List<File> filteredFiles) {
        String[] columnNames = { "Icon", "Name", "Date Modified", "Type", "Size" };
        Object[][] data = new Object[filteredFiles.size()][5];

        for (int i = 0; i < filteredFiles.size(); i++) {
            File file = filteredFiles.get(i);
            data[i][0] = fileSystemView.getSystemIcon(file); // ICON
            data[i][1] = file.getName();
            data[i][2] = new Date(file.lastModified());
            data[i][3] = file.isDirectory() ? "Folder" : getFileExtension(file);
            data[i][4] = file.isDirectory() ? "" : formatFileSize(file.length());
        }

        JTable detailsTable = new JTable(new javax.swing.table.DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });

        final int[] lastHoveredRow = { -1 };
        final int[] lastClickedRow = { -1 };

        detailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (row == lastClickedRow[0]) {
                    setBackground(CLICK_COLOR);
                    setForeground(Color.WHITE);
                } else if (row == lastHoveredRow[0]) {
                    setBackground(panelHoverColor);
                    setForeground(Color.BLACK);
                } else {
                    setBackground(folderBackgroundColor);
                    setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
                }

                if (column == 0) {
                    setHorizontalAlignment(CENTER);
                    setIcon(value instanceof Icon ? (Icon) value : null);
                    setText("");
                } else {
                    setHorizontalAlignment(LEFT);
                    setIcon(null);
                    setText(value == null ? "" : value.toString());
                }

                return this;
            }
        });
        detailsTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = detailsTable.rowAtPoint(e.getPoint());
                if (row != lastHoveredRow[0]) {
                    if (lastHoveredRow[0] != -1) {
                        Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true).union(
                                detailsTable.getCellRect(lastHoveredRow[0], detailsTable.getColumnCount() - 1, true));
                        detailsTable.repaint(r);
                    }
                    lastHoveredRow[0] = row;
                    if (row != -1) {
                        Rectangle r = detailsTable.getCellRect(row, 0, true).union(
                                detailsTable.getCellRect(row, detailsTable.getColumnCount() - 1, true));
                        detailsTable.repaint(r);
                    }
                }
            }
        });

        detailsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (lastHoveredRow[0] != -1) {
                    Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true).union(
                            detailsTable.getCellRect(lastHoveredRow[0], detailsTable.getColumnCount() - 1, true));
                    detailsTable.repaint(r);
                    lastHoveredRow[0] = -1;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = detailsTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    lastClickedRow[0] = row;
                    detailsTable.repaint();
                    File file = filteredFiles.get(row);
                    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        openFileOrDirectory(file);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        Point p = e.getLocationOnScreen();
                        createContextMenu(file, p.x, p.y);
                    }
                }
            }
        });
        detailsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        detailsTable.getColumnModel().getColumn(0).setMinWidth(36);
        detailsTable.getColumnModel().getColumn(0).setMaxWidth(48);

        detailsTable.setBackground(folderBackgroundColor);
        detailsTable.setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
        detailsTable.setGridColor(Color.DARK_GRAY);
        detailsTable.setRowHeight(32);

        JTableHeader header = detailsTable.getTableHeader();
        header.setBackground(topBackgroundColor);
        header.setForeground(Color.BLACK);
        header.setFont(new Font("SansSerif", Font.BOLD, 12));
        header.setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(detailsTable);
        scrollPane.getViewport().setBackground(folderBackgroundColor);

        fileViewPanel.setLayout(new BorderLayout());
        fileViewPanel.add(scrollPane, BorderLayout.CENTER);
    }

    private void displayFilteredLargeIconsView(List<File> filteredFiles) {
        fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10)); // LARGE ICONS
        for (File file : filteredFiles) {
            JPanel filePanel = createFilePanel(file);
            fileViewPanel.add(filePanel);
        }
    }

    private JPanel createFilePanel(File file) {
        JPanel filePanel = new JPanel();
        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        filePanel.setOpaque(true);
        filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
        filePanel.setPreferredSize(new Dimension(120, 110));
        filePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        filePanel.setBackground(folderBackgroundColor);

        Icon systemIcon = fileSystemView.getSystemIcon(file);
        iconLabel.setIcon(systemIcon);
        filePanel.add(iconLabel);

        // FILES ICON
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() {
                    try {
                        ImageIcon imageIcon = new ImageIcon(file.getAbsolutePath());
                        Image img = imageIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                        return new ImageIcon(img);
                    } catch (Exception ex) {
                        return null;
                    }
                }

                @Override
                protected void done() {
                    try {
                        ImageIcon thumbnail = get();
                        if (thumbnail != null) {
                            iconLabel.setIcon(thumbnail);
                        }
                    } catch (Exception ignored) {
                    }
                }
            };
            worker.execute();
        } else if (systemIcon instanceof ImageIcon) {

            ImageIcon icon = (ImageIcon) systemIcon;
            Image scaledImage = icon.getImage().getScaledInstance(27, 27, Image.SCALE_SMOOTH);
            iconLabel.setIcon(new ImageIcon(scaledImage));
        } else {
            iconLabel.setIcon(systemIcon);
        }
        filePanel.add(iconLabel);

        filePanel.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                return new FileTransferable(Collections.singletonList(file));
            }

            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDrop())
                    return false;
                // Only allow drop if this panel represents a directory
                return file.isDirectory() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support))
                    return false;
                try {
                    Transferable t = support.getTransferable();
                    java.util.List<File> droppedFiles = (java.util.List<File>) t
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File fileToMove : droppedFiles) {
                        if (!fileToMove.equals(file)) {
                            File newFile = new File(file, fileToMove.getName());
                            Files.move(fileToMove.toPath(), newFile.toPath());
                        }
                    }
                    displayFiles(file);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(Main.this, "Drag-and-drop move failed.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        });
        filePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                filePanel.getTransferHandler().exportAsDrag(filePanel, e, TransferHandler.MOVE);
            }
        });

        filePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (filePanel != lastClickedPanel) {
                    setHoverEffect(filePanel);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (filePanel != lastClickedPanel) {
                    clearHoverEffect(filePanel);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.getClickCount() == 1) {
                        if (lastClickedPanel == filePanel) {

                            clearSelectionEffect();
                            selectedFile = null;
                            currentFile = null;
                        } else {
                            clearSelectionEffect();
                            setSelectedEffect(filePanel);
                            selectedFile = file;
                            currentFile = file;
                        }
                    } else if (e.getClickCount() == 2) {
                        openFileOrDirectory(file);
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    clearSelectionEffect();
                    setSelectedEffect(filePanel);
                    selectedFile = file;
                    currentFile = file;
                    createContextMenu(file, e.getXOnScreen(), e.getYOnScreen());
                }
            }
        });

        JLabel nameLabel = new JLabel(file.getName());
        nameLabel.setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        filePanel.add(Box.createVerticalStrut(5));
        filePanel.add(nameLabel);

        return filePanel;
    }

    private void createTree(DefaultMutableTreeNode root) {
        List<File> specialFolders = getSpecialFolders();
        for (File folder : specialFolders) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(folder);
            if (folder.isDirectory() && hasSubFolders(folder)) {
                node.add(new DefaultMutableTreeNode(DUMMY_NODE));
            }
            root.add(node);
        }
        if (!specialFolders.isEmpty()) {
            displayFiles(specialFolders.get(0));
        }
    }

    private List<File> getSpecialFolders() {
        List<File> folders = new ArrayList<>();
        FileSystemView fsv = FileSystemView.getFileSystemView();

        // DESKTOP
        File desktop = fsv.getHomeDirectory();
        folders.add(desktop);

        // DOCUMENTS
        File documents = new File(System.getProperty("user.home"), "Documents");
        if (documents.exists())
            folders.add(documents);

        // DOWNLOADS
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.exists())
            folders.add(downloads);

        // MUSIC
        File music = new File(System.getProperty("user.home"), "Music");
        if (music.exists())
            folders.add(music);

        // PICTURES
        File pictures = new File(System.getProperty("user.home"), "Pictures");
        if (pictures.exists())
            folders.add(pictures);

        // VIDEOS
        File videos = new File(System.getProperty("user.home"), "Videos");
        if (videos.exists())
            folders.add(videos);

        // LIBRARIES
        File libraries = new File(System.getProperty("user.home"), "Libraries");
        if (libraries.exists())
            folders.add(libraries);

        File userHome = new File(System.getProperty("user.home"));
        folders.add(userHome);

        for (File root : File.listRoots()) {
            folders.add(root);
        }

        return folders;
    }

    private void displayFiles(File directory) {
        if (fileViewPanel == null || directory == null)
            return;

        showLoadingEffect();

        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            currentFile = directory;
            displayedDirectory = directory;

            lastClickedPanel = null;
            lastHoveredPanel = null;

            File[] files = fileSystemView.getFiles(directory, true);

            files = filterFilesByType(files);
            sortFiles(files);

            if ("details".equals(currentViewMode)) {
                displayDetailsView(files);
            } else {
                displayLargeIconsView(files);
            }

            updateStatusLabel(files.length, selectedCount);

            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private void setHoverEffect(JPanel panel) {
        if (panel != lastClickedPanel) {
            if (lastHoveredPanel != null && lastHoveredPanel != lastClickedPanel) {
                lastHoveredPanel.setBackground(folderBackgroundColor);
                lastHoveredPanel.repaint();
            }
            lastHoveredPanel = panel;
            panel.setBackground(panelHoverColor);
            panel.repaint();
        }
    }

    private void clearHoverEffect(JPanel panel) {
        if (panel == lastHoveredPanel && panel != lastClickedPanel) {
            panel.setBackground(folderBackgroundColor);
            panel.repaint();
            if (panel.getParent() != fileViewPanel) {
                lastHoveredPanel = null;
            }
        }
    }

    private void setSelectedEffect(JPanel panel) {
        if (lastClickedPanel != null) {
            lastClickedPanel.setBackground(folderBackgroundColor);
            lastClickedPanel.repaint();
        }
        lastClickedPanel = panel;
        panel.setBackground(CLICK_COLOR);
        panel.repaint();
        selectedCount = 1;
        updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
    }

    private void clearSelectionEffect() {
        if (lastClickedPanel != null) {
            lastClickedPanel.setBackground(folderBackgroundColor);
            lastClickedPanel.repaint();
            if (lastClickedPanel.getParent() != fileViewPanel) {
                lastClickedPanel = null;
            }
        }
        selectedCount = 0;
        updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
    }

    private void updateStatusLabel(int total, int selected) {
        if (selected > 0) {
            statusLabel.setText("Items: " + total + "    Selected: " + selected);
        } else {
            statusLabel.setText("Items: " + total);
        }
    }

    private int getDirectoryItemCount(File directory) {
        if (directory != null && directory.isDirectory()) {
            File[] files = fileSystemView.getFiles(directory, true);
            files = filterFilesByType(files);
            return files.length;
        }
        return 0;
    }

    private void openFileOrDirectory(File file) {
        try {
            if (file.isDirectory()) {
                displayFiles(file);
                updateHistory(file);
            } else {
                Desktop.getDesktop().open(file);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void displayDetailsView(File[] files) {
        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            fileViewPanel.setLayout(new BorderLayout());
            fileViewPanel.setBackground(folderBackgroundColor);

            String[] columnNames = { "Icon", "Name", "Date Modified", "Type", "Size" };
            Object[][] data = gatherFileDetailsWithIcons(files);

            JTable detailsTable = new JTable(new javax.swing.table.DefaultTableModel(data, columnNames) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            });

            final int[] lastHoveredRow = { -1 };
            final int[] lastClickedRow = { -1 };

            // MOUSE LISTENERRRRS
            detailsTable.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int row = detailsTable.rowAtPoint(e.getPoint());
                    if (row != lastHoveredRow[0]) {
                        // Repaint old hovered row
                        if (lastHoveredRow[0] != -1) {
                            Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true)
                                    .union(detailsTable.getCellRect(lastHoveredRow[0],
                                            detailsTable.getColumnCount() - 1, true));
                            detailsTable.repaint(r);
                        }
                        lastHoveredRow[0] = row;
                        if (row != -1) {
                            Rectangle r = detailsTable.getCellRect(row, 0, true)
                                    .union(detailsTable.getCellRect(row, detailsTable.getColumnCount() - 1, true));
                            detailsTable.repaint(r);
                        }
                    }
                }
            });

            detailsTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (lastHoveredRow[0] != -1) {
                        Rectangle r = detailsTable.getCellRect(lastHoveredRow[0], 0, true)
                                .union(detailsTable.getCellRect(lastHoveredRow[0], detailsTable.getColumnCount() - 1,
                                        true));
                        detailsTable.repaint(r);
                        lastHoveredRow[0] = -1;
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = detailsTable.rowAtPoint(e.getPoint());
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        if (row >= 0) {
                            if (e.getClickCount() == 1) {
                                if (row == lastClickedRow[0]) {
                                    lastClickedRow[0] = -1;
                                    selectedCount = 0;
                                    detailsTable.clearSelection();
                                    detailsTable.repaint();
                                    updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
                                } else {
                                    lastClickedRow[0] = row;
                                    selectedCount = 1;
                                    detailsTable.setRowSelectionInterval(row, row);
                                    detailsTable.repaint();
                                    updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
                                }
                            }

                            else if (e.getClickCount() == 2) {
                                File file = files[row];
                                openFileOrDirectory(file);
                            }
                        } else {
                            lastClickedRow[0] = -1;
                            selectedCount = 0;
                            detailsTable.clearSelection();
                            detailsTable.repaint();
                            updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
                        }
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        if (row >= 0) {
                            lastClickedRow[0] = row;
                            selectedCount = 1;
                            detailsTable.setRowSelectionInterval(row, row);
                            detailsTable.repaint();
                            updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
                            File file = files[row];
                            Point p = e.getLocationOnScreen();
                            createContextMenu(file, p.x, p.y);
                        } else {
                            lastClickedRow[0] = -1;
                            selectedCount = 0;
                            detailsTable.clearSelection();
                            detailsTable.repaint();
                            updateStatusLabel(getDirectoryItemCount(displayedDirectory), selectedCount);
                        }
                    }
                }
            });

            detailsTable.setDragEnabled(true);
            detailsTable.setDropMode(DropMode.ON);
            detailsTable.setTransferHandler(new TransferHandler() {
                @Override
                protected Transferable createTransferable(JComponent c) {
                    int[] selectedRows = detailsTable.getSelectedRows();
                    java.util.List<File> selectedFiles = new ArrayList<>();
                    for (int row : selectedRows) {
                        if (row >= 0 && row < files.length) {
                            selectedFiles.add(files[row]);
                        }
                    }
                    return new FileTransferable(selectedFiles);
                }

                @Override
                public int getSourceActions(JComponent c) {
                    return MOVE;
                }

                @Override
                public boolean canImport(TransferSupport support) {
                    if (!support.isDrop())
                        return false;
                    JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                    int row = dl.getRow();
                    if (row < 0 || row >= files.length)
                        return false;
                    File target = files[row];
                    return target.isDirectory();
                }

                @Override
                public boolean importData(TransferSupport support) {
                    if (!canImport(support))
                        return false;
                    try {
                        JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                        int row = dl.getRow();
                        File targetDir = files[row];
                        if (!targetDir.isDirectory())
                            return false;

                        Transferable t = support.getTransferable();
                        java.util.List<File> droppedFiles = (java.util.List<File>) t
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        for (File fileToMove : droppedFiles) {
                            if (!fileToMove.equals(targetDir)) {
                                File newFile = new File(targetDir, fileToMove.getName());
                                Files.move(fileToMove.toPath(), newFile.toPath());
                            }
                        }
                        displayFiles(targetDir);
                        return true;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(Main.this, "Drag-and-drop move failed.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                }
            });
            detailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                        boolean hasFocus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (row == lastClickedRow[0]) {
                        setBackground(CLICK_COLOR);
                        setForeground(Color.WHITE);
                    } else if (row == lastHoveredRow[0]) {
                        setBackground(panelHoverColor);
                        setForeground(Color.BLACK);
                    } else {
                        setBackground(folderBackgroundColor);
                        setForeground(isDarkTheme ? DARK_TEXT : LIGHT_TEXT);
                    }

                    if (column == 0) {
                        setHorizontalAlignment(CENTER);
                        setIcon(value instanceof Icon ? (Icon) value : null);
                        setText("");
                    } else {
                        setHorizontalAlignment(LEFT);
                        setIcon(null);
                        setText(value == null ? "" : value.toString());
                    }

                    return this;
                }
            });
            detailsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
            detailsTable.getColumnModel().getColumn(0).setMinWidth(36);
            detailsTable.getColumnModel().getColumn(0).setMaxWidth(48);

            detailsTable.setBackground(folderBackgroundColor);
            detailsTable.setForeground(Color.LIGHT_GRAY);
            detailsTable.setGridColor(Color.DARK_GRAY);
            detailsTable.setRowHeight(32);

            JTableHeader header = detailsTable.getTableHeader();
            header.setBackground(topBackgroundColor);
            header.setForeground(Color.BLACK);
            header.setFont(new Font("SansSerif", Font.BOLD, 12));
            header.setReorderingAllowed(false);

            JScrollPane scrollPane = new JScrollPane(detailsTable);
            scrollPane.getViewport().setBackground(folderBackgroundColor);

            fileViewPanel.add(scrollPane, BorderLayout.CENTER);

            fileViewPanel.revalidate();
            fileViewPanel.repaint();
        });
    }

    private Object[][] gatherFileDetailsWithIcons(File[] files) {
        Object[][] data = new Object[files.length][5];
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            data[i][0] = fileSystemView.getSystemIcon(file);
            data[i][1] = file.getName();
            data[i][2] = new Date(file.lastModified());
            data[i][3] = file.isDirectory() ? "Folder" : getFileExtension(file);
            data[i][4] = file.isDirectory() ? "" : formatFileSize(file.length());
        }
        return data;
    }

    private void displayLargeIconsView(File[] files) {
        SwingUtilities.invokeLater(() -> {
            fileViewPanel.removeAll();
            lastClickedPanel = null;
            lastHoveredPanel = null;
            fileViewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
            fileViewPanel.setBackground(folderBackgroundColor);

            for (File file : files) {
                JPanel filePanel = createFilePanel(file);
                fileViewPanel.add(filePanel);
            }

            fileViewPanel.revalidate();
            fileViewPanel.repaint();

            fileViewPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Component clicked = fileViewPanel.getComponentAt(e.getPoint());
                    if (clicked == fileViewPanel) {
                        clearSelectionEffect();
                        selectedFile = null;
                        currentFile = null;
                    }
                }
            });
        });
    }

    private void createContextMenu(File file, int x, int y) {
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openFileOrDirectory(file));
        contextMenu.add(openItem);

        // Show Edit option for any file (not just .txt)
        if (file.isFile()) {
            JMenuItem editItem = new JMenuItem("Edit");
            editItem.addActionListener(e -> editTextFile(file));
            contextMenu.add(editItem);
        }

        JMenuItem renameItem = new JMenuItem("Rename");
        renameItem.addActionListener(e -> renameFile(file));
        contextMenu.add(renameItem);

        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.addActionListener(e -> {
            clipboardFile = file;
            cutMode = true;
            List<File> files = Collections.singletonList(file);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new FileTransferable(files), null);
            JOptionPane.showMessageDialog(this,
                    (file.isDirectory() ? "Folder" : "File") + " cut to clipboard.",
                    "Cut",
                    JOptionPane.INFORMATION_MESSAGE);
            System.out.println("Cut: " + clipboardFile.getAbsolutePath());
        });
        contextMenu.add(cutItem);

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> {
            clipboardFile = file;
            cutMode = false;
            List<File> files = Collections.singletonList(file);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new FileTransferable(files), null);
            JOptionPane.showMessageDialog(this,
                    (file.isDirectory() ? "Folder" : "File") + " copied to clipboard.",
                    "Copy",
                    JOptionPane.INFORMATION_MESSAGE);
            System.out.println("Copied: " + clipboardFile.getAbsolutePath());
        });
        contextMenu.add(copyItem);

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> deleteFile(file));
        contextMenu.add(deleteItem);

        JMenuItem propertiesItem = new JMenuItem("Properties");
        propertiesItem.addActionListener(e -> showProperties(file));
        contextMenu.add(propertiesItem);

        contextMenu.show(this, x - getLocationOnScreen().x, y - getLocationOnScreen().y);
    }

    private void editTextFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            JTextArea textArea = new JTextArea(content, 20, 60);
            int result = JOptionPane.showConfirmDialog(this, new JScrollPane(textArea), "Edit " + file.getName(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                Files.writeString(file.toPath(), textArea.getText());
                JOptionPane.showMessageDialog(this, "File saved successfully.", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to edit file.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameFile(File file) {
        String oldName = file.getName();
        String extension = "";
        int dotIndex = oldName.lastIndexOf('.');
        boolean isDirectory = file.isDirectory();

        if (!isDirectory && dotIndex > 0 && dotIndex < oldName.length() - 1) {
            extension = oldName.substring(dotIndex);
        }

        String newName = JOptionPane.showInputDialog(Main.this, "Enter new name:", "Rename", JOptionPane.PLAIN_MESSAGE);

        if (newName != null && !newName.trim().isEmpty()) {
            String trimmedName = newName.trim();

            if (!isDirectory && !trimmedName.contains(".") && !extension.isEmpty()) {
                trimmedName += extension;
            }

            File newFile = new File(file.getParentFile(), trimmedName);
            if (fileOrFolderExists(file.getParentFile(), trimmedName)) {
                JOptionPane.showMessageDialog(Main.this, "The file or folder name already exists.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (file.renameTo(newFile)) {
                displayFiles(file.getParentFile());
            } else {
                JOptionPane.showMessageDialog(Main.this, "Couldn't rename " + file.getName(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteFile(File file) {
        int option = JOptionPane.showConfirmDialog(Main.this, "Are you sure you want to delete " + file.getName() + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            boolean deleted;
            if (file.isDirectory()) {
                deleted = deleteDirectoryRecursively(file);
            } else {
                deleted = file.delete();
            }
            if (deleted) {
                displayFiles(file.getParentFile());
                clearSelectionEffect();
                currentFile = null;
            } else {
                JOptionPane.showMessageDialog(Main.this, "Couldn't delete " + file.getName(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean deleteDirectoryRecursively(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File f : allContents) {
                if (f.isDirectory()) {
                    if (!deleteDirectoryRecursively(f))
                        return false;
                } else {
                    if (!f.delete())
                        return false;
                }
            }
        }
        return dir.delete();
    }

    private void showProperties(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            String size = formatFileSize(attrs.size());

            String properties = "Name: " + file.getName() + "\n" +
                    "Path: " + file.getAbsolutePath() + "\n" +
                    "Type: " + (file.isDirectory() ? "Directory" : "File") + "\n" +
                    "Size: " + size + "\n" +
                    "Created: " + new Date(attrs.creationTime().toMillis()) + "\n" +
                    "Last Modified: " + new Date(attrs.lastModifiedTime().toMillis()) + "\n" +
                    "Readable: " + file.canRead() + "\n" +
                    "Writable: " + file.canWrite() + "\n" +
                    "Hidden: " + file.isHidden();

            JOptionPane.showMessageDialog(Main.this, properties, "Properties of " + file.getName(),
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(Main.this, "Could not read file properties.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0)
            return "0 bytes";
        final String[] units = new String[] { "bytes", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::new);
    }

    static class FileTreeCellRenderer extends DefaultTreeCellRenderer {
        private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            Color fg = Main.CURRENT_DARK_THEME ? Main.DARK_TEXT : Main.LIGHT_TEXT;
            Color bg = tree.getBackground();

            setBackgroundNonSelectionColor(bg);
            setTextNonSelectionColor(fg);
            setTextSelectionColor(fg);
            setBackgroundSelectionColor(Main.CLICK_COLOR);

            Point mousePosition = tree.getMousePosition();
            if (mousePosition != null) {
                int hoverRow = tree.getRowForLocation(mousePosition.x, mousePosition.y);
                TreePath hoverPath = tree.getPathForRow(hoverRow);
                TreePath currentPath = tree.getPathForRow(row);
                if (hoverPath != null && hoverPath.equals(currentPath) && !sel) {
                    setBackgroundNonSelectionColor(Main.panelHoverColor);
                }
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();
            if (userObject instanceof File) {
                File file = (File) userObject;
                setText(fileSystemView.getSystemDisplayName(file));
                setIcon(fileSystemView.getSystemIcon(file));
            }

            TreeNode parent = node.getParent();
            if (parent != null && parent.toString().equals("My Computer")) {
                setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
            } else {
                setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
            }
            return this;
        }
    }

    private static class FileTransferable implements Transferable {
        private final List<File> fileList;

        public FileTransferable(List<File> fileList) {
            this.fileList = fileList;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return fileList;
        }
    }
}
