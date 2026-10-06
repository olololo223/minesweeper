package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.payload.FriendInvitePush;
import com.example.minesweeper.protocol.payload.FriendPush;
import com.example.minesweeper.protocol.payload.LeaderboardResponse;
import com.example.minesweeper.protocol.payload.MpLeaderboardResponse;

import javax.swing.*;
import javax.swing.Timer;          // для опроса друзей; игровой таймер — java.util.Timer
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.TimerTask;


public class Minesweeper extends JFrame {
    private final GameClient gameClient;   // может быть null

    public Minesweeper() {
        this(null);
    }


    // Цвета плиток
    private static final Color CLOSED_BG   = new Color(0xBDBDBD); // закрытая — серый
    private static final Color CLOSED_HOVER= new Color(0xD6D6D6); // при наведении
    private static final Color OPENED_BG   = new Color(0xE8E8E8); // открытая — светло-серый
    private static final Color MINE_BG     = new Color(0xFF6B6B); // мина — красный
    private static final Color FLAG_BG     = new Color(0xFFE082); // флажок — жёлтый
    private static final Color WRONG_BG    = new Color(0xFFB3B3); // неверный флажок

    // ==== Уровни сложности ====
    enum Difficulty {
        EASY("Новичок", 9, 9, 10),
        MEDIUM("Любитель", 16, 16, 40),
        HARD("Профессионал", 16, 30, 99);

        final String title;
        final int rows, cols, mines;

        Difficulty(String title, int rows, int cols, int mines) {
            this.title = title;
            this.rows = rows;
            this.cols = cols;
            this.mines = mines;
        }
    }

    /** Режим игры: классика (таймер вверх) или на время (обратный отсчёт). */
    enum GameMode {
        CLASSIC, TIMED
    }

    private static final int CELL_SIZE = 32;

    private Difficulty difficulty = Difficulty.EASY;
    private Cell[][] cells;
    private int rows, cols, mineCount;
    private boolean gameOver;
    private boolean firstClick;
    private int flagsPlaced;
    private int revealedCount;

    private JPanel boardPanel;
    private JLabel minesLabel;
    private JLabel timerLabel;
    private JButton resetButton;
    private JMenuItem friendsItem;                        // пункт меню «Друзья (N)...»

    private java.util.Timer timer;                        // игровой таймер (не Swing!)
    private int seconds;

    // ==== Опрос заявок в друзьях ====
    private Timer friendsPollTimer;                       // javax.swing.Timer, раз в 30 сек
    private int lastIncomingCount = -1;                   // чтобы не трогать UI без изменений

    // ==== Push и заголовок окна ====
    private String baseTitle = "Сапёр";                   // заголовок без маркера
    private Timer titleFlashTimer;                        // одноразовый таймер маркера
    private volatile boolean closing;                     // окно закрывается штатно

    // ==== Режим и обратный отсчёт ====
    private GameMode currentMode = GameMode.CLASSIC;
    private int timeLeft;                                 // остаток в timed-режиме
    private JMenuItem[] difficultyItems;                  // пункты сложностей в меню

    /** Лимит времени в timed-режиме зависит от сложности. */
    private static int timedLimitFor(Difficulty d) {
        return switch (d) {
            case EASY   -> 60;    // 9×9, 10 мин — минута
            case MEDIUM -> 180;   // 16×16, 40 мин — 3 минуты
            case HARD   -> 420;   // 16×30, 99 мин — 7 минут
        };
    }

    /** Человекочитаемый лимит: 60 -> "1 мин", 90 -> "90 сек". */
    private static String timedLimitLabel(int seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + " мин" : seconds + " сек.";
    }

    // ====== Модель клетки ======
    static class Cell {
        boolean hasMine;
        boolean revealed;
        boolean flagged;
        int neighborMines;
        JButton button;
    }

    public Minesweeper(GameClient client) {
        super("Сапёр");
        this.gameClient = client;

        // Закрытие окна: остановить опрос, аккуратно закрыть соединение и выйти
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closing = true;                   // чтобы onDisconnected не показал диалог
                if (friendsPollTimer != null) friendsPollTimer.stop();
                if (titleFlashTimer != null) titleFlashTimer.stop();
                if (gameClient != null) gameClient.close();
                dispose();
                System.exit(0);
            }
        });
        setLayout(new BorderLayout());

        // Верхняя панель: счётчик мин, кнопка сброса, таймер
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        minesLabel = new JLabel("Мины: 0", SwingConstants.LEFT);
        minesLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        timerLabel = new JLabel("Время: 0", SwingConstants.RIGHT);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        resetButton = new JButton("Новая игра");
        resetButton.addActionListener(e -> newGame(difficulty));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(minesLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.add(timerLabel);

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerPanel.add(resetButton);

        top.add(leftPanel, BorderLayout.WEST);
        top.add(centerPanel, BorderLayout.CENTER);
        top.add(rightPanel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Игровое поле
        boardPanel = new JPanel();
        add(boardPanel, BorderLayout.CENTER);

        // Нижняя панель — статус соединения
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JLabel connLabel = new JLabel();
        if (gameClient != null && gameClient.isConnected()) {
            connLabel.setText("● Онлайн");
            connLabel.setForeground(new Color(0, 140, 0));
        } else {
            connLabel.setText("● Оффлайн");
            connLabel.setForeground(Color.GRAY);
        }
        statusBar.add(connLabel);
        add(statusBar, BorderLayout.SOUTH);

        // Меню выбора сложности
        JMenuBar menuBar = new JMenuBar();
        JMenu gameMenu = new JMenu("Игра");
        JMenu netMenu = new JMenu("Сеть");
        JMenu accountMenu = new JMenu("Аккаунт");

        JMenuItem statsItem = new JMenuItem("Моя статистика...");
        statsItem.setEnabled(gameClient != null && gameClient.isConnected());
        statsItem.addActionListener(e -> showMyStats());
        accountMenu.add(statsItem);

        JMenuItem mpStatsItem = new JMenuItem("Моя статистика мультиплеера...");
        mpStatsItem.setEnabled(gameClient != null && gameClient.isConnected());
        mpStatsItem.addActionListener(e -> showMpStats());
        accountMenu.add(mpStatsItem);

        JMenuItem changePwdItem = new JMenuItem("Сменить пароль...");
        changePwdItem.setEnabled(gameClient != null && gameClient.isConnected());
        changePwdItem.addActionListener(e -> showChangePasswordDialog());
        accountMenu.add(changePwdItem);

        friendsItem = new JMenuItem("Друзья...");
        friendsItem.setEnabled(gameClient != null && gameClient.isConnected());
        friendsItem.addActionListener(e -> openFriendsDialog());
        accountMenu.addSeparator();
        accountMenu.add(friendsItem);

        menuBar.add(accountMenu);

        // Сетевая игра: лобби и рейтинг — в одном меню «Сеть»
        JMenuItem lobbyItem = new JMenuItem("Лобби...");
        lobbyItem.setEnabled(gameClient != null && gameClient.isConnected());
        lobbyItem.addActionListener(e -> openLobby());
        netMenu.add(lobbyItem);
        netMenu.addSeparator();

        JMenuItem leaderboardItem = new JMenuItem("Рейтинг...");
        leaderboardItem.addActionListener(e -> showLeaderboard());
        netMenu.add(leaderboardItem);
        JMenuItem mpLeaderboardItem = new JMenuItem("Рейтинг мультиплеера...");
        mpLeaderboardItem.addActionListener(e -> showMpLeaderboard());
        netMenu.add(mpLeaderboardItem);
        menuBar.add(netMenu);
        // Выбор режима игры
        JRadioButtonMenuItem classicItem = new JRadioButtonMenuItem("Классика", true);
        JRadioButtonMenuItem timedItem = new JRadioButtonMenuItem("На время");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(classicItem);
        modeGroup.add(timedItem);

        classicItem.addActionListener(e -> {
            currentMode = GameMode.CLASSIC;
            newGame(difficulty);   // перезапуск с новым режимом
        });
        timedItem.addActionListener(e -> {
            currentMode = GameMode.TIMED;
            newGame(difficulty);
        });

        gameMenu.add(classicItem);
        gameMenu.add(timedItem);
        gameMenu.addSeparator();

        Difficulty[] allDifficulties = Difficulty.values();
        difficultyItems = new JMenuItem[allDifficulties.length];
        for (int i = 0; i < allDifficulties.length; i++) {
            Difficulty d = allDifficulties[i];
            JMenuItem item = new JMenuItem();
            item.addActionListener(e -> newGame(d));
            difficultyItems[i] = item;
            gameMenu.add(item);
        }
        refreshDifficultyMenu();
        gameMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(e -> System.exit(0));
        gameMenu.add(exitItem);
        menuBar.add(gameMenu);
        setJMenuBar(menuBar);

        newGame(difficulty);

        if (gameClient != null && gameClient.isConnected()) {
            // Подписка на push от сервера (мгновенный бейдж) ...
            gameClient.addListener(new GameClient.MessageListener() {
                @Override
                public void onMessage(com.example.minesweeper.protocol.GameMessage msg) {
                    switch (msg.getType()) {
                        case FRIEND_REQUEST_PUSH -> handleFriendPush(
                                (FriendPush) msg.getPayload());
                        case FRIEND_ACCEPTED_PUSH -> handleFriendAccepted(
                                (FriendPush) msg.getPayload());
                        case FRIEND_INVITE_PUSH -> handleFriendInvite(
                                (com.example.minesweeper.protocol.payload.FriendInvitePush) msg.getPayload());
                        default -> { /* остальное обрабатывают futures запросов */ }
                    }
                }

                @Override
                public void onDisconnected() {
                    // При штатном выходе окно уже закрывается — диалог не нужен
                    if (closing) return;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            Minesweeper.this,
                            "Соединение с сервером потеряно.",
                            "Ошибка сети", JOptionPane.WARNING_MESSAGE));
                }
            });

            // ... и polling как fallback: если push потерялся, через 30 секунд подтянем
            friendsPollTimer = new Timer(30_000, e -> refreshIncomingBadge());
            friendsPollTimer.setInitialDelay(2_000);   // до start(), иначе не подействует
            friendsPollTimer.start();
        }
        refreshIncomingBadge();                       // и сразу — чтобы бейдж не ждал 2 секунды

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    /** Push о новой входящей заявке. Приходит из Netty, не из EDT. */
    private void handleFriendPush(FriendPush push) {
        if (push == null) return;
        SwingUtilities.invokeLater(() -> {
            logPush(push);
            updateFriendsBadge(push.incomingCount);
            beep();
            flashTitleMarker();
        });
    }

    /** Push о том, что нашу заявку приняли. */
    private void handleFriendAccepted(FriendPush push) {
        if (push == null) return;
        SwingUtilities.invokeLater(() -> {
            logPush(push);
            updateFriendsBadge(push.incomingCount);
            beep();
        });
    }

    private void logPush(FriendPush push) {
        System.out.println("[push] " + push.event + " от " + push.fromUsername
                + ", входящих: " + push.incomingCount);
    }

    private void beep() {
        try {
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
            // звонок — необязательная мелочь, падать из-за него нельзя
        }
    }

    /**
     * Мигает маркером в заголовке. Таймер один и перезапускается,
     * поэтому маркеры не накапливаются, а заголовок всегда возвращается к baseTitle.
     */
    private void flashTitleMarker() {
        if (titleFlashTimer == null) {
            titleFlashTimer = new Timer(3000, e -> setTitle(baseTitle));
            titleFlashTimer.setRepeats(false);
        }
        setTitle(baseTitle + "  ●");
        titleFlashTimer.restart();
    }

    /** Лобби мультиплеера. Модальное: после закрытия узнаём, вошёл ли игрок в комнату. */
    private void openLobby() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Лобби доступно только онлайн.",
                    "Сетевая игра", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        LobbyDialog lobby = new LobbyDialog(this, gameClient);
        setCursor(Cursor.getDefaultCursor());
        lobby.setVisible(true);

        long roomId = lobby.getJoinedRoomId();
        if (roomId > 0) {
            // Окно комнаты живёт своей жизнью; главное окно остаётся на фоне,
            // чтобы после выхода из комнаты вернуться в одиночную игру
            new MultiplayerFrame(gameClient, roomId).setVisible(true);
            refreshIncomingBadge();
        }
    }

    private void openFriendsDialog() {        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Друзья доступны только онлайн.",
                    "Друзья", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Модальное окно: после закрытия сразу обновляем бейдж —
        // пользователь мог принять или отклонить заявку
        new FriendsDialog(this, gameClient).setVisible(true);
        refreshIncomingBadge();
    }

    /** Спрашивает у сервера число входящих заявок и обновляет бейдж. */
    private void refreshIncomingBadge() {
        if (gameClient == null || !gameClient.isConnected() || friendsItem == null) return;
        gameClient.countFriendRequests().whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || resp == null || !resp.success) return;
                    updateFriendsBadge(resp.count);
                }));
    }

    /** Текст и цвет пункта меню; без изменений UI не трогаем. */
    private void updateFriendsBadge(int incoming) {
        if (friendsItem == null) return;
        if (incoming == lastIncomingCount) return;
        lastIncomingCount = incoming;

        if (incoming > 0) {
            friendsItem.setText("Друзья (" + incoming + ")...");
            friendsItem.setForeground(new Color(0xC0, 0x39, 0x2B));   // красноватый
        } else {
            friendsItem.setText("Друзья...");
            friendsItem.setForeground(null);                          // дефолтный цвет
        }
    }

    private void showLeaderboard() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Игра запущена в оффлайн-режиме. Рейтинг недоступен.",
                    "Рейтинг", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 1. Выбор режима
        Object[] modes = {"Классика", "На время"};
        int modeChoice = JOptionPane.showOptionDialog(this,
                "Какой режим?", "Рейтинг",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, modes, modes[0]);
        if (modeChoice < 0) return;
        String mode = modeChoice == 1 ? "TIMED" : "CLASSIC";

        // 2. Выбор сложности
        Object[] diffs = {"Новичок", "Любитель", "Профессионал"};
        int diffChoice = JOptionPane.showOptionDialog(this,
                "Какая сложность?", "Рейтинг",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, diffs, diffs[0]);
        if (diffChoice < 0) return;
        String diff = switch (diffChoice) {
            case 1 -> "MEDIUM";
            case 2 -> "HARD";
            default -> "EASY";
        };

        // 3. Запрос
        gameClient.getLeaderboard(diff, mode)
                .whenComplete((entries, err) -> SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Не удалось получить рейтинг: " + err.getMessage(),
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    showLeaderboardDialog(diff, mode, entries);
                }));
    }

    private void showLeaderboardDialog(String difficulty, String mode,
                                       java.util.List<LeaderboardResponse.Entry> entries) {
        String modeName = "TIMED".equals(mode) ? "На время" : "Классика";
        String diffName = switch (difficulty) {
            case "MEDIUM" -> "Любитель";
            case "HARD" -> "Профессионал";
            default -> "Новичок";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("Рейтинг — ").append(diffName)
                .append(" / ").append(modeName).append("\n\n");
        if (entries.isEmpty()) {
            sb.append("(пока никто не играл)");
        } else {
            int place = 1;
            for (var e : entries) {
                sb.append(String.format("%2d. %-20s %4d сек.%n",
                        place++, e.username, e.bestTimeSeconds));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(),
                "Рейтинг", JOptionPane.INFORMATION_MESSAGE);
    }

    // ====== Новая игра ======
    private void newGame(Difficulty d) {
        stopTimer();
        this.difficulty = d;
        this.rows = d.rows;
        this.cols = d.cols;
        this.mineCount = d.mines;

        this.gameOver = false;
        this.firstClick = true;
        this.flagsPlaced = 0;
        this.revealedCount = 0;
        this.seconds = 0;
        // В классике обратного отсчёта нет, поэтому остаток не нужен
        this.timeLeft = currentMode == GameMode.TIMED ? timedLimitFor(d) : 0;

        // В заголовке видно режим и лимит времени
        String modeStr = currentMode == GameMode.CLASSIC
                ? "Классика"
                : "На время (" + timedLimitFor(d) + " сек.)";
        baseTitle = "Сапёр — " + d.title + " [" + modeStr + "]";
        setTitle(baseTitle);

        refreshDifficultyMenu();
        minesLabel.setText("Мины: " + (mineCount - flagsPlaced));
        updateTimerLabel();

        boardPanel.removeAll();
        boardPanel.setLayout(new GridLayout(rows, cols));
        cells = new Cell[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = new Cell();
                JButton btn = new JButton();
                btn.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setFont(new Font("SansSerif", Font.BOLD, 14));
                btn.setFocusPainted(false);
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBorderPainted(true);
                btn.setBorder(BorderFactory.createRaisedBevelBorder());
                btn.setBackground(CLOSED_BG);   // цвет закрытой клетки

                final int rr = r, cc = c;
                btn.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (gameOver) return;
                        if (SwingUtilities.isRightMouseButton(e)) {
                            toggleFlag(rr, cc);
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            revealCell(rr, cc);
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        Cell c2 = cells[rr][cc];
                        if (!c2.revealed && !c2.flagged && !gameOver) {
                            c2.button.setBackground(CLOSED_HOVER);
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        Cell c2 = cells[rr][cc];
                        if (!c2.revealed && !c2.flagged) {
                            c2.button.setBackground(CLOSED_BG);
                        }
                    }
                });

                cell.button = btn;
                cells[r][c] = cell;
                boardPanel.add(btn);
            }
        }
        boardPanel.revalidate();
        boardPanel.repaint();
        pack();
        setLocationRelativeTo(null);
    }

    // ====== Установка мин после первого клика ======
    private void placeMines(int safeRow, int safeCol) {
        Random rnd = new Random();
        int placed = 0;
        while (placed < mineCount) {
            int r = rnd.nextInt(rows);
            int c = rnd.nextInt(cols);
            // Не ставим мину в первую открытую клетку и её соседей,
            // чтобы первый клик всегда открывал пустую зону
            if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) continue;
            if (cells[r][c].hasMine) continue;
            cells[r][c].hasMine = true;
            placed++;
        }
        // Подсчёт соседних мин
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells[r][c].neighborMines = countNeighborMines(r, c);
            }
        }
    }

    private int countNeighborMines(int r, int c) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr, nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && cells[nr][nc].hasMine) {
                    count++;
                }
            }
        }
        return count;
    }

    // ====== Открытие клетки ======
    private void revealCell(int r, int c) {
        Cell cell = cells[r][c];
        if (cell.revealed || cell.flagged) return;

        if (firstClick) {
            firstClick = false;
            placeMines(r, c);
            startTimer();
        }

        if (cell.hasMine) {
            cell.revealed = true;
            updateCellView(r, c);
            loseGame();
            return;
        }

        // Рекурсивное открытие пустых клеток
        floodFill(r, c);

        checkWin();
    }

    private void floodFill(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        Cell cell = cells[r][c];
        if (cell.revealed || cell.flagged) return;

        cell.revealed = true;
        revealedCount++;
        updateCellView(r, c);

        if (cell.neighborMines == 0) {
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    floodFill(r + dr, c + dc);
                }
            }
        }
    }

    // ====== Флажок ======
    private void toggleFlag(int r, int c) {
        Cell cell = cells[r][c];
        if (cell.revealed) return;
        cell.flagged = !cell.flagged;
        flagsPlaced += cell.flagged ? 1 : -1;
        minesLabel.setText("Мины: " + (mineCount - flagsPlaced));
        updateCellView(r, c);
    }

    // ====== Обновление внешнего вида клетки ======
    private void updateCellView(int r, int c) {
        Cell cell = cells[r][c];
        JButton btn = cell.button;

        if (cell.revealed) {
            // === ОТКРЫТАЯ КЛЕТКА ===
            btn.setEnabled(true);          // оставляем enabled, чтобы фон не сбрасывался L&F
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);

            if (cell.hasMine) {
                btn.setBackground(MINE_BG);
                btn.setText("💣");
                btn.setForeground(Color.BLACK);
            } else if (cell.neighborMines > 0) {
                btn.setBackground(OPENED_BG);
                btn.setText(String.valueOf(cell.neighborMines));
                btn.setForeground(colorForNumber(cell.neighborMines));
            } else {
                btn.setBackground(OPENED_BG);
                btn.setText("");
            }
        } else {
            // === ЗАКРЫТАЯ КЛЕТКА ===
            btn.setEnabled(true);
            btn.setBorderPainted(true);
            btn.setBorder(BorderFactory.createRaisedBevelBorder());

            if (cell.flagged) {
                btn.setBackground(FLAG_BG);
                btn.setText("🚩");
                btn.setForeground(Color.BLACK);
            } else {
                btn.setBackground(CLOSED_BG);
                btn.setText("");
            }
        }
    }

    private Color colorForNumber(int n) {
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

    // ====== Проверка победы ======
    private void checkWin() {
        if (revealedCount == rows * cols - mineCount) {
            gameOver = true;
            stopTimer();
            // Помечаем все мины флажками
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    Cell cell = cells[r][c];
                    if (cell.hasMine && !cell.flagged) {
                        cell.flagged = true;
                        updateCellView(r, c);
                    }
                }
            }
            minesLabel.setText("Мины: 0");
            JOptionPane.showMessageDialog(this,
                    "Победа! Время: " + elapsedSeconds() + " сек.",
                    "Победа", JOptionPane.INFORMATION_MESSAGE);
            sendResultToServer(elapsedSeconds(), true);
        }
    }

    // ====== Проигрыш ======
    private void loseGame() {
        gameOver = true;
        stopTimer();
        // Показываем все мины
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = cells[r][c];
                if (cell.hasMine && !cell.flagged) {
                    cell.revealed = true;
                    updateCellView(r, c);
                } else if (!cell.hasMine && cell.flagged) {
                    // Неправильный флажок
                    cell.button.setBackground(WRONG_BG);
                    cell.button.setForeground(Color.BLACK);
                    cell.button.setText("❌");
                }
            }
        }
        JOptionPane.showMessageDialog(this,
                "Вы подорвались!",
                "Проигрыш", JOptionPane.ERROR_MESSAGE);
        sendResultToServer(elapsedSeconds(), false);
    }

    private void sendResultToServer(int durationSeconds, boolean win) {
        if (gameClient == null || !gameClient.isConnected()) return;

        String diff = difficulty.name();   // EASY / MEDIUM / HARD
        String mode = currentMode.name();  // CLASSIC / TIMED
        gameClient.submitScore(diff, mode, durationSeconds, win)
                .whenComplete((ok, err) -> SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        System.out.println("[game] Не удалось сохранить результат: "
                                + err.getMessage());
                    } else {
                        System.out.println("[game] Результат сохранён: " + ok);
                    }
                }));
    }

    /**
     * Сколько секунд длилась партия.
     * В классике считаем вверх, в timed — как разницу лимита и остатка:
     * иначе победная партия на время ушла бы на сервер с нулём секунд
     * и навсегда заняла первое место в рейтинге.
     */
    private int elapsedSeconds() {
        return currentMode == GameMode.CLASSIC
                ? seconds
                : timedLimitFor(difficulty) - timeLeft;
    }

    /**
     * Подписи сложностей в меню.
     * В timed-режиме к каждой сложности дописываем её лимит времени,
     * чтобы игрок видел условия до начала партии.
     */
    private void refreshDifficultyMenu() {
        if (difficultyItems == null) return;
        Difficulty[] all = Difficulty.values();
        for (int i = 0; i < all.length && i < difficultyItems.length; i++) {
            Difficulty d = all[i];
            String hint = currentMode == GameMode.TIMED
                    ? " — на время " + timedLimitLabel(timedLimitFor(d))
                    : "";
            difficultyItems[i].setText(d.title + " (" + d.rows + "x" + d.cols
                    + ", мин: " + d.mines + ")" + hint);
        }
    }

    // ====== Таймер ======
    private void startTimer() {
        timer = new java.util.Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (gameOver) return;

                    if (currentMode == GameMode.CLASSIC) {
                        seconds++;
                    } else {
                        // Timed: считаем обратный отсчёт
                        timeLeft--;
                        if (timeLeft <= 0) {
                            timeLeft = 0;
                            updateTimerLabel();
                            loseByTimeout();
                            return;
                        }
                    }
                    updateTimerLabel();
                });
            }
        }, 1000, 1000);
    }

    private void updateTimerLabel() {
        if (currentMode == GameMode.CLASSIC) {
            timerLabel.setText("Время: " + seconds);
            timerLabel.setForeground(Color.BLACK);
        } else {
            timerLabel.setText("Осталось: " + timeLeft + " сек.");
            // Подкрашиваем красным при малом остатке
            timerLabel.setForeground(timeLeft <= 10 ? Color.RED : Color.BLACK);
        }
    }

    /** Время вышло: проигрыш по таймеру, мины показываем как при подрыве. */
    private void loseByTimeout() {
        if (gameOver) return;
        gameOver = true;
        stopTimer();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = cells[r][c];
                if (cell.hasMine && !cell.flagged) {
                    cell.revealed = true;
                    updateCellView(r, c);
                }
            }
        }
        JOptionPane.showMessageDialog(this,
                "Время вышло!",
                "Проигрыш по времени", JOptionPane.ERROR_MESSAGE);

        sendResultToServer(elapsedSeconds(), false);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void showChangePasswordDialog() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Смена пароля доступна только онлайн.",
                    "Аккаунт", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPasswordField oldField = new JPasswordField(15);
        JPasswordField newField = new JPasswordField(15);
        JPasswordField confirmField = new JPasswordField(15);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; panel.add(new JLabel("Старый пароль:"), c);
        c.gridx = 1; panel.add(oldField, c);

        c.gridx = 0; c.gridy = 1; panel.add(new JLabel("Новый пароль:"), c);
        c.gridx = 1; panel.add(newField, c);

        c.gridx = 0; c.gridy = 2; panel.add(new JLabel("Повтор нового:"), c);
        c.gridx = 1; panel.add(confirmField, c);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Смена пароля", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String oldPass = new String(oldField.getPassword());
        String newPass = new String(newField.getPassword());
        String confirm = new String(confirmField.getPassword());

        if (oldPass.isEmpty() || newPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Заполните все поля",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!newPass.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Новые пароли не совпадают",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (newPass.length() < 4) {
            JOptionPane.showMessageDialog(this, "Пароль минимум 4 символа",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        gameClient.changePassword(oldPass, newPass)
                .whenComplete((resp, err) -> SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Ошибка: " + err.getMessage(),
                                "Смена пароля", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                resp.message,
                                resp.success ? "Успех" : "Ошибка",
                                resp.success ? JOptionPane.INFORMATION_MESSAGE
                                        : JOptionPane.ERROR_MESSAGE);
                    }
                }));
    }

    private void showMyStats() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Статистика доступна только онлайн.",
                    "Аккаунт", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Пока ждём — курсор «занято»
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        gameClient.getMyStats(50).whenComplete((stats, err) ->
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Не удалось получить статистику: " + err.getMessage(),
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!stats.success) {
                        JOptionPane.showMessageDialog(this, stats.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    new StatsDialog(this, stats).setVisible(true);
                }));
    }

    // ====== Точка входа ======

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            LoginDialog dialog = new LoginDialog(null);
            dialog.setVisible(true);

            GameClient client = dialog.getClient();
            new Minesweeper(client).setVisible(true);
        });
    }

    private void handleFriendInvite(FriendInvitePush push) {
        SwingUtilities.invokeLater(() -> {
            Toolkit.getDefaultToolkit().beep();

            String diff = switch (push.difficulty) {
                case "MEDIUM" -> "Любитель";
                case "HARD" -> "Профессионал";
                default -> "Новичок";
            };

            int answer = JOptionPane.showConfirmDialog(this,
                    push.fromUsername + " приглашает вас в комнату:\n\n" +
                            "  «" + push.roomName + "» (" + diff + ")\n\n" +
                            "Присоединиться?",
                    "Приглашение в игру",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (answer == JOptionPane.YES_OPTION) {
                joinInvitedRoom(push.roomId);
            }
        });
    }

    private void joinInvitedRoom(long roomId) {
        if (gameClient == null || !gameClient.isConnected()) return;

        gameClient.joinRoom(roomId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Не удалось присоединиться",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    new MultiplayerFrame(gameClient, resp.roomId);
                }));
    }
    private void showMpLeaderboard() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Рейтинг доступен только онлайн.",
                    "Рейтинг", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        gameClient.getMpLeaderboard(20).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    showMpLeaderboardDialog(resp);
                }));
    }

    private void showMpLeaderboardDialog(MpLeaderboardResponse resp) {
        StringBuilder sb = new StringBuilder();
        sb.append("Рейтинг мультиплеера (топ-").append(resp.entries.size()).append(")\n\n");
        if (resp.entries.isEmpty()) {
            sb.append("(пока никто не играл в мультиплеер)");
        } else {
            sb.append(String.format("%-3s %-20s %8s %6s %6s%n",
                    "#", "Игрок", "Очки", "Партий", "Побед"));
            int place = 1;
            for (var e : resp.entries) {
                sb.append(String.format("%-3d %-20s %8d %6d %6d%n",
                        place++, e.username, e.totalScore,
                        e.totalGames, e.totalWins));
            }
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(480, 400));
        JOptionPane.showMessageDialog(this, scroll,
                "Рейтинг мультиплеера", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showMpStats() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Статистика доступна только онлайн.",
                    "Аккаунт", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        gameClient.getMpStats().whenComplete((stats, err) ->
                SwingUtilities.invokeLater(() -> {
                    setCursor(Cursor.getDefaultCursor());
                    if (err != null || !stats.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : stats.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    new MpStatsDialog(this, stats).setVisible(true);
                }));
    }
}