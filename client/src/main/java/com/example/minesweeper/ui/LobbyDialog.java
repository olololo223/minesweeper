package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.payload.CreateRoomResponse;
import com.example.minesweeper.protocol.payload.JoinRoomResponse;
import com.example.minesweeper.protocol.payload.ListRoomsResponse;
import com.example.minesweeper.protocol.payload.ListRoomsResponse.RoomInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Лобби мультиплеера: список комнат, создание, вход.
 * При успешном входе возвращает id комнаты через getJoinedRoomId().
 *
 * Вся отрисовка строк вынесена в статические методы — их можно
 * проверить без окна (см. LobbyProbe).
 */
public class LobbyDialog extends JDialog {

    private final GameClient client;

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Название", "Сложность", "Игроки", "Состояние"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel(" ");
    private final Timer refreshTimer;                 // javax.swing.Timer: тикает в EDT

    /** Текущий список — по нему определяем выбранную комнату (без разбора строк таблицы). */
    private List<RoomInfo> currentRooms = new ArrayList<>();

    /**
     * Запрос уже в полёте. Тик таймера (3 с) может обогнать ответ, а клиент
     * держит по одному ожидающему future на тип — второй запрос вытеснил бы
     * первый, и тот завершился бы таймаутом с ложной ошибкой в статусе.
     */
    private boolean refreshing;

    private long joinedRoomId = -1;

    public LobbyDialog(Frame owner, GameClient client) {
        super(owner, "Сетевая игра — Лобби", true);
        this.client = client;

        setSize(720, 460);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        // ---- Таблица комнат ----
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Двойной клик = войти
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) joinSelected();
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // ---- Статус ----
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(statusLabel, BorderLayout.NORTH);

        // ---- Кнопки ----
        JButton refreshBtn = new JButton("Обновить");
        refreshBtn.addActionListener(e -> refreshRooms());

        JButton createBtn = new JButton("Создать комнату");
        createBtn.addActionListener(e -> createRoomDialog());

        JButton joinBtn = new JButton("Войти");
        joinBtn.addActionListener(e -> joinSelected());

        JButton closeBtn = new JButton("Закрыть");
        closeBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(refreshBtn);
        buttons.add(createBtn);
        buttons.add(joinBtn);
        buttons.add(closeBtn);
        add(buttons, BorderLayout.SOUTH);

        // ---- Автообновление раз в 3 секунды (это ~20 запросов в минуту) ----
        refreshTimer = new Timer(3000, e -> refreshRooms());
        refreshTimer.setInitialDelay(0);
        refreshTimer.start();

        refreshRooms();                                // и сразу, чтобы список не ждал
    }

    /** Если игрок вошёл в комнату — вернёт её id, иначе -1. */
    public long getJoinedRoomId() {
        return joinedRoomId;
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) refreshTimer.stop();
        super.dispose();
    }

    // ============ ЗАПРОС СПИСКА ============

    /** Клиент может отвалиться, пока окно открыто: send() бросает синхронно. */
    private boolean online() {
        if (client != null && client.isConnected()) return true;
        statusLabel.setText("Нет соединения с сервером");
        return false;
    }

    private void refreshRooms() {
        if (refreshing) return;                        // предыдущий запрос ещё не вернулся
        if (!online()) return;

        refreshing = true;
        try {
            client.listRooms().whenComplete((resp, err) ->
                    SwingUtilities.invokeLater(() -> {
                        refreshing = false;
                        if (err != null) {
                            statusLabel.setText("Ошибка: " + err.getMessage());
                            return;
                        }
                        fillTable(resp.rooms == null ? List.of() : resp.rooms);
                        statusLabel.setText("Комнат: " + currentRooms.size()
                                + "    (обновлено " + nowTime() + ")");
                    }));
        } catch (RuntimeException e) {
            refreshing = false;
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    private void fillTable(List<RoomInfo> rooms) {
        // Запоминаем выделение по индексу строки: таблица и currentRooms синхронны
        int selRow = table.getSelectedRow();
        long selectedId = (selRow >= 0 && selRow < currentRooms.size())
                ? currentRooms.get(selRow).roomId : -1;

        currentRooms = new ArrayList<>(rooms);
        Object[][] rows = roomRows(rooms);

        model.setRowCount(0);
        for (Object[] row : rows) model.addRow(row);

        // Возвращаем выделение, но не дёргаем таблицу, если строка осталась на месте:
        // setRowSelectionInterval прокручивает список к выделенной строке
        if (selectedId >= 0) {
            for (int i = 0; i < rows.length; i++) {
                if ((Long) rows[i][0] == selectedId) {
                    if (i != selRow) table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    // ============ СОЗДАНИЕ ============

    private void createRoomDialog() {
        if (!online()) return;

        JTextField nameField = new JTextField("Моя комната", 20);
        JComboBox<String> diffBox = new JComboBox<>(DIFFICULTY_TITLES);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Название:"), c);
        c.gridx = 1; form.add(nameField, c);

        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Сложность:"), c);
        c.gridx = 1; form.add(diffBox, c);

        int ok = JOptionPane.showConfirmDialog(this, form,
                "Создать комнату", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "Моя комната";
        if (name.length() > 40) name = name.substring(0, 40);

        String diff = difficultyCode(diffBox.getSelectedIndex());
        statusLabel.setText("Создаём комнату...");

        try {
            client.createRoom(diff, name).whenComplete((resp, err) ->
                    SwingUtilities.invokeLater(() -> {
                        if (err != null) {
                            statusLabel.setText("Ошибка: " + err.getMessage());
                        } else if (!resp.success) {
                            statusLabel.setText(resp.message);
                        } else {
                            enterRoom(resp.roomId, resp.message);
                        }
                    }));
        } catch (RuntimeException e) {
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    // ============ ВХОД ============

    private void joinSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentRooms.size()) {
            statusLabel.setText("Выберите комнату в списке");
            return;
        }
        if (!online()) return;

        RoomInfo room = currentRooms.get(row);
        if (!isWaiting(room.state)) {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Игра в этой комнате уже началась. Всё равно войти?",
                    "Подтверждение", JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) return;
        }

        statusLabel.setText("Входим в комнату " + room.roomId + "...");
        try {
            client.joinRoom(room.roomId).whenComplete((resp, err) ->
                    SwingUtilities.invokeLater(() -> {
                        if (err != null) {
                            statusLabel.setText("Ошибка: " + err.getMessage());
                        } else if (!resp.success) {
                            statusLabel.setText(resp.message);
                        } else {
                            enterRoom(resp.roomId, resp.message);
                        }
                    }));
        } catch (RuntimeException e) {
            statusLabel.setText("Нет соединения с сервером");
        }
    }

    /** Успешный вход: запоминаем комнату и закрываем лобби. */
    private void enterRoom(long roomId, String message) {
        joinedRoomId = roomId;
        statusLabel.setText("Вошли в комнату " + roomId + " (" + message + ")");

        JOptionPane.showMessageDialog(this,
                "Вошёл в комнату " + roomId + "\n"
                        + "Игровое окно появится в следующем шаге.",
                "Сетевая игра", JOptionPane.INFORMATION_MESSAGE);

        dispose();          // остановит таймер и вернёт управление openLobby()
    }

    // ============ ЧИСТЫЕ ФУНКЦИИ (проверяются без окна) ============

    static final String[] DIFFICULTY_TITLES = {"Новичок", "Любитель", "Профессионал"};

    /** Индекс в выпадающем списке → код сложности для сервера. */
    static String difficultyCode(int selectedIndex) {
        return switch (selectedIndex) {
            case 1 -> "MEDIUM";
            case 2 -> "HARD";
            default -> "EASY";
        };
    }

    /** Данные для таблицы комнат: ID, название, сложность, игроки, состояние. */
    static Object[][] roomRows(List<RoomInfo> rooms) {
        Object[][] rows = new Object[rooms.size()][5];
        for (int i = 0; i < rooms.size(); i++) {
            RoomInfo r = rooms.get(i);
            rows[i][0] = r.roomId;
            rows[i][1] = r.roomName;
            rows[i][2] = localizedDifficulty(r.difficulty);
            rows[i][3] = r.players + " / " + r.maxPlayers;
            rows[i][4] = localizedState(r.state);
        }
        return rows;
    }

    /** Можно ли входить без предупреждения. */
    static boolean isWaiting(String state) {
        return "WAITING".equals(state);
    }

    static String localizedDifficulty(String d) {
        if (d == null) return "—";
        return switch (d) {
            case "MEDIUM" -> "Любитель";
            case "HARD" -> "Профессионал";
            default -> "Новичок";
        };
    }

    static String localizedState(String s) {
        if (s == null) return "—";
        return switch (s) {
            case "WAITING" -> "Ожидание";
            case "PLAYING" -> "Игра идёт";
            case "FINISHED" -> "Завершена";
            default -> s;
        };
    }

    static String nowTime() {
        return java.time.LocalTime.now().withNano(0).toString();
    }
}
