package com.example.minesweeper.ui;

import com.example.minesweeper.net.GameClient;
import com.example.minesweeper.protocol.payload.LoginResponse;
import com.example.minesweeper.protocol.payload.RegisterResponse;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class LoginDialog extends JDialog {
    private final JTextField usernameField = new JTextField("player", 14);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JTextField hostField;
    private final JTextField portField;
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton loginButton = new JButton("Войти");
    private final JButton registerButton = new JButton("Регистрация");
    private final JButton offlineButton = new JButton("Играть без сети");
    private final JCheckBox tlsCheck = new JCheckBox("TLS");
    private final JTextField certField = new JTextField("server-cert.pem", 14);

    private GameClient client;

    public LoginDialog(Frame owner) {
        super(owner, "Вход в игру", true);

        Properties p = loadClientProps();
        hostField = new JTextField(p.getProperty("server.host", "localhost"), 14);
        portField = new JTextField(p.getProperty("server.port", "9000"), 6);

        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Имя игрока:"), c);
        c.gridx = 1; form.add(usernameField, c);

        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Пароль:"), c);
        c.gridx = 1; form.add(passwordField, c);

        c.gridx = 0; c.gridy = 2; form.add(new JLabel("Сервер:"), c);
        c.gridx = 1; form.add(hostField, c);

        c.gridx = 0; c.gridy = 3; form.add(new JLabel("Порт:"), c);
        c.gridx = 1; form.add(portField, c);

        c.gridx = 0; c.gridy = 4;
        form.add(new JLabel("TLS:"), c);
        c.gridx = 1;
        form.add(tlsCheck, c);

        c.gridx = 0; c.gridy = 5;
        form.add(new JLabel("Сертификат:"), c);
        c.gridx = 1;
        form.add(certField, c);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(offlineButton);
        buttons.add(registerButton);
        buttons.add(loginButton);
        add(buttons, BorderLayout.SOUTH);

        statusLabel.setForeground(Color.RED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        add(statusLabel, BorderLayout.NORTH);

        loginButton.addActionListener(e -> doLogin());
        registerButton.addActionListener(e -> doRegister());
        offlineButton.addActionListener(e -> {
            client = null;
            dispose();
        });

        getRootPane().setDefaultButton(loginButton);

        pack();
        setLocationRelativeTo(owner);
    }

    public GameClient getClient() { return client; }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String host = hostField.getText().trim();
        int port;

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Введите имя и пароль");
            return;
        }
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Некорректный порт");
            return;
        }

        setButtons(false);
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Подключаемся...");

        new SwingWorker<GameClient, Void>() {
            @Override protected GameClient doInBackground() throws Exception {
                GameClient c = new GameClient(host, port,
                        tlsCheck.isSelected(), certField.getText().trim());
                c.connect().get(3, TimeUnit.SECONDS);
                LoginResponse resp = c.login(username, password).get(3, TimeUnit.SECONDS);
                if (!resp.success) {
                    c.close();
                    throw new RuntimeException(resp.message);
                }
                return c;
            }
            @Override protected void done() {
                try {
                    client = get();
                    dispose();
                } catch (Exception ex) {
                    showError(unwrap(ex));
                }
            }
        }.execute();
    }

    private void doRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String host = hostField.getText().trim();
        int port;

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Введите имя и пароль");
            return;
        }
        if (password.length() < 4) {
            statusLabel.setText("Пароль минимум 4 символа");
            return;
        }
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Некорректный порт");
            return;
        }

        setButtons(false);
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Регистрируемся...");

        new SwingWorker<GameClient, Void>() {
            @Override protected GameClient doInBackground() throws Exception {
                GameClient c = new GameClient(host, port,
                        tlsCheck.isSelected(), certField.getText().trim());
                c.connect().get(3, TimeUnit.SECONDS);
                RegisterResponse r = c.register(username, password).get(3, TimeUnit.SECONDS);
                if (!r.success) {
                    c.close();
                    throw new RuntimeException(r.message);
                }
                // Сразу логинимся
                LoginResponse lr = c.login(username, password).get(3, TimeUnit.SECONDS);
                if (!lr.success) {
                    c.close();
                    throw new RuntimeException(lr.message);
                }
                return c;
            }
            @Override protected void done() {
                try {
                    client = get();
                    dispose();
                } catch (Exception ex) {
                    showError(unwrap(ex));
                }
            }
        }.execute();
    }

    private void setButtons(boolean enabled) {
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        offlineButton.setEnabled(enabled);
    }

    private void showError(Throwable cause) {
        statusLabel.setForeground(Color.RED);
        statusLabel.setText("Ошибка: " + cause.getMessage());
        setButtons(true);
    }

    private static Throwable unwrap(Exception ex) {
        Throwable cause = ex.getCause();
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        return cause != null ? cause : ex;
    }

    private static Properties loadClientProps() {
        Properties p = new Properties();
        try (InputStream in = LoginDialog.class.getClassLoader()
                .getResourceAsStream("client.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {}
        return p;
    }
}