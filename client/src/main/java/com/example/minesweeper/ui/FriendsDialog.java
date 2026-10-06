package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.payload.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Окно «Друзья»: принятые друзья, входящие заявки и поиск игроков.
 */
public class FriendsDialog extends JDialog {

    private final GameClient client;

    private final JTextField searchField = new JTextField(15);
    private final JTable friendsTable = new JTable();
    private final JTable incomingTable = new JTable();
    private final JTable outgoingTable = new JTable();
    private final JLabel statusLabel = new JLabel(" ");

    private List<GetFriendsResponse.FriendInfo> cachedFriends;

    public FriendsDialog(Frame owner, GameClient client) {
        super(owner, "Друзья", true);
        this.client = client;
        setSize(720, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Мои друзья", buildFriendsTab());
        tabs.addTab("Входящие заявки", buildIncomingTab());
        tabs.addTab("Исходящие заявки", buildOutgoingTab());
        tabs.addTab("Поиск игроков", buildSearchTab());

        statusLabel.setForeground(Color.GRAY);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        JButton closeBtn = new JButton("Закрыть");
        closeBtn.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(closeBtn);

        add(statusLabel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        refresh();
    }

    /**
     * Диалог модальный: пока он открыт, связь может пропасть.
     * Без этой проверки запрос бросил бы исключение прямо в EDT.
     */
    private boolean ensureOnline() {
        if (client != null && client.isConnected()) return true;
        statusLabel.setText("Нет соединения с сервером");
        return false;
    }

    // =============== ВКЛАДКА «МОИ ДРУЗЬЯ» ===============

    private JPanel buildFriendsTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        friendsTable.setModel(new DefaultTableModel(
                new Object[]{"Имя", "Друзья с"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        friendsTable.setRowHeight(24);
        p.add(new JScrollPane(friendsTable), BorderLayout.CENTER);

        JButton removeBtn = new JButton("Удалить из друзей");
        removeBtn.addActionListener(e -> removeSelectedFriend());
        JButton refreshBtn = new JButton("Обновить");
        refreshBtn.addActionListener(e -> refresh());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refreshBtn);
        actions.add(removeBtn);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    // =============== ВКЛАДКА «ВХОДЯЩИЕ» ===============

    private JPanel buildIncomingTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        incomingTable.setModel(new DefaultTableModel(
                new Object[]{"Имя", "Дата"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        incomingTable.setRowHeight(24);
        p.add(new JScrollPane(incomingTable), BorderLayout.CENTER);

        JButton acceptBtn = new JButton("Принять");
        acceptBtn.addActionListener(e -> acceptSelected());
        JButton declineBtn = new JButton("Отклонить");
        declineBtn.addActionListener(e -> declineSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(declineBtn);
        actions.add(acceptBtn);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    // =============== ВКЛАДКА «ИСХОДЯЩИЕ» ===============

    private JPanel buildOutgoingTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        outgoingTable.setModel(new DefaultTableModel(
                new Object[]{"Имя", "Отправлена"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        outgoingTable.setRowHeight(24);
        p.add(new JScrollPane(outgoingTable), BorderLayout.CENTER);

        JButton cancelBtn = new JButton("Отменить заявку");
        cancelBtn.addActionListener(e -> cancelSelectedRequest());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancelBtn);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    // =============== ВКЛАДКА «ПОИСК» ===============

    private JPanel searchResultPanel;

    private JPanel buildSearchTab() {
        JPanel p = new JPanel(new BorderLayout(6, 6));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Имя игрока:"));
        top.add(searchField);
        JButton findBtn = new JButton("Найти");
        findBtn.addActionListener(e -> doSearch());
        top.add(findBtn);
        p.add(top, BorderLayout.NORTH);

        JPanel resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
        resultPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(resultPanel, BorderLayout.CENTER);

        // запомним ссылку, чтобы наполнять при поиске
        this.searchResultPanel = resultPanel;
        return p;
    }

    private void doSearch() {
        String name = searchField.getText().trim();
        if (name.isEmpty()) return;
        if (!ensureOnline()) return;

        searchResultPanel.removeAll();
        searchResultPanel.add(new JLabel("Поиск..."));
        searchResultPanel.revalidate();
        searchResultPanel.repaint();

        client.findUser(name).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    searchResultPanel.removeAll();
                    if (err != null) {
                        searchResultPanel.add(new JLabel("Ошибка: " + err.getMessage()));
                    } else if (!resp.found) {
                        searchResultPanel.add(new JLabel(resp.message));
                    } else {
                        searchResultPanel.add(buildSearchResultRow(resp));
                    }
                    searchResultPanel.revalidate();
                    searchResultPanel.repaint();
                }));
    }

    private JPanel buildSearchResultRow(FindUserResponse r) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("ID " + r.userId + " — " + r.username + " — "));

        switch (r.relation) {
            case "SELF" -> row.add(new JLabel("(это вы)"));
            case "FRIEND" -> row.add(new JLabel("Уже друзья"));
            case "PENDING_OUT" -> row.add(new JLabel("Заявка уже отправлена"));
            case "PENDING_IN" -> {
                row.add(new JLabel("Прислал вам заявку. "));
                JButton accept = new JButton("Принять");
                accept.addActionListener(e -> acceptFromSearch(r.userId));
                row.add(accept);
            }
            case "NONE" -> {
                JButton add = new JButton("Добавить в друзья");
                add.addActionListener(e -> addFromSearch(r.userId));
                row.add(add);
            }
            default -> row.add(new JLabel("Неизвестное состояние"));
        }
        return row;
    }

    private void addFromSearch(long userId) {
        if (!ensureOnline()) return;
        client.addFriend(userId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Ошибка: " + err.getMessage(),
                                "Друзья", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, resp.message,
                                resp.success ? "OK" : "Ошибка",
                                resp.success ? JOptionPane.INFORMATION_MESSAGE
                                        : JOptionPane.ERROR_MESSAGE);
                        if (resp.success) doSearch();
                    }
                }));
    }

    private void acceptFromSearch(long userId) {
        if (!ensureOnline()) return;
        client.acceptFriend(userId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        JOptionPane.showMessageDialog(this,
                                "Ошибка: " + err.getMessage(),
                                "Друзья", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, resp.message);
                        doSearch();
                        refresh();
                    }
                }));
    }

    // =============== ОБНОВЛЕНИЕ СПИСКОВ ===============

    private void refresh() {
        if (!ensureOnline()) return;
        statusLabel.setText("Загрузка...");
        client.getFriends().whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null) {
                        statusLabel.setText("Ошибка: " + err.getMessage());
                        return;
                    }
                    if (!resp.success) {
                        statusLabel.setText(resp.message);
                        return;
                    }
                    cachedFriends = resp.friends;
                    fillTables(resp.friends);
                    int incoming = 0;
                    for (var fi : resp.friends) {
                        if ("PENDING_IN".equals(fi.status)) incoming++;
                    }
                    statusLabel.setText("Заявок в друзья: " + incoming);
                }));
    }

    private void fillTables(List<GetFriendsResponse.FriendInfo> list) {
        DefaultTableModel friendModel = (DefaultTableModel) friendsTable.getModel();
        DefaultTableModel incomingModel = (DefaultTableModel) incomingTable.getModel();
        DefaultTableModel outgoingModel = (DefaultTableModel) outgoingTable.getModel();
        friendModel.setRowCount(0);
        incomingModel.setRowCount(0);
        outgoingModel.setRowCount(0);

        for (var fi : list) {
            String since = fi.since == null ? "—" : fi.since.replace('T', ' ');
            switch (fi.status) {
                case "ACCEPTED" -> friendModel.addRow(new Object[]{fi.username, since});
                case "PENDING_IN" -> incomingModel.addRow(new Object[]{fi.username, since});
                case "PENDING_OUT" -> outgoingModel.addRow(new Object[]{fi.username, since});
            }
        }
    }

    // =============== ДЕЙСТВИЯ ===============

    /** id другого игрока из кэша по имени и статусу. */
    private long idFrom(List<String> statuses, JTable table) {
        int row = table.getSelectedRow();
        if (row < 0 || cachedFriends == null) return -1;
        String name = (String) table.getValueAt(row, 0);
        for (var fi : cachedFriends) {
            if (statuses.contains(fi.status) && fi.username.equals(name)) {
                return fi.userId;
            }
        }
        return -1;
    }

    private void removeSelectedFriend() {
        long otherId = idFrom(List.of("ACCEPTED"), friendsTable);
        if (otherId < 0) return;
        if (!ensureOnline()) return;

        String name = (String) friendsTable.getValueAt(friendsTable.getSelectedRow(), 0);
        int ok = JOptionPane.showConfirmDialog(this,
                "Удалить " + name + " из друзей?",
                "Подтверждение", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        client.removeFriend(otherId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                    } else {
                        refresh();
                    }
                }));
    }

    private void acceptSelected() {
        long otherId = idFrom(List.of("PENDING_IN"), incomingTable);
        if (otherId < 0) return;
        if (!ensureOnline()) return;

        client.acceptFriend(otherId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                    } else {
                        refresh();
                    }
                }));
    }

    private void declineSelected() {
        long otherId = idFrom(List.of("PENDING_IN"), incomingTable);
        if (otherId < 0) return;
        if (!ensureOnline()) return;

        client.declineFriend(otherId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                    } else {
                        refresh();
                    }
                }));
    }

    /** Отмена своей исходящей заявки — та же кнопка, что и удаление, но kind=OUTGOING. */
    private void cancelSelectedRequest() {
        long otherId = idFrom(List.of("PENDING_OUT"), outgoingTable);
        if (otherId < 0) return;
        if (!ensureOnline()) return;

        String name = (String) outgoingTable.getValueAt(outgoingTable.getSelectedRow(), 0);
        int ok = JOptionPane.showConfirmDialog(this,
                "Отменить заявку игроку " + name + "?",
                "Подтверждение", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        client.cancelFriendRequest(otherId).whenComplete((resp, err) ->
                SwingUtilities.invokeLater(() -> {
                    if (err != null || !resp.success) {
                        JOptionPane.showMessageDialog(this,
                                err != null ? err.getMessage() : resp.message,
                                "Ошибка", JOptionPane.ERROR_MESSAGE);
                    } else {
                        refresh();
                    }
                }));
    }
}
