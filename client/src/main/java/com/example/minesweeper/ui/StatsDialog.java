package com.example.minesweeper.ui;

import com.example.minesweeper.protocol.payload.MyStatsResponse;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Диалог личной статистики. Показывает сводку по сложностям,
 * историю партий и график времени.
 */
public class StatsDialog extends JDialog {

    public StatsDialog(Frame owner, MyStatsResponse stats) {
        super(owner, "Моя статистика", true);
        setLayout(new BorderLayout(8, 8));
        setSize(820, 600);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Сводка", buildSummaryPanel(stats));
        tabs.addTab("История и график", buildHistoryPanel(stats));
        add(tabs, BorderLayout.CENTER);

        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);
        add(bottom, BorderLayout.SOUTH);
    }

    // ============== ВКЛАДКА «СВОДКА» ==============

    private JPanel buildSummaryPanel(MyStatsResponse stats) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        List<MyStatsResponse.DifficultyStats> byDiff = stats.byDifficulty == null
                ? Collections.emptyList() : stats.byDifficulty;

        Map<String, int[]> byMode = modeTotalsOf(stats);
        int[] classic = byMode.getOrDefault("CLASSIC", new int[]{0, 0});
        int[] timed = byMode.getOrDefault("TIMED", new int[]{0, 0});

        // Три ряда карточек: Общий, Классика, На время
        JPanel cards = new JPanel(new GridLayout(3, 1, 0, 8));
        cards.add(cardRow("Все режимы", stats.totalGames, stats.totalWins));
        cards.add(cardRow("Классика", classic[0], classic[1]));
        cards.add(cardRow("На время", timed[0], timed[1]));
        panel.add(cards, BorderLayout.NORTH);

        // ===== Две таблицы — классика и timed =====
        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 0));
        tables.add(buildModeTable("Классика", "CLASSIC", byDiff));
        tables.add(buildModeTable("На время", "TIMED", byDiff));
        panel.add(tables, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Итоги по режимам: {партий, побед} для CLASSIC и TIMED.
     *
     * Приоритет — серверное поле modeTotals. Если сервер старый и не прислал его,
     * складываем локально из byDifficulty (там mode есть в каждой строке).
     * Если нет и его — останутся нули, но строка «Все режимы» покажет верную сумму.
     */
    static Map<String, int[]> modeTotalsOf(MyStatsResponse stats) {
        Map<String, int[]> byMode = new LinkedHashMap<>();
        byMode.put("CLASSIC", new int[]{0, 0});
        byMode.put("TIMED", new int[]{0, 0});

        if (stats.modeTotals != null && !stats.modeTotals.isEmpty()) {
            for (var mt : stats.modeTotals) {
                if (mt.mode == null) continue;
                int[] slot = byMode.computeIfAbsent(mt.mode, k -> new int[]{0, 0});
                slot[0] += mt.totalGames;
                slot[1] += mt.totalWins;
            }
        } else if (stats.byDifficulty != null) {
            for (var d : stats.byDifficulty) {
                if (d.mode == null) continue;
                int[] slot = byMode.computeIfAbsent(d.mode, k -> new int[]{0, 0});
                slot[0] += d.totalGames;
                slot[1] += d.wins;
            }
        }
        return byMode;
    }

    /** Один ряд из четырёх карточек: название режима, партии, победы, процент. */
    private JPanel cardRow(String title, int games, int wins) {
        JPanel row = new JPanel(new GridLayout(1, 4, 8, 8));

        // Название режима — узкая карточка
        JPanel titleCard = new JPanel(new BorderLayout());
        titleCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        JLabel l = new JLabel(title, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        titleCard.add(l, BorderLayout.CENTER);
        row.add(titleCard);

        row.add(card("Партий", String.valueOf(games)));
        row.add(card("Побед", String.valueOf(wins)));

        int rate = games == 0 ? 0 : (int) Math.round(100.0 * wins / games);
        row.add(card("Процент побед", rate + "%"));

        return row;
    }

    /** Таблица сложностей для одного режима. */
    private JPanel buildModeTable(String title, String mode,
                                  List<MyStatsResponse.DifficultyStats> all) {
        JPanel wrap = new JPanel(new BorderLayout(4, 4));
        JLabel header = new JLabel(title, SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        wrap.add(header, BorderLayout.NORTH);

        String[] cols = {"Сложность", "Партий", "Побед", "Лучшее"};
        List<MyStatsResponse.DifficultyStats> filtered = all.stream()
                .filter(d -> mode.equals(d.mode))
                .toList();
        Object[][] data = new Object[filtered.size()][4];
        for (int i = 0; i < filtered.size(); i++) {
            var d = filtered.get(i);
            data[i][0] = localizedDifficulty(d.difficulty);
            data[i][1] = d.totalGames;
            data[i][2] = d.wins;
            data[i][3] = d.bestTimeSeconds != null
                    ? d.bestTimeSeconds + " сек." : "—";
        }
        if (data.length == 0) {
            data = new Object[][]{{"—", "—", "—", "—"}};
        }

        JTable table = new JTable(new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setRowHeight(24);
        wrap.add(new JScrollPane(table), BorderLayout.CENTER);

        return wrap;
    }

    private JPanel card(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 28f));
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setForeground(Color.GRAY);
        p.add(v, BorderLayout.CENTER);
        p.add(l, BorderLayout.SOUTH);
        return p;
    }

    // ============== ВКЛАДКА «ИСТОРИЯ + ГРАФИК» ==============

    private JPanel buildHistoryPanel(MyStatsResponse stats) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        List<MyStatsResponse.GameEntry> games = stats.recentGames == null
                ? Collections.emptyList() : stats.recentGames;

        // График сверху
        JFreeChart chart = buildChart(games);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(760, 240));

        JPanel chartBox = new JPanel(new BorderLayout());
        JLabel chartHint = new JLabel(
                "На графике — только победы (оба режима), по одной линии на сложность. "
                        + "Проигрыши и режим видны в таблице ниже.");
        chartHint.setForeground(Color.GRAY);
        chartHint.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
        chartBox.add(chartHint, BorderLayout.NORTH);
        chartBox.add(chartPanel, BorderLayout.CENTER);
        panel.add(chartBox, BorderLayout.NORTH);

        // Таблица снизу
        String[] cols = {"Сложность", "Режим", "Время", "Результат", "Когда"};
        Object[][] data = new Object[games.size()][5];
        for (int i = 0; i < games.size(); i++) {
            var g = games.get(i);
            data[i][0] = localizedDifficulty(g.difficulty);
            data[i][1] = "TIMED".equals(g.mode) ? "На время" : "Классика";
            data[i][2] = g.durationSeconds + " сек.";
            data[i][3] = g.win ? "Победа" : "Проигрыш";
            data[i][4] = g.playedAt == null ? "—" : g.playedAt.replace('T', ' ');
        }
        JTable table = new JTable(new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setRowHeight(22);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    /**
     * Строит линейный график времени победных партий — по одной линии на сложность.
     * Проигрыши на графике не отображаются — они остаются в таблице.
     *
     * По X — номер победы по счёту внутри этой сложности (1, 2, 3...),
     * по Y — время в секундах. Сложности развиваются независимо, поэтому
     * у каждой линии своя нумерация.
     */
    private JFreeChart buildChart(List<MyStatsResponse.GameEntry> games) {
        // Идём от старых к новым, чтобы график читался слева направо
        List<MyStatsResponse.GameEntry> reversed = new java.util.ArrayList<>(games);
        Collections.reverse(reversed);

        XYSeries easy = new XYSeries("Новичок");
        XYSeries medium = new XYSeries("Любитель");
        XYSeries hard = new XYSeries("Профессионал");

        int idxEasy = 0, idxMedium = 0, idxHard = 0;
        for (MyStatsResponse.GameEntry g : reversed) {
            if (!g.win) continue;             // пропускаем проигрыши
            switch (g.difficulty) {
                case "EASY"   -> easy.add(++idxEasy, g.durationSeconds);
                case "MEDIUM" -> medium.add(++idxMedium, g.durationSeconds);
                case "HARD"   -> hard.add(++idxHard, g.durationSeconds);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        // Пустые серии не добавляем, иначе в легенде будет лишняя строка
        if (!easy.isEmpty())   dataset.addSeries(easy);
        if (!medium.isEmpty()) dataset.addSeries(medium);
        if (!hard.isEmpty())   dataset.addSeries(hard);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Время победных партий по сложностям",
                "Номер победы в этой сложности",
                "Секунды",
                dataset,
                PlotOrientation.VERTICAL,
                true,    // легенда — серий несколько
                true,
                false);

        chart.setAntiAlias(true);
        return chart;
    }

    private static String localizedDifficulty(String d) {
        if (d == null) return "—";
        return switch (d) {
            case "EASY" -> "Новичок";
            case "MEDIUM" -> "Любитель";
            case "HARD" -> "Профессионал";
            default -> d;
        };
    }
}
