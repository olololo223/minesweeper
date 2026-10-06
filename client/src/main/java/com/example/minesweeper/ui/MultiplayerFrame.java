package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.GameMessage;
import com.example.minesweeper.protocol.MessageType;
import com.example.minesweeper.protocol.payload.GetFriendsResponse;
import com.example.minesweeper.protocol.payload.RoomFinishedPush;
import com.example.minesweeper.protocol.payload.RoomUpdatePush;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Игровое окно мультиплеера: одна комната — одно окно.
 *
 * Клиент не хранит «истину»: состояние приходит только в ROOM_UPDATE_PUSH
 * (полный snapshot при входе, дальше — дельты). Клики отправляют DIG/FLAG
 * и ничего не ждут: ответом будет очередной push.
 *
 * Правила отрисовки и допустимости клика вынесены в статические методы —
 * их можно проверить без окна (см. MultiplayerProbe).
 */
public class MultiplayerFrame extends JFrame {

    private static final int CELL_SIZE = 32;

    // Цвета те же, что в одиночной игре
    static final Color CLOSED_BG = new Color(0xBDBDBD);
    static final Color OPENED_BG = new Color(0xE8E8E8);
    static final Color FLAG_BG = new Color(0xFFE082);
    static final Color MINE_BG = new Color(0xBDC3C7);
    static final Color EXPLODED_BG = new Color(0xE74C3C);

    private final GameClient client;
    private final long roomId;

    private final JPanel boardPanel = new JPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton startButton = new JButton("Начать игру");
    private final JButton leaveButton = new JButton("Покинуть комнату");
    private final JButton inviteButton = new JButton("Пригласить друга");

    private final DefaultTableModel playersModel = new DefaultTableModel(
            new Object[]{"Игрок", "Статус", "Флаги"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    // Локальные «зеркала» состояния от сервера
    private int rows, cols;
    private CellView[][] cells;
    private String roomState = "WAITING";
    private boolean finished = false;
    private long ownerUserId = -1;

    /** Наш слушатель — храним ссылку, чтобы снять его при закрытии. */
    private final GameClient.MessageListener listener;

    /** Локальное представление клетки. */
    private static class CellView {
        JButton button;
        boolean revealed;
        boolean flagged;
        boolean mine;
        boolean exploded;
        int neighborMines = -1;
    }

    public MultiplayerFrame(GameClient client, long roomId) {
        super("Сапёр — Комната " + roomId);
        this.client = client;
        this.roomId = roomId;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // ---- Верх: статус ----
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(statusLabel, BorderLayout.NORTH);

        // ---- Центр: игровое поле (размер станет известен из первого push) ----
        boardPanel.setLayout(new GridLayout(1, 1));
        JScrollPane boardScroll = new JScrollPane(boardPanel);
        boardScroll.setPreferredSize(new Dimension(680, 500));
        add(boardScroll, BorderLayout.CENTER);

        // ---- Восток: игроки + кнопки ----
        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setPreferredSize(new Dimension(220, 0));

        JTable playersTable = new JTable(playersModel);
        playersTable.setRowHeight(24);
        playersTable.getTableHeader().setReorderingAllowed(false);
        right.add(new JScrollPane(playersTable), BorderLayout.CENTER);

        // BoxLayout, а не GridLayout: кнопка «Начать» прячется у не-владельца,
        // и в GridLayout на её месте оставалась бы дырка
        JPanel buttons = new JPanel(new GridLayout(3, 1, 6, 6));
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        leaveButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        inviteButton.addActionListener(e -> openInviteDialog());
        startButton.addActionListener(e -> doStart());
        leaveButton.addActionListener(e -> doLeave());
        buttons.add(inviteButton);
        buttons.add(startButton);
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(leaveButton);
        right.add(buttons, BorderLayout.SOUTH);

        add(right, BorderLayout.EAST);

        // ---- Закрытие окна ----
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                doLeave();
            }
        });

        // ---- Слушатель push'ов: добавляемся, не вытесняя слушателя главного окна ----
        listener = new GameClient.MessageListener() {
            @Override
            public void onMessage(GameMessage msg) {
                switch (msg.getType()) {
                    case ROOM_UPDATE_PUSH -> handleUpdate((RoomUpdatePush) msg.getPayload());
                    case ROOM_FINISHED_PUSH -> handleFinished((RoomFinishedPush) msg.getPayload());
                    default -> { /* прочее не наше */ }
                }
            }

            @Override
            public void onDisconnected() {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MultiplayerFrame.this,
                            "Соединение с сервером потеряно.",
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                    dispose();
                });
            }
        };
        client.addListener(listener);

        setSize(960, 620);
        setLocationRelativeTo(null);
    }

    @Override
    public void dispose() {
        // Иначе слушатель останется висеть на клиенте после закрытия окна
        client.removeListener(listener);
        super.dispose();
    }

    // ============ ПРИЁМ PUSH-ОБНОВЛЕНИЙ ============

    private void handleUpdate(RoomUpdatePush push) {
        SwingUtilities.invokeLater(() -> {
            // Первый snapshot задаёт размер поля; дальше приходят только дельты
            if (cells == null || push.rows != rows || push.cols != cols) {
                this.rows = push.rows;
                this.cols = push.cols;
                buildBoard(push.rows, push.cols);
            }

            this.roomState = push.state;

            if (push.changedCells != null) {
                for (RoomUpdatePush.CellState cs : push.changedCells) {
                    if (cs.row < 0 || cs.row >= rows || cs.col < 0 || cs.col >= cols) continue;
                    CellView v = cells[cs.row][cs.col];
                    v.revealed = cs.revealed;
                    v.flagged = cs.flagged;
                    v.mine = cs.mine;
                    v.exploded = cs.exploded;
                    v.neighborMines = cs.neighborMines;
                    renderCell(cs.row, cs.col);
                }
            }

            playersModel.setRowCount(0);
            ownerUserId = -1;
            if (push.players != null) {
                for (var p : push.players) {
                    if (p.owner) ownerUserId = p.userId;
                    playersModel.addRow(new Object[]{
                            p.username, playerStatus(p.alive, p.owner), p.flagsPlaced});
                }
            }

            statusLabel.setText("Комната " + push.roomId
                    + " — " + localizedState(push.state)
                    + " — мин: " + push.totalMines);

            updateControls();
        });
    }

    private void handleFinished(RoomFinishedPush push) {
        SwingUtilities.invokeLater(() -> {
            finished = true;
            updateControls();
            JOptionPane.showMessageDialog(this,
                    push.won ? "Победа! " + push.message : "Игра окончена: " + push.message,
                    push.won ? "Победа" : "Конец игры",
                    push.won ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
        });
    }

    // ============ ПОСТРОЕНИЕ ПОЛЯ ============

    private void buildBoard(int rows, int cols) {
        boardPanel.removeAll();
        boardPanel.setLayout(new GridLayout(rows, cols));
        cells = new CellView[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CellView v = new CellView();
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setFont(new Font("SansSerif", Font.BOLD, 14));
                btn.setFocusPainted(false);
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBorderPainted(true);

                final int rr = r, cc = c;
                btn.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            onFlag(rr, cc);
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            onDig(rr, cc);
                        }
                    }
                });

                v.button = btn;
                cells[r][c] = v;
                boardPanel.add(btn);
                renderCell(r, c);
            }
        }
        boardPanel.revalidate();
        boardPanel.repaint();
    }

    private void renderCell(int r, int c) {
        CellView v = cells[r][c];
        JButton b = v.button;

        b.setText(cellText(v.revealed, v.flagged, v.mine, v.neighborMines));
        b.setBackground(cellColor(v.revealed, v.flagged, v.mine, v.exploded, v.neighborMines));
        b.setForeground(textColor(v.revealed, v.neighborMines));
        b.setBorderPainted(!v.revealed);
        if (!v.revealed) b.setBorder(BorderFactory.createRaisedBevelBorder());
    }

    // ============ ДЕЙСТВИЯ ============

    private void onDig(int r, int c) {
        if (cells == null || r >= rows || c >= cols) return;
        CellView v = cells[r][c];
        if (!canDig(roomState, finished, v.revealed, v.flagged)) return;
        try {
            client.dig(r, c);
        } catch (RuntimeException e) {
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    private void onFlag(int r, int c) {
        if (cells == null || r >= rows || c >= cols) return;
        CellView v = cells[r][c];
        if (!canFlag(roomState, finished, v.revealed)) return;
        try {
            client.flag(r, c);
        } catch (RuntimeException e) {
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    private void doStart() {
        if (!startButton.isEnabled()) return;         // защита от двойного клика
        startButton.setEnabled(false);                // блокируем на время запроса

        try {
            client.startRoom().whenComplete((resp, err) ->
                    SwingUtilities.invokeLater(() -> {
                        // Разблокировка — всегда, вне зависимости от результата.
                        // updateControls() сам решит, должна ли кнопка быть активной:
                        // если комната уже PLAYING — она скроется/выключится,
                        // если отказ — снова станет доступной.
                        updateControls();

                        if (err != null) {
                            JOptionPane.showMessageDialog(this,
                                    "Ошибка: " + err.getMessage(),
                                    "Старт", JOptionPane.ERROR_MESSAGE);
                        } else if (!resp.success) {
                            JOptionPane.showMessageDialog(this, resp.message,
                                    "Старт", JOptionPane.WARNING_MESSAGE);
                        }
                        // При успехе состояние придёт через ROOM_UPDATE_PUSH,
                        // и updateControls() скроет кнопку для не-владельца
                        // или выключит её, когда state сменится на PLAYING.
                    }));
        } catch (RuntimeException e) {
            // Соединения нет — вернём кнопку, чтобы пользователь мог попробовать позже
            updateControls();
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    /**
     * Выход из комнаты. Запрашиваем подтверждение только в игре:
     * после финиша комната уже не нужна, но LEAVE всё равно отправляем —
     * иначе игрок останется в ней на сервере и не сможет войти в другую.
     */
    private void doLeave() {
        if (!finished) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Покинуть комнату?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
        }

        try {
            client.leaveRoom().whenComplete((resp, err) ->
                    SwingUtilities.invokeLater(this::dispose));
        } catch (RuntimeException e) {
            dispose();          // соединения нет — просто закрываем окно
        }
    }

    private void updateControls() {
        boolean owner = isOwner();
        startButton.setVisible(owner);
        startButton.setEnabled(canStart(roomState, finished, owner));
        leaveButton.setEnabled(true);
    }

    /** Мы владелец, если наш id совпал с тем, кого сервер пометил owner. */
    private boolean isOwner() {
        long me = client.getMyUserId();
        return me >= 0 && ownerUserId == me;
    }

    // ============ ЧИСТЫЕ ПРАВИЛА (проверяются без окна) ============

    /** Можно ли открыть клетку. */
    static boolean canDig(String roomState, boolean finished,
                          boolean revealed, boolean flagged) {
        return !finished && "PLAYING".equals(roomState) && !revealed && !flagged;
    }

    /** Можно ли поставить/снять флаг. */
    static boolean canFlag(String roomState, boolean finished, boolean revealed) {
        return !finished && "PLAYING".equals(roomState) && !revealed;
    }

    /** Доступна ли кнопка «Начать игру». */
    static boolean canStart(String roomState, boolean finished, boolean isOwner) {
        return !finished && isOwner && "WAITING".equals(roomState);
    }

    /** Текст в клетке. */
    static String cellText(boolean revealed, boolean flagged, boolean mine, int neighborMines) {
        if (revealed) {
            if (mine) return "💣";
            return neighborMines > 0 ? String.valueOf(neighborMines) : "";
        }
        return flagged ? "🚩" : "";
    }

    /** Фон клетки: открытая — светлая, взорванная мина — красная, флаг — жёлтый. */
    static Color cellColor(boolean revealed, boolean flagged, boolean mine,
                           boolean exploded, int neighborMines) {
        if (revealed) {
            if (mine) return exploded ? EXPLODED_BG : MINE_BG;
            return OPENED_BG;
        }
        return flagged ? FLAG_BG : CLOSED_BG;
    }

    /** Цвет цифры. */
    static Color textColor(boolean revealed, int neighborMines) {
        return revealed && neighborMines > 0 ? colorForNumber(neighborMines) : Color.BLACK;
    }

    static String playerStatus(boolean alive, boolean owner) {
        String status = alive ? "В игре" : "Взорвался";
        return owner ? status + " (хозяин)" : status;
    }

    static Color colorForNumber(int n) {
        return switch (n) {
            case 1 -> Color.BLUE;
            case 2 -> new Color(0, 128, 0);
            case 3 -> Color.RED;
            case 4 -> new Color(0, 0, 139);
            case 5 -> new Color(139, 0, 0);
            case 6 -> new Color(0, 139, 139);
            case 7 -> Color.BLACK;
            case 8 -> Color.GRAY;
            default -> Color.BLACK;
        };
    }

    static String localizedState(String s) {
        if (s == null) return "—";
        return switch (s) {
            case "WAITING" -> "ожидание";
            case "PLAYING" -> "игра идёт";
            case "FINISHED" -> "завершена";
            default -> s;
        };
    }

    /** Для тестов: сколько клеток уже открыто в локальной модели. */
    int revealedCount() {
        if (cells == null) return 0;
        int n = 0;
        for (CellView[] row : cells)
            for (CellView v : row) if (v.revealed) n++;
        return n;
    }

    int rowsCount() {
        return rows;
    }

    int colsCount() {
        return cols;
    }

    String currentState() {
        return roomState;
    }

    private void openInviteDialog() {
        if (!"WAITING".equals(roomState)) {
            JOptionPane.showMessageDialog(this,
                    "Приглашать можно только до начала игры.",
                    "Приглашение", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Загружаем друзей
        client.getFriends().whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || resp == null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // Только принятые, исключая тех, кто уже в комнате
                    java.util.List<GetFriendsResponse.FriendInfo> candidates =
                            new java.util.ArrayList<>();
                    java.util.Set<String> inRoom = new java.util.HashSet<>();
                    for (int i = 0; i < playersModel.getRowCount(); i++) {
                        inRoom.add((String) playersModel.getValueAt(i, 0));
                    }
                    for (var f : resp.friends) {
                        if ("ACCEPTED".equals(f.status) && !inRoom.contains(f.username)) {
                            candidates.add(f);
                        }
                    }

                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "Нет доступных друзей для приглашения.",
                                "Приглашение", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // Диалог выбора
                    String[] names = candidates.stream()
                            .map(f -> f.username).toArray(String[]::new);
                    String chosen = (String) JOptionPane.showInputDialog(this,
                            "Кого пригласить?", "Приглашение",
                            JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
                    if (chosen == null) return;

                    long targetId = -1;
                    for (var f : candidates) {
                        if (f.username.equals(chosen)) { targetId = f.userId; break; }
                    }
                    if (targetId < 0) return;

                    client.inviteFriendToRoom(targetId, roomId).whenComplete((r, e) ->
                            SwingUtilities.invokeLater(() -> {
                                if (e != null) {
                                    JOptionPane.showMessageDialog(this,
                                            "Ошибка: " + e.getMessage(),
                                            "Приглашение", JOptionPane.ERROR_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(this, r.message,
                                            r.success ? "OK" : "Не удалось",
                                            r.success ? JOptionPane.INFORMATION_MESSAGE
                                                    : JOptionPane.WARNING_MESSAGE);
                                }
                            }));
                }));
    }
}
