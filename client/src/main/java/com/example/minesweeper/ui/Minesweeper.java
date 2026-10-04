package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.payload.LeaderboardResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import java.util.Timer;
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

    private Timer timer;
    private int seconds;

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
        setDefaultCloseOperation(EXIT_ON_CLOSE);
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
        JMenuItem leaderboardItem = new JMenuItem("Рейтинг...");
        leaderboardItem.addActionListener(e -> showLeaderboard());
        netMenu.add(leaderboardItem);
        menuBar.add(netMenu);
        for (Difficulty d : Difficulty.values()) {
            JMenuItem item = new JMenuItem(d.title + " (" + d.rows + "x" + d.cols + ", " + d.mines + " мин)");
            item.addActionListener(e -> newGame(d));
            gameMenu.add(item);
        }
        gameMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Выход");
        exitItem.addActionListener(e -> System.exit(0));
        gameMenu.add(exitItem);
        menuBar.add(gameMenu);
        setJMenuBar(menuBar);

        newGame(difficulty);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void showLeaderboard() {
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Игра запущена в оффлайн-режиме. Рейтинг недоступен.",
                    "Рейтинг", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Диалог выбора сложности
        Object[] options = {"Новичок", "Любитель", "Профессионал"};
        int choice = JOptionPane.showOptionDialog(this,
                "Какую таблицу показать?", "Рейтинг",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0) return;

        String diff = switch (choice) {
            case 1 -> "MEDIUM";
            case 2 -> "HARD";
            default -> "EASY";
        };

        gameClient.getLeaderboard(diff)
                .whenComplete((entries, err) -> SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Не удалось получить рейтинг: " + err.getMessage(),
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    showLeaderboardDialog(diff, entries);
                }));
    }

    private void showLeaderboardDialog(String difficulty,
                                       java.util.List<LeaderboardResponse.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Рейтинг ").append(difficulty).append("\n\n");
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

        setTitle("Сапёр — " + d.title);
        minesLabel.setText("Мины: " + (mineCount - flagsPlaced));
        timerLabel.setText("Время: 0");

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
                    "Победа! Время: " + seconds + " сек.",
                    "Победа", JOptionPane.INFORMATION_MESSAGE);
            sendResultToServer(seconds, true);
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
        sendResultToServer(seconds, false);
    }

    private void sendResultToServer(int seconds, boolean win) {
        if (gameClient == null || !gameClient.isConnected()) return;

        String diff = difficulty.name();   // EASY / MEDIUM / HARD
        gameClient.submitScore(diff, seconds, win)
                .whenComplete((ok, err) -> SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        System.out.println("[game] Не удалось сохранить результат: "
                                + err.getMessage());
                    } else {
                        System.out.println("[game] Результат сохранён: " + ok);
                    }
                }));
    }

    // ====== Таймер ======
    private void startTimer() {
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (!gameOver) {
                        seconds++;
                        timerLabel.setText("Время: " + seconds);
                    }
                });
            }
        }, 1000, 1000);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
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
}