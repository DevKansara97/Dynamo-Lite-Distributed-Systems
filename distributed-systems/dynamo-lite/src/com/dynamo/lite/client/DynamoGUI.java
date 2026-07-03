package com.dynamo.lite.client;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.Timer;

/**
 * Dynamo-lite GUI Dashboard
 *
 * A self-contained Swing desktop UI for the Dynamo-lite cluster.
 * No web server, no browser, no extra dependencies.
 * Compile and run exactly like any other Java class.
 *
 * Features:
 *  - Live cluster health panel (polls STATUS every 2s)
 *  - Command console (PUT / GET / DELETE)
 *  - Response log with timestamps
 *  - Start/stop nodes via shell scripts (optional)
 */
public class DynamoGUI extends JFrame {

    // ── Colour palette (dark theme) ────────────────────────────────
    private static final Color BG_DARK    = new Color(18,  18,  24);
    private static final Color BG_PANEL   = new Color(26,  26,  36);
    private static final Color BG_INPUT   = new Color(32,  32,  44);
    private static final Color BORDER_COL = new Color(55,  55,  75);
    private static final Color TEXT_PRI   = new Color(220, 220, 230);
    private static final Color TEXT_SEC   = new Color(140, 140, 160);
    private static final Color ACCENT     = new Color(99,  102, 241); // indigo
    private static final Color GREEN      = new Color(52,  211, 153);
    private static final Color RED        = new Color(248, 113, 113);
    private static final Color YELLOW     = new Color(251, 191,  36);
    private static final Color MONO_FONT_COL = new Color(180, 220, 180);

    // ── Fonts ──────────────────────────────────────────────────────
    private static final Font FONT_MONO  = new Font("JetBrains Mono", Font.PLAIN, 13);
    private static final Font FONT_MONO2 = new Font("Monospaced",     Font.PLAIN, 13);
    private static final Font FONT_LABEL = new Font("SansSerif",      Font.BOLD,  12);
    private static final Font FONT_TITLE = new Font("SansSerif",      Font.BOLD,  14);
    private static final Font FONT_BODY  = new Font("SansSerif",      Font.PLAIN, 13);

    // ── Node configuration ─────────────────────────────────────────
    private static final List<NodeDef> NODES = List.of(
        new NodeDef("NodeA", "localhost", 7001),
        new NodeDef("NodeB", "localhost", 7002),
        new NodeDef("NodeC", "localhost", 7003)
    );

    // ── State ──────────────────────────────────────────────────────
    private final DefaultTableModel clusterModel;
    private final JTextArea         logArea;
    private final JTextField        keyField;
    private final JTextField        valueField;
    private final JComboBox<String> nodeSelector;
    private final JLabel            statusBar;

    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gui-poller");
                t.setDaemon(true);
                return t;
            });

    // ══════════════════════════════════════════════════════════════
    public DynamoGUI() {
        super("Dynamo-lite  ·  Distributed KV Store");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1100, 720));
        setMinimumSize(new Dimension(900, 600));
        getContentPane().setBackground(BG_DARK);

        // ── Cluster table model ─────────────────────────────────
        String[] cols = {"Node", "Host", "Port", "Status", "Keys"};
        clusterModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (NodeDef n : NODES) {
            clusterModel.addRow(new Object[]{
                n.id, n.host, String.valueOf(n.port), "—", "—"
            });
        }

        // ── Log area ────────────────────────────────────────────
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(BG_INPUT);
        logArea.setForeground(MONO_FONT_COL);
        logArea.setFont(monoFont());
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        logArea.setCaretColor(TEXT_PRI);

        // ── Input fields ─────────────────────────────────────────
        keyField   = styledField("key");
        valueField = styledField("value  (required for PUT)");

        nodeSelector = new JComboBox<>();
        for (NodeDef n : NODES) nodeSelector.addItem(n.id);
        styleCombo(nodeSelector);

        // ── Status bar ───────────────────────────────────────────
        statusBar = new JLabel("  Polling cluster…");
        statusBar.setFont(FONT_BODY);
        statusBar.setForeground(TEXT_SEC);
        statusBar.setOpaque(true);
        statusBar.setBackground(BG_PANEL);
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        // ── Layout ───────────────────────────────────────────────
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(statusBar,      BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        startPolling();
        log("Dashboard started. Polling " + NODES.size() + " nodes every 2s.");
    }

    // ══════════════════════════════════════════════════════════════
    //  Layout builders
    // ══════════════════════════════════════════════════════════════

    private JPanel buildHeader() {
        JPanel p = darkPanel(new BorderLayout());
        p.setBackground(BG_PANEL);
        p.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COL),
            new EmptyBorder(14, 20, 14, 20)
        ));

        JLabel title = new JLabel("DYNAMO-LITE");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(ACCENT);

        JLabel sub = new JLabel(
            "  ·  Distributed Key-Value Store  "
          + "·  N=3  W=2  R=2  "
          + "·  Consistent Hashing  "
          + "·  Quorum Replication");
        sub.setFont(FONT_BODY);
        sub.setForeground(TEXT_SEC);

        JPanel left = darkPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(title);
        left.add(sub);
        p.add(left, BorderLayout.WEST);

        JLabel time = new JLabel();
        time.setFont(FONT_BODY);
        time.setForeground(TEXT_SEC);
        p.add(time, BorderLayout.EAST);

        // Timer clock = new Timer(1000, e ->
        //     time.setText(LocalTime.now()
        //         .format(DateTimeFormatter.ofPattern("HH:mm:ss  "))));
        // clock.start();

        Timer clock = new Timer(1000, e ->
    time.setText(LocalTime.now()
        .format(DateTimeFormatter.ofPattern("HH:mm:ss  "))));
clock.start();

        return p;
    }

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(),
            buildRightPanel()
        );
        split.setDividerLocation(420);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(BG_DARK);
        return split;
    }

    private JPanel buildLeftPanel() {
        JPanel p = darkPanel(new BorderLayout(0, 0));
        p.setBorder(new EmptyBorder(16, 16, 16, 8));

        // ── Cluster health table ──────────────────────────────
        JTable table = new JTable(clusterModel) {
            @Override
            public Component prepareRenderer(
                    TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_PANEL : BG_INPUT);
                c.setForeground(TEXT_PRI);
                if (col == 3) {
                    String v = (String) getValueAt(row, col);
                    if ("ALIVE".equals(v))     c.setForeground(GREEN);
                    else if ("DOWN".equals(v)
                          || "UNREACHABLE".equals(v)) c.setForeground(RED);
                    else if ("SUSPECTED".equals(v))   c.setForeground(YELLOW);
                }
                ((JComponent) c).setBorder(
                    new EmptyBorder(6, 10, 6, 10));
                return c;
            }
        };
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRI);
        table.setFont(FONT_BODY);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setFocusable(false);
        table.setSelectionBackground(BORDER_COL);
        table.getTableHeader().setBackground(BG_DARK);
        table.getTableHeader().setForeground(TEXT_SEC);
        table.getTableHeader().setFont(FONT_LABEL);
        table.getTableHeader().setBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COL));
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(50);

        JScrollPane tableScroll = new JScrollPane(table);
        styleScrollPane(tableScroll);
        tableScroll.setPreferredSize(new Dimension(380, 140));

        // ── Command panel ─────────────────────────────────────
        JPanel cmdPanel = darkPanel(new GridBagLayout());
        cmdPanel.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_COL),
            new EmptyBorder(16, 0, 0, 0)
        ));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 4, 5, 4);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        // Row 0 — section label
        gc.gridx=0; gc.gridy=0; gc.gridwidth=3;
        cmdPanel.add(sectionLabel("COMMAND CONSOLE"), gc);

        // Row 1 — node selector label + combo
        gc.gridwidth=1;
        gc.gridx=0; gc.gridy=1; gc.weightx=0;
        cmdPanel.add(fieldLabel("Target Node"), gc);
        gc.gridx=1; gc.gridy=1; gc.gridwidth=2; gc.weightx=1;
        cmdPanel.add(nodeSelector, gc);

        // Row 2 — key
        gc.gridwidth=1; gc.weightx=0;
        gc.gridx=0; gc.gridy=2;
        cmdPanel.add(fieldLabel("Key"), gc);
        gc.gridx=1; gc.gridy=2; gc.gridwidth=2; gc.weightx=1;
        cmdPanel.add(keyField, gc);

        // Row 3 — value
        gc.gridwidth=1; gc.weightx=0;
        gc.gridx=0; gc.gridy=3;
        cmdPanel.add(fieldLabel("Value"), gc);
        gc.gridx=1; gc.gridy=3; gc.gridwidth=2; gc.weightx=1;
        cmdPanel.add(valueField, gc);

        // Row 4 — buttons
        gc.gridx=0; gc.gridy=4; gc.gridwidth=1; gc.weightx=1;
        cmdPanel.add(actionButton("PUT",    new Color(52, 211, 153), e -> sendCommand("PUT")),    gc);
        gc.gridx=1;
        cmdPanel.add(actionButton("GET",    ACCENT,                  e -> sendCommand("GET")),    gc);
        gc.gridx=2;
        cmdPanel.add(actionButton("DELETE", new Color(248, 113, 113), e -> sendCommand("DELETE")), gc);

        // Row 5 — utility buttons
        gc.gridx=0; gc.gridy=5; gc.gridwidth=1;
        cmdPanel.add(utilButton("STATUS", e -> sendRaw("STATUS")), gc);
        gc.gridx=1;
        cmdPanel.add(utilButton("PING",   e -> sendRaw("PING")),   gc);
        gc.gridx=2;
        cmdPanel.add(utilButton("Clear Log", e -> logArea.setText("")), gc);

        p.add(sectionLabel("CLUSTER HEALTH"), BorderLayout.NORTH);
        p.add(tableScroll,  BorderLayout.CENTER);

        JPanel bottom = darkPanel(new BorderLayout());
        bottom.add(cmdPanel, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);

        return p;
    }

    private JPanel buildRightPanel() {
        JPanel p = darkPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(16, 8, 16, 16));

        JPanel header = darkPanel(new BorderLayout());
        header.add(sectionLabel("RESPONSE LOG"), BorderLayout.WEST);

        JScrollPane logScroll = new JScrollPane(logArea);
        styleScrollPane(logScroll);

        p.add(header,    BorderLayout.NORTH);
        p.add(logScroll, BorderLayout.CENTER);
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  Command execution
    // ══════════════════════════════════════════════════════════════

    private void sendCommand(String cmd) {
        String key   = keyField.getText().trim();
        String value = valueField.getText().trim();

        if (key.isEmpty()) {
            log("[ERROR] Key is required.");
            return;
        }
        if ("PUT".equals(cmd) && value.isEmpty()) {
            log("[ERROR] Value is required for PUT.");
            return;
        }

        String line = switch (cmd) {
            case "PUT"    -> "PUT "    + key + " " + value;
            case "GET"    -> "GET "    + key;
            case "DELETE" -> "DELETE " + key;
            default       -> cmd;
        };

        executeAsync(line);
    }

    private void sendRaw(String cmd) {
        executeAsync(cmd);
    }

    private void executeAsync(String command) {
        NodeDef node = NODES.get(nodeSelector.getSelectedIndex());
        log("→ [" + node.id + "] " + command);

        CompletableFuture.supplyAsync(() -> {
            try (Socket s = new Socket(node.host, node.port)) {
                s.setSoTimeout(3000);
                PrintWriter  out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream()));
                out.println(command);
                return in.readLine();
            } catch (IOException e) {
                return "ERROR " + e.getMessage();
            }
        }).thenAccept(response ->
            SwingUtilities.invokeLater(() ->
                log("← " + response)));
    }

    // ══════════════════════════════════════════════════════════════
    //  Cluster polling
    // ══════════════════════════════════════════════════════════════

    private void startPolling() {
        poller.scheduleAtFixedRate(this::pollCluster, 0, 2, TimeUnit.SECONDS);
    }

    private void pollCluster() {
        int alive = 0;
        for (int i = 0; i < NODES.size(); i++) {
            NodeDef node = NODES.get(i);
            String[] result = queryNode(node);
            final int row  = i;
            final String[] r = result;
            if ("ALIVE".equals(r[0])) alive++;
            SwingUtilities.invokeLater(() -> {
                clusterModel.setValueAt(r[0], row, 3);
                clusterModel.setValueAt(r[1], row, 4);
            });
        }
        final int a = alive;
        SwingUtilities.invokeLater(() ->
            statusBar.setText("  " +
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "  ·  Cluster: "
                + a + "/" + NODES.size() + " nodes alive"
                + "  ·  Auto-refresh every 2s"));
    }

    /** Returns [status, keyCount] */
    private String[] queryNode(NodeDef node) {
        try (Socket s = new Socket(node.host, node.port)) {
            s.setSoTimeout(1500);
            PrintWriter    out = new PrintWriter(s.getOutputStream(), true);
            BufferedReader in  = new BufferedReader(
                new InputStreamReader(s.getInputStream()));
            out.println("STATUS");
            String resp = in.readLine();
            if (resp != null && resp.startsWith("STATUS ")) {
                String json = resp.substring(7);
                int idx = json.indexOf("\"keys\":");
                String keys = idx >= 0
                    ? json.substring(idx + 7).replace("}", "").trim()
                    : "?";
                return new String[]{"ALIVE", keys};
            }
            return new String[]{"SUSPECTED", "?"};
        } catch (IOException e) {
            return new String[]{"UNREACHABLE", "—"};
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Logging
    // ══════════════════════════════════════════════════════════════

    private void log(String msg) {
        String ts = LocalTime.now()
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + ts + "]  " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Widget helpers
    // ══════════════════════════════════════════════════════════════

    private JPanel darkPanel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(BG_DARK);
        p.setOpaque(true);
        return p;
    }

    private JTextField styledField(String placeholder) {
        JTextField f = new JTextField(20) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    g.setColor(TEXT_SEC);
                    g.setFont(FONT_BODY);
                    g.drawString(placeholder, 10, 18);
                }
            }
        };
        f.setBackground(BG_INPUT);
        f.setForeground(TEXT_PRI);
        f.setCaretColor(TEXT_PRI);
        f.setFont(FONT_BODY);
        f.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, BORDER_COL),
            new EmptyBorder(6, 10, 6, 10)));
        return f;
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setBackground(BG_INPUT);
        cb.setForeground(TEXT_PRI);
        cb.setFont(FONT_BODY);
        cb.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_COL));
    }

    private void styleScrollPane(JScrollPane sp) {
        sp.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_COL));
        sp.getViewport().setBackground(BG_INPUT);
        sp.setBackground(BG_PANEL);
        sp.getVerticalScrollBar().setBackground(BG_PANEL);
        sp.getHorizontalScrollBar().setBackground(BG_PANEL);
    }

    private JButton actionButton(String text, Color color, ActionListener al) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()
                    ? color.darker() : getModel().isRollover()
                    ? color.brighter() : color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(0, 0, 0, 60));
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(text)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, tx, ty);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(90, 36));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(al);
        return b;
    }

    private JButton utilButton(String text, ActionListener al) {
        JButton b = new JButton(text);
        b.setBackground(BG_INPUT);
        b.setForeground(TEXT_SEC);
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, BORDER_COL),
            new EmptyBorder(5, 10, 5, 10)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(al);
        return b;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(TEXT_SEC);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_LABEL);
        l.setForeground(TEXT_SEC);
        return l;
    }

    private Font monoFont() {
        try {
            new Font("JetBrains Mono", Font.PLAIN, 13)
                .createGlyphVector(
                    new java.awt.font.FontRenderContext(null, true, true),
                    "X");
            return FONT_MONO;
        } catch (Exception e) {
            return FONT_MONO2;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Node definition
    // ══════════════════════════════════════════════════════════════

    record NodeDef(String id, String host, int port) {}

    // ══════════════════════════════════════════════════════════════
    //  Entry point
    // ══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        // Use system look and feel as base, then override
        try {
            UIManager.setLookAndFeel(
                UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Global UI defaults
        UIManager.put("Panel.background",         BG_DARK);
        UIManager.put("ScrollPane.background",    BG_PANEL);
        UIManager.put("Table.background",         BG_PANEL);
        UIManager.put("TableHeader.background",   BG_DARK);
        UIManager.put("SplitPane.background",     BG_DARK);
        UIManager.put("SplitPaneDivider.background", BORDER_COL);
        UIManager.put("ComboBox.background",      BG_INPUT);
        UIManager.put("ComboBox.foreground",      TEXT_PRI);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("TextField.background",     BG_INPUT);
        UIManager.put("TextField.foreground",     TEXT_PRI);
        UIManager.put("Button.background",        BG_INPUT);
        UIManager.put("Button.foreground",        TEXT_PRI);

        SwingUtilities.invokeLater(DynamoGUI::new);
    }
}