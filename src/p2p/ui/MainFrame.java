package p2p.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;

import p2p.AppInfo;
import p2p.model.AppConfig;
import p2p.model.PeerInfo;
import p2p.model.RemoteFile;
import p2p.model.TransferInfo;
import p2p.net.P2PService;
import p2p.net.ServiceListener;

public class MainFrame extends JFrame implements ServiceListener {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JTextField folderField = new JTextField(System.getProperty("user.home"), 32);
    private final JTextField secretField = new JTextField("cse471", 32);
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(AppInfo.DEFAULT_TCP_PORT, 1, 65535, 1));
    private final JTextField excludedField = new JTextField("", 32);
    private final JLabel statusLabel = new JLabel("Disconnected");

    private final PeerTableModel peerModel = new PeerTableModel();
    private final FileTableModel fileModel = new FileTableModel();
    private final TransferTableModel transferModel = new TransferTableModel();
    private final P2PService service = new P2PService();

    public MainFrame() {
        super(AppInfo.APP_NAME);
        service.addListener(this);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(820, 560);
        setLocationRelativeTo(null);
        setJMenuBar(createMenuBar());
        cards.add(createSetupPanel(), "setup");
        cards.add(createMainPanel(), "main");
        add(cards, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exitApplication();
            }
        });
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu files = new JMenu("Files");
        JMenuItem connect = new JMenuItem("Connect");
        JMenuItem disconnect = new JMenuItem("Disconnect");
        JMenuItem exit = new JMenuItem("Exit");
        connect.addActionListener(e -> connect());
        disconnect.addActionListener(e -> disconnect());
        exit.addActionListener(e -> exitApplication());
        files.add(connect);
        files.add(disconnect);
        files.addSeparator();
        files.add(exit);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                AppInfo.APP_NAME + "\nDeveloper: " + AppInfo.DEVELOPER + "\n" + AppInfo.COURSE,
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);

        bar.add(files);
        bar.add(help);
        return bar;
    }

    private JPanel createSetupPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Shared Folder Location"), c);
        c.gridx = 1;
        panel.add(folderField, c);
        JButton browse = new JButton("Browse");
        browse.addActionListener(e -> browseFolder());
        c.gridx = 2;
        panel.add(browse, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Shared Secret"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(secretField, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("TCP Port"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(portSpinner, c);
        c.gridwidth = 1;

        c.gridx = 0;
        c.gridy = 3;
        panel.add(new JLabel("Excluded Child Folders"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(excludedField, c);
        c.gridwidth = 1;

        JButton start = new JButton("Start");
        start.addActionListener(e -> connect());
        c.gridx = 1;
        c.gridy = 4;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        panel.add(start, c);
        return panel;
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTable peersTable = new JTable(peerModel);
        JTable filesTable = new JTable(fileModel);
        JTable transfersTable = new JTable(transferModel);
        JButton downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> downloadSelectedFile(filesTable));
        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && filesTable.getSelectedRow() >= 0) {
                    downloadSelectedFile(filesTable);
                }
            }
        });

        JPanel filesPanel = titledPanel("Files Found", new JScrollPane(filesTable));
        JPanel filesActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        filesActions.add(downloadButton);
        filesPanel.add(filesActions, BorderLayout.SOUTH);

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledPanel("Computers in Network", new JScrollPane(peersTable)),
                filesPanel);
        top.setResizeWeight(0.42);
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top,
                titledPanel("File Transfers", new JScrollPane(transfersTable)));
        main.setResizeWeight(0.55);
        panel.add(main, BorderLayout.CENTER);
        return panel;
    }

    private void downloadSelectedFile(JTable filesTable) {
        if (filesTable.getSelectedRow() < 0) {
            return;
        }
        int row = filesTable.convertRowIndexToModel(filesTable.getSelectedRow());
        RemoteFile file = fileModel.getFile(row);
        service.download(file);
    }

    private JPanel titledPanel(String title, JScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser(folderField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void connect() {
        try {
            AppConfig config = new AppConfig(Path.of(folderField.getText()), secretField.getText().trim(),
                    (Integer) portSpinner.getValue(), parseExcludedFolders());
            if (config.getSecret().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Shared secret cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            service.start(config);
            cardLayout.show(cards, "main");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnect() {
        service.stop();
        cardLayout.show(cards, "setup");
    }

    private Set<String> parseExcludedFolders() {
        Set<String> excluded = new HashSet<>();
        String text = excludedField.getText().trim();
        if (text.isEmpty()) {
            return excluded;
        }
        for (String item : text.split(",")) {
            String value = item.trim();
            if (!value.isEmpty()) {
                excluded.add(value);
            }
        }
        return excluded;
    }

    private void exitApplication() {
        if (service.isRunning()) {
            service.stop();
        }
        dispose();
        System.exit(0);
    }

    @Override
    public void peersChanged(List<PeerInfo> peers) {
        SwingUtilities.invokeLater(() -> peerModel.setPeers(peers));
    }

    @Override
    public void filesChanged(List<RemoteFile> files) {
        SwingUtilities.invokeLater(() -> fileModel.setFiles(files));
    }

    @Override
    public void transferUpdated(TransferInfo transfer) {
        SwingUtilities.invokeLater(() -> transferModel.upsert(transfer));
    }

    @Override
    public void statusChanged(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private static class PeerTableModel extends AbstractTableModel {
        private final String[] columns = { "Computer Hostname", "Computer IP", "Port" };
        private List<PeerInfo> peers = new ArrayList<>();

        void setPeers(List<PeerInfo> peers) {
            this.peers = peers;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return peers.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PeerInfo peer = peers.get(rowIndex);
            if (columnIndex == 0) {
                return peer.getHostName();
            }
            if (columnIndex == 1) {
                return peer.getAddress().getHostAddress();
            }
            return peer.getPort();
        }
    }

    private static class FileTableModel extends AbstractTableModel {
        private final String[] columns = { "File Name", "Size", "Sources", "Known Names" };
        private List<RemoteFile> files = new ArrayList<>();

        void setFiles(List<RemoteFile> files) {
            this.files = files;
            fireTableDataChanged();
        }

        RemoteFile getFile(int row) {
            return files.get(row);
        }

        @Override
        public int getRowCount() {
            return files.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RemoteFile file = files.get(rowIndex);
            if (columnIndex == 0) {
                return file.getPreferredName();
            }
            if (columnIndex == 1) {
                return file.getSize();
            }
            if (columnIndex == 2) {
                return file.getSourceCount();
            }
            return file.getKnownNames();
        }
    }

    private static class TransferTableModel extends AbstractTableModel {
        private final String[] columns = { "File Name", "Bytes", "Percentage", "Status" };
        private final List<TransferInfo> transfers = new ArrayList<>();

        void upsert(TransferInfo transfer) {
            int index = transfers.indexOf(transfer);
            if (index < 0) {
                transfers.add(transfer);
                fireTableRowsInserted(transfers.size() - 1, transfers.size() - 1);
            } else {
                fireTableRowsUpdated(index, index);
            }
        }

        @Override
        public int getRowCount() {
            return transfers.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TransferInfo transfer = transfers.get(rowIndex);
            if (columnIndex == 0) {
                return transfer.getFileName();
            }
            if (columnIndex == 1) {
                return transfer.getTransferredBytes() + " / " + transfer.getTotalBytes();
            }
            if (columnIndex == 2) {
                return transfer.getPercentage() + "%";
            }
            return transfer.getStatus();
        }
    }
}
