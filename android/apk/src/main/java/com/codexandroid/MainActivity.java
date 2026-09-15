package com.codexandroid;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 18);
    private static final int PANEL = Color.rgb(27, 30, 36);
    private static final int FIELD = Color.rgb(35, 39, 46);
    private static final int TEXT = Color.rgb(235, 238, 242);
    private static final int MUTED = Color.rgb(154, 162, 174);
    private static final int OK = Color.rgb(92, 214, 143);
    private static final int WARN = Color.rgb(255, 199, 99);
    private static final int BAD = Color.rgb(255, 111, 111);
    private static final int ACCENT = Color.rgb(95, 164, 255);
    private static final int MAX_HISTORY_CHARS = 60_000;

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern LOGIN_CODE_PATTERN = Pattern.compile("\\b[A-Z0-9]{4}-[A-Z0-9]{5}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final String ANDROID_AGENT_PROMPT =
            "You are Codex running inside an Android app on the phone. "
                    + "Work like Codex: briefly say what you are doing, use shell/tools for the task, then give one final result. "
                    + "Use /sdcard or /storage/emulated/0 for user files when permission allows it. "
                    + "For Android apps and settings you may use commands such as pm list packages, am start, cmd, settings, content. "
                    + "If Android blocks an action without Accessibility, ADB, or root, say exactly which access is needed. "
                    + "User request:\n\n";

    private static final String[] MODEL_LABELS = {
        "5.6 Sol", "5.6 Terra", "5.6 Luna", "5.5", "5.4", "5.4 Mini", "5.2"
    };
    private static final String[] MODEL_IDS = {
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna",
        "gpt-5.5",
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.2"
    };

    private Button loginButton;
    private EditText promptInput;
    private Spinner modelSpinner;
    private ScrollView scrollView;
    private TextView chatOutput;
    private TextView authStatus;
    private TextView filesStatus;
    private TextView accessibilityStatus;
    private TextView rootStatus;
    private TextView adbStatus;
    private LinearLayout accessRows;
    private Button accessToggle;
    private String previousRuns = "";
    private boolean accessExpanded;
    private volatile boolean rootAvailable;
    private volatile boolean rootChecked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureDirs();
        loadHistory();
        setContentView(createContentView());
        checkRootAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccessIndicators();
    }

    private View createContentView() {
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Codex Android");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        root.addView(title);

        LinearLayout accessPanel = panel();
        root.addView(accessPanel);

        authStatus = statusText();
        filesStatus = statusText();
        accessibilityStatus = statusText();
        rootStatus = statusText();
        adbStatus = statusText();

        accessToggle = actionButton("Access +");
        accessToggle.setOnClickListener(view -> toggleAccessPanel());
        accessPanel.addView(accessToggle, matchWrap());

        accessRows = new LinearLayout(this);
        accessRows.setOrientation(LinearLayout.VERTICAL);
        accessPanel.addView(accessRows);

        loginButton = actionButton("Login");
        loginButton.setOnClickListener(view -> loginWithDeviceAuth());
        accessRows.addView(row(authStatus, loginButton));

        Button filesButton = actionButton("Files");
        filesButton.setOnClickListener(view -> openAllFilesSettings());
        accessRows.addView(row(filesStatus, filesButton));

        Button accessibilityButton = actionButton("Control");
        accessibilityButton.setOnClickListener(view -> openAccessibilitySettings());
        accessRows.addView(row(accessibilityStatus, accessibilityButton));

        Button rootButton = actionButton("Root");
        rootButton.setOnClickListener(view -> requestRoot());
        accessRows.addView(row(rootStatus, rootButton));

        Button adbButton = actionButton("ADB");
        adbButton.setOnClickListener(view -> openDeveloperSettings());
        accessRows.addView(row(adbStatus, adbButton));

        modelSpinner = new Spinner(this);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MODEL_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(adapter);
        modelSpinner.setBackgroundColor(FIELD);
        root.addView(modelSpinner, matchWrap());

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(0, 18, 0, 18);
        root.addView(composer);

        promptInput = new EditText(this);
        promptInput.setMinLines(2);
        promptInput.setMaxLines(5);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptInput.setHint("Message for Codex");
        promptInput.setHintTextColor(MUTED);
        promptInput.setTextColor(TEXT);
        promptInput.setBackgroundColor(FIELD);
        promptInput.setPadding(20, 16, 20, 16);
        composer.addView(promptInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button send = actionButton("\u27a4");
        send.setTextSize(22);
        send.setOnClickListener(view -> runPrompt());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(112, 112);
        sendParams.setMargins(14, 0, 0, 0);
        composer.addView(send, sendParams);

        chatOutput = new TextView(this);
        chatOutput.setTextSize(14);
        chatOutput.setTextColor(TEXT);
        chatOutput.setPadding(0, 8, 0, 0);
        updateChatOutput(previousRuns.isEmpty() ? "Ready." : previousRuns);
        root.addView(chatOutput);

        applyAccessPanelState();
        refreshAccessIndicators();
        return scrollView;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 16, 16, 16);
        panel.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 18, 0, 18);
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout row(TextView status, Button button) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);
        row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(button, new LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView statusText() {
        TextView view = new TextView(this);
        view.setTextColor(TEXT);
        view.setTextSize(13);
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(TEXT);
        button.setBackgroundColor(FIELD);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void toggleAccessPanel() {
        accessExpanded = !accessExpanded;
        applyAccessPanelState();
        prefs().edit().putBoolean("access_expanded", accessExpanded).apply();
    }

    private void applyAccessPanelState() {
        if (accessRows != null) {
            accessRows.setVisibility(accessExpanded ? View.VISIBLE : View.GONE);
        }
        if (accessToggle != null) {
            accessToggle.setText(accessExpanded ? "Access -" : accessSummaryText());
        }
    }

    private String accessSummaryText() {
        int enabled = 0;
        int total = 4;
        if (authFile().isFile()) {
            enabled++;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            enabled++;
        }
        if (CodexAccessibilityService.isActive()) {
            enabled++;
        }
        if (rootAvailable) {
            enabled++;
        }
        return "Access " + enabled + "/" + total + " +";
    }

    private void refreshAccessIndicators() {
        boolean loggedIn = authFile().isFile();
        setIndicator(authStatus, "Login", loggedIn, loggedIn ? "signed in" : "not signed in");
        if (loginButton != null) {
            loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        }

        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        setIndicator(filesStatus, "All files", allFiles, allFiles ? "/sdcard access allowed" : "tap Files to enable");
        setIndicator(
                accessibilityStatus,
                "Screen control",
                CodexAccessibilityService.isActive(),
                CodexAccessibilityService.isActive() ? "Accessibility enabled" : "tap Control to enable");
        setIndicator(
                rootStatus,
                "Root",
                rootAvailable,
                rootChecked ? (rootAvailable ? "su granted" : "su unavailable") : "checking");
        adbStatus.setText("\u25cb ADB/system: enabled on PC side, not an app permission");
        adbStatus.setTextColor(MUTED);
        applyAccessPanelState();
    }

    private void setIndicator(TextView view, String name, boolean enabled, String detail) {
        view.setText((enabled ? "\u25cf " : "\u25cb ") + name + ": " + detail);
        view.setTextColor(enabled ? OK : WARN);
    }

    private void loginWithDeviceAuth() {
        runCodex("login", List.of("login", "--device-auth"), true);
    }

    private void runPrompt() {
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            prependRun("Codex\n\nWrite a message first.");
            return;
        }
        promptInput.setText("");

        String model = MODEL_IDS[modelSpinner.getSelectedItemPosition()];
        String modelLabel = MODEL_LABELS[modelSpinner.getSelectedItemPosition()];
        List<String> args = new ArrayList<>();
        args.add("exec");
        args.add("--json");
        args.add("--skip-git-repo-check");
        args.add("--dangerously-bypass-approvals-and-sandbox");
        args.add("-m");
        args.add(model);
        args.add("-C");
        args.add(workspaceDir().getAbsolutePath());
        args.add("-");
        runCodex("You: " + prompt + "\nModel: " + modelLabel, args, false, ANDROID_AGENT_PROMPT + prompt);
    }

    private void runCodex(String label, List<String> args, boolean openLoginUrl) {
        runCodex(label, args, openLoginUrl, null);
    }

    private void runCodex(String label, List<String> args, boolean openLoginUrl, String stdinText) {
        setTopRun(label + "\n\nRunning...");
        new Thread(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add(codexExecutable().getAbsolutePath());
                command.addAll(args);

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(workspaceDir());
                builder.redirectErrorStream(true);
                Map<String, String> env = builder.environment();
                env.put("HOME", filesRoot().getAbsolutePath());
                env.put("CODEX_HOME", codexHome().getAbsolutePath());
                env.put("TMPDIR", getCacheDir().getAbsolutePath());
                env.put("PATH", getApplicationInfo().nativeLibraryDir + ":/system/bin:/system/xbin");

                Process process = builder.start();
                if (stdinText == null) {
                    process.getOutputStream().close();
                } else {
                    process.getOutputStream().write(stdinText.getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().close();
                }

                StringBuilder loginText = new StringBuilder();
                CodexRunView runView = new CodexRunView(label);
                String[] loginUrl = {null};
                String[] loginCode = {null};
                boolean[] openedUrl = {false};
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String cleanLine = stripAnsi(line);
                        if (openLoginUrl) {
                            loginText.append(cleanLine).append('\n');
                            handleLoginLine(cleanLine, loginUrl, loginCode, openedUrl);
                            String snapshot = label + "\n\n" + loginText;
                            runOnUiThread(() -> setTopRun(snapshot));
                        } else {
                            runView.acceptJsonLine(cleanLine);
                            String snapshot = runView.render(false);
                            runOnUiThread(() -> setTopRun(snapshot));
                        }
                    }
                }
                int exitCode = process.waitFor();
                if (openLoginUrl) {
                    loginText.append("\n[exit ").append(exitCode).append("]");
                    String finalLogin = label + "\n\n" + loginText;
                    runOnUiThread(() -> {
                        setTopRun(finalLogin);
                        refreshAccessIndicators();
                    });
                } else {
                    String rendered = runView.render(true) + "\n\n[exit " + exitCode + "]";
                    runOnUiThread(() -> finishTopRun(rendered));
                }
            } catch (Exception exc) {
                runOnUiThread(() -> finishTopRun("Codex launch error:\n" + exc));
            }
        }).start();
    }

    private void handleLoginLine(
            String cleanLine, String[] loginUrl, String[] loginCode, boolean[] openedUrl) {
        Matcher urlMatcher = URL_PATTERN.matcher(cleanLine);
        if (urlMatcher.find()) {
            loginUrl[0] = trimUrl(urlMatcher.group());
        }
        Matcher codeMatcher = LOGIN_CODE_PATTERN.matcher(cleanLine);
        if (codeMatcher.find()) {
            loginCode[0] = codeMatcher.group();
        }
        if (!openedUrl[0] && loginUrl[0] != null && loginCode[0] != null) {
            openedUrl[0] = true;
            String url = loginUrl[0];
            String code = loginCode[0];
            runOnUiThread(() -> copyLoginAndOpenBrowser(url, code));
        } else if (loginUrl[0] != null) {
            String url = loginUrl[0];
            String code = loginCode[0];
            runOnUiThread(() -> copyLoginToClipboard(url, code));
        }
    }

    private void setTopRun(String message) {
        updateChatOutput(message + divider() + previousRuns);
    }

    private void finishTopRun(String message) {
        previousRuns = message + divider() + previousRuns;
        trimAndSaveHistory();
        updateChatOutput(previousRuns);
        refreshAccessIndicators();
    }

    private void prependRun(String message) {
        previousRuns = message + divider() + previousRuns;
        trimAndSaveHistory();
        updateChatOutput(previousRuns);
    }

    private void updateChatOutput(String text) {
        chatOutput.setText(text);
        if (scrollView != null) {
            scrollView.post(() -> scrollView.smoothScrollTo(0, Math.max(0, chatOutput.getTop())));
        }
    }

    private void loadHistory() {
        previousRuns = prefs().getString("chat_history", "");
        accessExpanded = prefs().getBoolean("access_expanded", false);
    }

    private void trimAndSaveHistory() {
        if (previousRuns.length() > MAX_HISTORY_CHARS) {
            previousRuns = previousRuns.substring(0, MAX_HISTORY_CHARS);
        }
        prefs().edit().putString("chat_history", previousRuns).putBoolean("access_expanded", accessExpanded).apply();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("codex_android", MODE_PRIVATE);
    }

    private String divider() {
        return "\n\n------------------------------\n\n";
    }

    private static String stripAnsi(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    private static String trimUrl(String url) {
        return url.replaceAll("[)\\].,;]+$", "");
    }

    private void copyLoginAndOpenBrowser(String url, String code) {
        copyLoginToClipboard(url, code);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void copyLoginToClipboard(String url, String code) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            String text = code == null ? url : url + "\nCode: " + code;
            clipboard.setPrimaryClip(ClipData.newPlainText("Codex login", text));
        }
    }

    private void openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return;
            }
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openDeveloperSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    }

    private void requestRoot() {
        setTopRun("Root\n\nRequesting su...");
        new Thread(() -> {
            boolean ok = runRootCheck();
            rootAvailable = ok;
            rootChecked = true;
            runOnUiThread(() -> {
                refreshAccessIndicators();
                finishTopRun(ok ? "Root\n\nRoot granted by su." : "Root\n\nRoot is not available or was denied.");
            });
        }).start();
    }

    private void checkRootAsync() {
        new Thread(() -> {
            rootAvailable = runRootCheck();
            rootChecked = true;
            runOnUiThread(this::refreshAccessIndicators);
        }).start();
    }

    private boolean runRootCheck() {
        try {
            Process process = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            int code = process.waitFor();
            return code == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private File codexExecutable() {
        return new File(getApplicationInfo().nativeLibraryDir, "libcodex.so");
    }

    private File filesRoot() {
        return getFilesDir();
    }

    private File codexHome() {
        return new File(filesRoot(), ".codex");
    }

    private File authFile() {
        return new File(codexHome(), "auth.json");
    }

    private File workspaceDir() {
        return new File(filesRoot(), "workspace");
    }

    private void ensureDirs() {
        codexHome().mkdirs();
        workspaceDir().mkdirs();
        writeCodexConfig();
    }

    private void writeCodexConfig() {
        File config = new File(codexHome(), "config.toml");
        try (InputStream input = getAssets().open("codex-android.config.toml");
                FileOutputStream output = new FileOutputStream(config, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (Exception exc) {
            throw new IllegalStateException("Could not write Codex config", exc);
        }
    }

    private static final class CodexRunView {
        private final String label;
        private final StringBuilder progress = new StringBuilder();
        private String finalMessage = "";
        private String error = "";
        private long totalTokens;

        CodexRunView(String label) {
            this.label = label;
            progress.append("Working...\n\n");
        }

        void acceptJsonLine(String line) {
            try {
                JSONObject event = new JSONObject(line);
                String type = event.optString("type");
                if ("turn.completed".equals(type)) {
                    JSONObject usage = event.optJSONObject("usage");
                    if (usage != null) {
                        totalTokens = usage.optLong("input_tokens")
                                + usage.optLong("output_tokens")
                                + usage.optLong("reasoning_output_tokens");
                    }
                    return;
                }
                if ("turn.failed".equals(type) || "error".equals(type)) {
                    JSONObject errorObject = event.optJSONObject("error");
                    error = errorObject == null ? event.optString("message") : errorObject.optString("message");
                    return;
                }
                JSONObject item = event.optJSONObject("item");
                if (item != null) {
                    acceptItem(type, item);
                }
            } catch (Exception ignored) {
                if (!line.trim().isEmpty()) {
                    appendProgress(line);
                }
            }
        }

        private void acceptItem(String eventType, JSONObject item) {
            String itemType = item.optString("type");
            if ("agent_message".equals(itemType)) {
                finalMessage = item.optString("text", finalMessage);
            } else if ("reasoning".equals(itemType)) {
                appendProgress("Thinking: " + item.optString("text"));
            } else if ("command_execution".equals(itemType)) {
                String status = item.optString("status");
                String command = item.optString("command");
                if ("item.started".equals(eventType) || "in_progress".equals(status)) {
                    appendProgress("Running: " + command);
                } else {
                    String output = item.optString("aggregated_output");
                    String message = "Done: " + command + " (exit " + item.optInt("exit_code", 0) + ")";
                    if (!output.trim().isEmpty()) {
                        message += "\n" + trimLong(output, 1800);
                    }
                    appendProgress(message);
                }
            } else if ("file_change".equals(itemType)) {
                appendProgress("Changing files: " + fileChangesText(item.optJSONArray("changes")));
            } else if ("web_search".equals(itemType)) {
                appendProgress("Searching the web...");
            } else if ("mcp_tool_call".equals(itemType)) {
                appendProgress("Using tool: " + item.optString("server") + "/" + item.optString("tool"));
            } else if ("error".equals(itemType)) {
                appendProgress("Error: " + item.optString("message"));
            }
        }

        private void appendProgress(String message) {
            String clean = message.trim();
            if (clean.isEmpty()) {
                return;
            }
            String block = clean + "\n\n";
            if (!progress.toString().endsWith(block)) {
                progress.append(block);
            }
        }

        String render(boolean finished) {
            StringBuilder out = new StringBuilder(label).append("\n\n").append(progress);
            if (!error.isEmpty()) {
                out.append("Error:\n").append(error).append("\n\n");
            }
            if (!finalMessage.isEmpty()) {
                out.append("Result:\n").append(finalMessage.trim()).append('\n');
            } else if (!finished) {
                out.append("Waiting for result...\n");
            }
            if (totalTokens > 0) {
                out.append("\nTokens: ").append(totalTokens);
            }
            return out.toString();
        }

        private static String fileChangesText(JSONArray changes) {
            if (changes == null || changes.length() == 0) {
                return "preparing changes";
            }
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < changes.length(); i++) {
                JSONObject change = changes.optJSONObject(i);
                if (change != null) {
                    paths.add(change.optString("path"));
                }
            }
            return String.join(", ", paths);
        }

        private static String trimLong(String text, int maxChars) {
            String clean = text.trim();
            if (clean.length() <= maxChars) {
                return clean;
            }
            return clean.substring(0, maxChars) + "\n...";
        }
    }
}
