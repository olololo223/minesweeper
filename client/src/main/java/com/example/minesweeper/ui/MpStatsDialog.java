package com.example.minesweeper.ui;

import com.example.minesweeper.protocol.payload.MpStatsResponse;

import javax.swing.*;
import java.awt.*;

public class MpStatsDialog extends JDialog {

    public MpStatsDialog(Frame owner, MpStatsResponse stats) {
        super(owner, "Статистика мультиплеера", true);
        setSize(420, 380);
        setLocationRelativeTo(owner);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        if (stats.totalGames == 0) {
            panel.add(new JLabel("Вы ещё не играли в мультиплеер."));
        } else {
            panel.add(cardRow("Всего партий", String.valueOf(stats.totalGames)));
            panel.add(Box.createVerticalStrut(8));
            panel.add(cardRow("Побед (команда)", String.valueOf(stats.totalWins)));
            panel.add(Box.createVerticalStrut(8));
            panel.add(cardRow("Суммарный счёт", String.valueOf(stats.totalScore)));
            panel.add(Box.createVerticalStrut(8));
            panel.add(cardRow("Лучшая партия", stats.bestScore + " очк."));
            panel.add(Box.createVerticalStrut(8));
            panel.add(cardRow("Клеток открыто всего", String.valueOf(stats.totalRevealed)));
            panel.add(Box.createVerticalStrut(8));
            panel.add(cardRow("В среднем за партию",
                    String.format("%.1f", stats.avgRevealed) + " клеток"));
        }

        add(new JScrollPane(panel), BorderLayout.CENTER);

        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel cardRow(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel l = new JLabel(label);
        JLabel v = new JLabel(value, SwingConstants.RIGHT);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 14f));
        p.add(l, BorderLayout.WEST);
        p.add(v, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return p;
    }
}