package com.mssh;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mssh.data.MsshDatabaseHelper;
import com.mssh.data.CredentialCipher;
import com.mssh.data.SshHost;
import com.mssh.data.SshHostRepository;
import com.mssh.data.SshLogRepository;
import com.mssh.logging.SshLogWriter;
import com.mssh.localcmd.LocalCommandController;
import com.mssh.sftp.SftpController;
import com.mssh.sftp.SftpEntry;
import com.mssh.sftp.SftpStateListener;
import com.mssh.ssh.JschSshClient;
import com.mssh.ssh.SshConnectionConfig;
import com.mssh.terminal.TerminalController;
import com.mssh.terminal.TerminalStateListener;
import com.mssh.ui.TerminalLayoutSizer;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private static final boolean SHOW_SFTP = false;
    private static final String STATE_LANDSCAPE_MODE = "landscape_mode";

    private SshHostRepository hostRepository;
    private TerminalController terminalController;
    private LocalCommandController localCommandController;
    private SftpController sftpController;
    private LinearLayout root;
    private TextView terminalText;
    private ScrollView terminalScroll;
    private LinearLayout sftpList;
    private TextView sftpPath;
    private Button logButton;
    private TextView statusText;
    private EditText activeCommandInput;
    private SshHost activeHost;
    private boolean localMode;
    private boolean landscapeMode;
    private int lastTerminalColumns = -1;
    private int lastTerminalRows = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        landscapeMode = savedInstanceState != null && savedInstanceState.getBoolean(STATE_LANDSCAPE_MODE, false);
        applyRequestedOrientation();

        MsshDatabaseHelper dbHelper = new MsshDatabaseHelper(this);
        hostRepository = new SshHostRepository(dbHelper, new CredentialCipher());
        SshLogRepository logRepository = new SshLogRepository(dbHelper);
        terminalController = new TerminalController(
                new JschSshClient(new java.io.File(getFilesDir(), "known_hosts")),
                new SshLogWriter(this, logRepository)
        );
        localCommandController = new LocalCommandController(this, new SshLogWriter(this, logRepository));
        sftpController = new SftpController(new java.io.File(getFilesDir(), "known_hosts"));

        requestLegacyStoragePermissionIfNeeded();
        showHostList();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_LANDSCAPE_MODE, landscapeMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        terminalController.disconnect();
        localCommandController.close();
        sftpController.disconnect();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (terminalText != null) {
            closeActiveTerminal();
            showHostList();
        } else if (sftpList != null) {
            closeSftp();
            showHostList();
        } else {
            super.onBackPressed();
        }
    }

    private void showHostList() {
        activeHost = null;
        localMode = false;
        terminalText = null;
        sftpList = null;
        root = baseRoot();

        LinearLayout header = row();
        TextView title = title("MSSH");
        TextView subtitle = small("Mobile SSH");
        LinearLayout titleBox = column();
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button addButton = actionButton("+");
        addButton.setOnClickListener(v -> showHostEditor(null));
        header.addView(addButton, fixedButtonParams());

        Button localButton = actionButton("cmd");
        localButton.setTextSize(12);
        localButton.setOnClickListener(v -> showLocalTerminal());
        header.addView(localButton, fixedButtonParams());

        Button orientationButton = actionButton(landscapeMode ? "竖版" : "横版");
        orientationButton.setTextSize(12);
        orientationButton.setOnClickListener(v -> toggleMainOrientation());
        header.addView(orientationButton, fixedButtonParams());
        root.addView(header);

        List<SshHost> hosts = hostRepository.listHosts();
        if (hosts.isEmpty()) {
            TextView empty = small("No hosts yet. Tap + to add one.");
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1
            ));
        } else {
            LinearLayout list = column();
            for (SshHost host : hosts) {
                list.addView(hostRow(host));
            }
            root.addView(list, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        setContentView(root);
    }

    private View hostRow(SshHost host) {
        LinearLayout row = row();
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(panelBackground());

        LinearLayout texts = column();
        TextView name = label(host.displayName());
        TextView address = small(host.protocolOrDefault() + " · " + host.username + "@" + host.hostname + ":" + host.port);
        texts.addView(name);
        texts.addView(address);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button connect = actionButton(">");
        connect.setOnClickListener(v -> askPasswordAndConnect(host));
        row.addView(connect, fixedButtonParams());

        Button edit = actionButton("edit");
        edit.setTextSize(12);
        edit.setOnClickListener(v -> showHostEditor(host));
        row.addView(edit, fixedButtonParams());

        Button delete = actionButton("del");
        delete.setTextSize(12);
        delete.setOnClickListener(v -> confirmDelete(host));
        row.addView(delete, fixedButtonParams());

        LinearLayout wrap = column();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        wrap.addView(row, params);
        return wrap;
    }

    private void showHostEditor(SshHost existing) {
        LinearLayout form = column();
        form.setPadding(dp(16), dp(8), dp(16), dp(12));
        form.setBackgroundColor(color(R.color.mssh_form_background));

        EditText name = input("Name");
        Spinner protocol = protocolSpinner();
        EditText hostname = input("Host / IP");
        EditText port = input("Port");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText username = input("Username");
        EditText password = input("Password");
        CheckBox savePassword = checkBox("保存 SSH 密码");
        ImageButton passwordToggle = iconButton();
        setPasswordVisible(password, passwordToggle, false);
        final boolean[] passwordVisible = {false};
        passwordToggle.setOnClickListener(v -> {
            passwordVisible[0] = !passwordVisible[0];
            setPasswordVisible(password, passwordToggle, passwordVisible[0]);
        });
        LinearLayout passwordRow = row();
        passwordRow.addView(password, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        passwordRow.addView(passwordToggle, fixedButtonParams());

        if (existing != null) {
            name.setText(existing.name);
            protocol.setSelection(SHOW_SFTP && "SFTP".equals(existing.protocolOrDefault()) ? 1 : 0);
            hostname.setText(existing.hostname);
            port.setText(String.valueOf(existing.port));
            username.setText(existing.username);
            password.setText(existing.password);
            savePassword.setChecked(existing.savePassword);
        } else {
            port.setText("22");
        }

        form.addView(formField("名称", name));
        form.addView(formField("协议", protocol));
        form.addView(formField("主机 / IP", hostname));
        form.addView(formField("端口", port));
        form.addView(formField("用户名", username));
        form.addView(formField("密码", passwordRow));
        form.addView(formField("保存密码", savePassword));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add Host" : "Edit Host")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                SshHost host = existing == null ? new SshHost() : existing;
                host.name = name.getText().toString();
                host.protocol = protocol.getSelectedItem().toString();
                host.hostname = hostname.getText().toString();
                host.port = parsePort(port.getText().toString());
                host.username = username.getText().toString();
                host.password = password.getText().toString();
                host.savePassword = savePassword.isChecked();
                host.authType = "PASSWORD";
                if (host.hostname.trim().isEmpty() || host.username.trim().isEmpty()) {
                    toast("Host and username are required.");
                    return;
                }
                if (host.savePassword && host.password.trim().isEmpty()) {
                    toast("勾选保存密码时请输入密码。");
                    return;
                }
                hostRepository.save(host);
                dialog.dismiss();
                showHostList();
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(color(R.color.mssh_form_background)));
        }
    }

    private void askPasswordAndConnect(SshHost host) {
        if (host.savePassword && host.password != null && !host.password.isEmpty()) {
            connectWithPassword(host, host.password);
            return;
        }
        EditText password = input("Password");
        ImageButton passwordToggle = iconButton();
        setPasswordVisible(password, passwordToggle, false);
        final boolean[] passwordVisible = {false};
        passwordToggle.setOnClickListener(v -> {
            passwordVisible[0] = !passwordVisible[0];
            setPasswordVisible(password, passwordToggle, passwordVisible[0]);
        });
        LinearLayout passwordRow = row();
        passwordRow.setPadding(dp(16), dp(8), dp(16), 0);
        passwordRow.addView(password, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        passwordRow.addView(passwordToggle, fixedButtonParams());
        new AlertDialog.Builder(this)
                .setTitle("Connect " + host.displayName())
                .setView(passwordRow)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (dialog, which) -> {
                    connectWithPassword(host, password.getText().toString());
                })
                .show();
    }

    private void connectWithPassword(SshHost host, String password) {
        if ("SFTP".equals(host.protocolOrDefault())) {
            if (!SHOW_SFTP) {
                toast("SFTP 功能当前隐藏。");
                return;
            }
            showSftp(host);
            sftpController.connect(new SshConnectionConfig(
                    host.id,
                    host.hostname,
                    host.port,
                    host.username,
                    password
            ));
            return;
        }
        showTerminal(host);
        terminalController.connect(new SshConnectionConfig(
                host.id,
                host.hostname,
                host.port,
                host.username,
                password
        ));
    }

    private void showSftp(SshHost host) {
        activeHost = host;
        root = baseRoot();

        LinearLayout top = row();
        Button back = actionButton("<");
        back.setOnClickListener(v -> {
            closeSftp();
            showHostList();
        });
        top.addView(back, fixedButtonParams());

        LinearLayout titleBox = column();
        titleBox.addView(label("SFTP " + host.displayName()));
        statusText = small("connecting");
        titleBox.addView(statusText);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button up = actionButton("up");
        up.setTextSize(12);
        up.setOnClickListener(v -> sftpController.up());
        top.addView(up, fixedButtonParams());

        Button refresh = actionButton("ref");
        refresh.setTextSize(12);
        refresh.setOnClickListener(v -> sftpController.refresh());
        top.addView(refresh, fixedButtonParams());
        root.addView(top);

        sftpPath = small("/");
        sftpPath.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(sftpPath);

        sftpList = column();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(color(R.color.mssh_black));
        scrollView.addView(sftpList);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        sftpController.setStateListener(new SftpStateListener() {
            @Override
            public void onStatusChanged(String status) {
                statusText.setText(status);
            }

            @Override
            public void onDirectoryChanged(String path, List<SftpEntry> entries) {
                renderSftpEntries(path, entries);
            }

            @Override
            public void onError(String message, Throwable throwable) {
                toast("SFTP: " + message);
            }
        });
    }

    private void renderSftpEntries(String path, List<SftpEntry> entries) {
        sftpPath.setText(path);
        sftpList.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = small("目录为空");
            empty.setGravity(Gravity.CENTER);
            sftpList.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            return;
        }
        for (SftpEntry entry : entries) {
            TextView row = label((entry.directory ? "[D] " : "[F] ") + entry.name + "  " + formatSize(entry.size));
            row.setBackground(panelBackground());
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(8));
            if (entry.directory) {
                row.setOnClickListener(v -> sftpController.changeDirectory(entry.name));
            }
            sftpList.addView(row, params);
        }
    }

    private void closeSftp() {
        sftpController.setStateListener(null);
        sftpController.disconnect();
    }

    private void confirmDelete(SshHost host) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Host")
                .setMessage("Delete " + host.displayName() + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    hostRepository.delete(host.id);
                    showHostList();
                })
                .show();
    }

    private void showTerminal(SshHost host) {
        activeHost = host;
        localMode = false;
        lastTerminalColumns = -1;
        lastTerminalRows = -1;
        root = baseRoot();

        LinearLayout top = row();
        Button back = actionButton("<");
        back.setOnClickListener(v -> {
            closeActiveTerminal();
            showHostList();
        });
        top.addView(back, fixedButtonParams());

        LinearLayout titleBox = column();
        titleBox.addView(label(host.displayName()));
        statusText = small("connecting");
        titleBox.addView(statusText);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        logButton = actionButton("log");
        logButton.setOnClickListener(v -> terminalController.toggleLogging());
        top.addView(logButton, fixedButtonParams());
        root.addView(top);

        terminalText = new TextView(this);
        terminalText.setTextColor(color(R.color.mssh_green));
        terminalText.setTextSize(14);
        terminalText.setTypeface(Typeface.MONOSPACE);
        terminalText.setTextIsSelectable(true);
        terminalText.setFocusable(false);
        terminalText.setFocusableInTouchMode(false);
        terminalText.setPadding(dp(10), dp(10), dp(10), dp(10));
        terminalText.setGravity(Gravity.START | Gravity.TOP);

        terminalScroll = new ScrollView(this);
        terminalScroll.setBackgroundColor(color(R.color.mssh_black));
        terminalScroll.addView(terminalText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(terminalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        terminalScroll.addOnLayoutChangeListener((v, left, viewTop, right, bottom,
                                                  oldLeft, oldTop, oldRight, oldBottom) ->
                updateSshTerminalSize());

        root.addView(shortcutBar());
        root.addView(commandBar());
        setContentView(root);
        terminalScroll.post(this::updateSshTerminalSize);

        terminalController.setStateListener(new TerminalStateListener() {
            @Override
            public void onTextChanged(String text) {
                terminalText.setText(text);
                scrollTerminalToBottom();
                if (activeCommandInput != null) {
                    focusCommandInput(activeCommandInput);
                }
            }

            @Override
            public void onStatusChanged(String status) {
                statusText.setText(status);
            }

            @Override
            public void onLogStateChanged(boolean recording, String path) {
                logButton.setText(recording ? "log*" : "log");
                logButton.setTextColor(recording ? color(R.color.mssh_black) : color(R.color.mssh_yellow));
                logButton.setBackground(buttonBackground(recording ? color(R.color.mssh_yellow) : color(R.color.mssh_surface)));
            }
        });
    }

    private void showLocalTerminal() {
        activeHost = null;
        localMode = true;
        lastTerminalColumns = -1;
        lastTerminalRows = -1;
        root = baseRoot();

        LinearLayout top = row();
        Button back = actionButton("<");
        back.setOnClickListener(v -> {
            closeActiveTerminal();
            showHostList();
        });
        top.addView(back, fixedButtonParams());

        LinearLayout titleBox = column();
        titleBox.addView(label("本地 CMD"));
        statusText = small("local");
        titleBox.addView(statusText);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        logButton = actionButton("log");
        logButton.setOnClickListener(v -> localCommandController.toggleLogging());
        top.addView(logButton, fixedButtonParams());
        root.addView(top);

        terminalText = new TextView(this);
        terminalText.setTextColor(color(R.color.mssh_green));
        terminalText.setTextSize(14);
        terminalText.setTypeface(Typeface.MONOSPACE);
        terminalText.setTextIsSelectable(true);
        terminalText.setFocusable(false);
        terminalText.setFocusableInTouchMode(false);
        terminalText.setPadding(dp(10), dp(10), dp(10), dp(10));
        terminalText.setGravity(Gravity.START | Gravity.TOP);

        terminalScroll = new ScrollView(this);
        terminalScroll.setBackgroundColor(color(R.color.mssh_black));
        terminalScroll.addView(terminalText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(terminalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(shortcutBar());
        root.addView(commandBar());
        setContentView(root);

        localCommandController.setStateListener(new TerminalStateListener() {
            @Override
            public void onTextChanged(String text) {
                terminalText.setText(text);
                scrollTerminalToBottom();
                if (activeCommandInput != null) {
                    focusCommandInput(activeCommandInput);
                }
            }

            @Override
            public void onStatusChanged(String status) {
                statusText.setText(status);
            }

            @Override
            public void onLogStateChanged(boolean recording, String path) {
                logButton.setText(recording ? "log*" : "log");
                logButton.setTextColor(recording ? color(R.color.mssh_black) : color(R.color.mssh_yellow));
                logButton.setBackground(buttonBackground(recording ? color(R.color.mssh_yellow) : color(R.color.mssh_surface)));
            }
        });
    }

    private View shortcutBar() {
        LinearLayout bar = row();
        addShortcut(bar, "Esc", "\u001B");
        addShortcut(bar, "Tab", "\t");
        addShortcut(bar, "^C", "\u0003");
        addShortcut(bar, "Up", "\u001B[A");
        addShortcut(bar, "Down", "\u001B[B");
        addShortcut(bar, "Left", "\u001B[D");
        addShortcut(bar, "Right", "\u001B[C");
        return bar;
    }

    private void addShortcut(LinearLayout bar, String label, String value) {
        Button button = actionButton(label);
        button.setTextSize(12);
        button.setOnClickListener(v -> {
            if (localMode) {
                localCommandController.sendRaw(value);
            } else {
                sendSshShortcut(value);
            }
        });
        bar.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1));
    }

    private View commandBar() {
        LinearLayout bar = row();
        EditText command = input("Command");
        activeCommandInput = command;
        command.setSingleLine(true);
        command.setImeOptions(EditorInfo.IME_ACTION_SEND);
        final boolean[] bridgeUpdating = {false};
        if (!localMode) {
            command.setHint("Keyboard");
            command.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                String shortcut = shortcutForKeyCode(keyCode);
                if (shortcut != null) {
                    sendSshShortcut(shortcut);
                    return true;
                }
                if (terminalController.isAlternateScreen() && keyCode == KeyEvent.KEYCODE_DEL) {
                    sendSshShortcut("\u007F");
                    focusCommandInput(command);
                    return true;
                }
                return false;
            });
            command.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (bridgeUpdating[0] || count <= 0) {
                        return;
                    }
                    if (!terminalController.isAlternateScreen()) {
                        return;
                    }
                    terminalController.sendRaw(s.subSequence(start, start + count).toString());
                    bridgeUpdating[0] = true;
                    command.setText("");
                    bridgeUpdating[0] = false;
                    focusCommandInput(command);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }
        command.setOnEditorActionListener((v, actionId, event) -> {
            boolean sendAction = actionId == EditorInfo.IME_ACTION_SEND;
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (sendAction || enter) {
                if (localMode) {
                    sendCommand(command);
                } else if (terminalController.isAlternateScreen()) {
                    terminalController.sendRaw("\n");
                    focusCommandInput(command);
                } else {
                    sendCommand(command);
                }
                return true;
            }
            return false;
        });
        bar.addView(command, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button send = actionButton("send");
        send.setOnClickListener(v -> {
            if (localMode) {
                sendCommand(command);
            } else if (terminalController.isAlternateScreen()) {
                terminalController.sendRaw("\n");
                focusCommandInput(command);
            } else {
                sendCommand(command);
            }
        });
        bar.addView(send, fixedButtonParams());
        return bar;
    }

    private void sendCommand(EditText command) {
        String value = command.getText().toString();
        if (!value.isEmpty()) {
            if (localMode) {
                localCommandController.runCommand(value);
            } else {
                terminalController.sendCommand(value);
            }
            command.setText("");
        }
        focusCommandInput(command);
    }

    private void sendSshShortcut(String value) {
        flushSshCommandInput();
        terminalController.sendRaw(value);
        if (activeCommandInput != null) {
            focusCommandInput(activeCommandInput);
        }
    }

    private void flushSshCommandInput() {
        if (activeCommandInput == null || terminalController.isAlternateScreen()) {
            return;
        }
        String value = activeCommandInput.getText().toString();
        if (value.isEmpty()) {
            return;
        }
        terminalController.sendRaw(value);
        activeCommandInput.setText("");
    }

    private String shortcutForKeyCode(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            return "\t";
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return "\u001B[D";
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return "\u001B[C";
        }
        return null;
    }

    private void focusCommandInput(EditText command) {
        command.post(() -> {
            command.requestFocusFromTouch();
            command.requestFocus();
            command.setSelection(command.getText().length());
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(command, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void scrollTerminalToBottom() {
        if (terminalScroll == null || terminalText == null) {
            return;
        }
        terminalScroll.post(() -> {
            int bottom = Math.max(0, terminalText.getBottom() - terminalScroll.getHeight());
            terminalScroll.scrollTo(0, bottom);
        });
    }

    private void updateSshTerminalSize() {
        if (localMode || terminalScroll == null || terminalText == null) {
            return;
        }
        TerminalLayoutSizer.Size size = TerminalLayoutSizer.measure(
                terminalText,
                terminalScroll.getWidth(),
                terminalScroll.getHeight()
        );
        if (size == null) {
            return;
        }
        if (size.columns == lastTerminalColumns && size.rows == lastTerminalRows) {
            return;
        }
        lastTerminalColumns = size.columns;
        lastTerminalRows = size.rows;
        terminalController.resizeTerminal(size.columns, size.rows);
    }

    private void closeActiveTerminal() {
        activeCommandInput = null;
        if (localMode) {
            localCommandController.setStateListener(null);
            localCommandController.close();
        } else {
            terminalController.setStateListener(null);
            terminalController.disconnect();
        }
    }

    private void toggleMainOrientation() {
        landscapeMode = !landscapeMode;
        applyRequestedOrientation();
        showHostList();
    }

    private void applyRequestedOrientation() {
        setRequestedOrientation(landscapeMode
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private LinearLayout baseRoot() {
        LinearLayout layout = column();
        layout.setPadding(dp(12), dp(12), dp(12), dp(12));
        layout.setBackgroundColor(color(R.color.mssh_black));
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView title(String value) {
        TextView text = label(value);
        text.setTextSize(30);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private TextView label(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color(R.color.mssh_text));
        text.setTextSize(16);
        text.setPadding(dp(4), dp(2), dp(4), dp(2));
        return text;
    }

    private TextView small(String value) {
        TextView text = label(value);
        text.setTextSize(12);
        text.setTextColor(color(R.color.mssh_yellow));
        return text;
    }

    private View formField(String title, View field) {
        LinearLayout wrap = column();
        TextView label = formLabel(title);
        label.setPadding(dp(4), dp(10), dp(4), dp(4));
        wrap.addView(label);
        wrap.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return wrap;
    }

    private TextView formLabel(String value) {
        TextView text = label(value);
        text.setTextSize(13);
        text.setTextColor(color(R.color.mssh_form_label));
        text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(color(R.color.mssh_text));
        input.setHintTextColor(color(R.color.mssh_yellow));
        input.setBackgroundColor(color(R.color.mssh_surface));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        return input;
    }

    private CheckBox checkBox(String text) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(color(R.color.mssh_text));
        checkBox.setTextSize(14);
        checkBox.setButtonTintList(ColorStateList.valueOf(color(R.color.mssh_yellow)));
        checkBox.setPadding(dp(4), dp(8), dp(4), dp(2));
        return checkBox;
    }

    private Spinner protocolSpinner() {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SHOW_SFTP ? new String[]{"SSH", "SFTP"} : new String[]{"SSH"}
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleProtocolItem(view);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleProtocolItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackgroundColor(color(R.color.mssh_surface));
        spinner.setPadding(dp(10), dp(8), dp(10), dp(8));
        return spinner;
    }

    private void styleProtocolItem(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(color(R.color.mssh_text));
            text.setBackgroundColor(color(R.color.mssh_surface));
            text.setTextSize(16);
            text.setPadding(dp(10), dp(8), dp(10), dp(8));
        }
    }

    private void setPasswordVisible(EditText password, ImageButton toggle, boolean visible) {
        password.setInputType(InputType.TYPE_CLASS_TEXT |
                (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        password.setSelection(password.getText().length());
        toggle.setImageResource(visible ? R.drawable.ic_eye : R.drawable.ic_eye_off);
        toggle.setContentDescription(visible ? "隐藏密码" : "显示密码");
    }

    private ImageButton iconButton() {
        ImageButton button = new ImageButton(this);
        button.setBackground(buttonBackground(color(R.color.mssh_surface)));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(color(R.color.mssh_yellow));
        button.setBackground(buttonBackground(color(R.color.mssh_surface)));
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        return button;
    }

    private GradientDrawable buttonBackground(int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setStroke(dp(1), color(R.color.mssh_line));
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(R.color.mssh_surface));
        drawable.setStroke(dp(1), color(R.color.mssh_line));
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private LinearLayout.LayoutParams fixedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(58), dp(42));
        params.setMargins(dp(4), dp(4), 0, dp(4));
        return params;
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 22;
        }
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return (size / (1024 * 1024)) + " MB";
    }

    private int color(int id) {
        return getColor(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
    }
}
