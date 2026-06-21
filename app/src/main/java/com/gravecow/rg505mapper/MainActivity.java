package com.gravecow.rg505mapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String MODDIR = "/data/adb/modules/rg505_dpad_wasd";
    private static final String PREFS = "presets";
    private static final String PRESETS_JSON = "presets_json";
    private static final String APPLIED_NAME = "applied_name";
    private static final String APPLIED_VERSION = "applied_version";
    private static final int BG = Color.rgb(247, 248, 250);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(31, 36, 44);
    private static final int MUTED = Color.rgb(92, 101, 115);
    private static final int LINE = Color.rgb(224, 229, 236);
    private static final int PRIMARY = Color.rgb(36, 87, 197);
    private static final int DANGER = Color.rgb(180, 54, 54);
    private static final String[] TARGETS = new String[]{
            "KEY_W","KEY_A","KEY_S","KEY_D","KEY_Q","KEY_E","KEY_R","KEY_F","KEY_Z","KEY_X","KEY_C","KEY_V","KEY_B",
            "KEY_SPACE","KEY_ENTER","KEY_ESC","KEY_TAB","KEY_LEFTCTRL","KEY_LEFTSHIFT","KEY_LEFTALT",
            "MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE"
    };

    private LinearLayout root;
    private ScrollView scroll;
    private TextView output;
    private SharedPreferences prefs;
    private final ArrayList<Preset> presets = new ArrayList<>();
    private Preset editing;
    private boolean editingIsNew;
    private EditText nameField, sourceField, mouseXField, mouseYField, centerXField, centerYField, mouseMinXField, mouseMaxXField, mouseMinYField, mouseMaxYField, deadzoneField, speedField, intervalField;
    private LinearLayout mappingsList;
    private MaterialButton learnStickButton, saveButton;
    private final ArrayList<MappingRow> mappingRows = new ArrayList<>();

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadPresets();
        showGateThenMain();
    }

    private void showGateThenMain() {
        setBase();
        addHeader("RG505 Input Mapper", "Checking backend status");
        addStatus("Checking backend...");
        setContentView(scroll);
        new Thread(() -> {
            BackendInfo info = getBackendInfo();
            runOnUiThread(() -> {
                if (!info.installed) showInstallOnly(info);
                else showMain(info);
            });
        }).start();
    }

    private void showInstallOnly(BackendInfo info) {
        setBase();
        addHeader("RG505 Input Mapper", "Install the mapper backend before editing presets.");
        MaterialCardView card = surface();
        LinearLayout body = cardBody(card);
        body.addView(bodyText("Backend not installed."));
        body.addView(space(8));
        body.addView(button("Install backend", false, v -> installBackendThenMain()), full());
        root.addView(card);
        addStatus("Backend not installed.");
        setContentView(scroll);
    }

    private void showMain(BackendInfo info) {
        setBase();
        addHeader("RG505 Input Mapper", "Create, edit, apply, and manage input presets.");

        if (info.outdated) {
            MaterialCardView update = surface();
            LinearLayout body = cardBody(update);
            body.addView(label("Backend update available", 16, true));
            body.addView(small("Installed " + info.installedCode + ", bundled " + info.assetCode));
            body.addView(space(8));
            body.addView(button("Update backend", false, v -> installBackendThenMain()), full());
            root.addView(update);
        }

        String applied = prefs.getString(APPLIED_NAME, null);
        int appliedVersion = prefs.getInt(APPLIED_VERSION, 0);
        MaterialCardView appliedCard = surface();
        LinearLayout appliedBody = cardBody(appliedCard);
        appliedBody.addView(small("Applied preset"));
        appliedBody.addView(label(applied == null ? "None" : applied + " v" + appliedVersion, 18, true));
        root.addView(appliedCard);

        LinearLayout presetHeader = splitHeader("Presets");
        presetHeader.addView(button("New preset", false, v -> showPreset(Preset.create("Untitled Preset"), true)), wrap());
        root.addView(presetHeader);

        if (presets.isEmpty()) {
            TextView empty = small("No saved presets yet. Create one, edit it, then save it to add it to this list.");
            empty.setPadding(0, dp(8), 0, dp(8));
            root.addView(empty);
        } else {
            for (Preset p : presets) addPresetRow(p, info);
        }

        root.addView(section("Backend"));
        MaterialButton restart = button("Restart", true, v -> runAndShowAsync("sh " + MODDIR + "/mapctl restart"));
        MaterialButton stop = button("Stop", true, v -> runAndShowAsync("sh " + MODDIR + "/mapctl stop"));
        MaterialButton status = button("Status", true, v -> runAndShowAsync("sh " + MODDIR + "/mapctl status"));
        MaterialButton logs = button("Logs", true, v -> runAndShowAsync("sh " + MODDIR + "/mapctl logs 160"));
        root.addView(buttonRow(restart, stop));
        root.addView(buttonRow(status, logs));

        addStatus("Ready");
        setContentView(scroll);
    }

    private void addPresetRow(Preset p, BackendInfo info) {
        MaterialCardView card = surface();
        card.setClickable(true);
        card.setOnClickListener(v -> showPreset(p, false));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(row, frameFull());

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(p.name, 17, true));
        text.addView(small(p.maps.size() + " mappings - v" + p.version));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton edit = button("Edit", true, v -> showPreset(p, false));
        MaterialButton delete = button("Delete", true, v -> confirmDeletePreset(p, info));
        delete.setTextColor(DANGER);
        delete.setStrokeColor(android.content.res.ColorStateList.valueOf(DANGER));
        row.addView(edit, compact());
        row.addView(delete, compact());
        root.addView(card);
    }

    private void confirmDeletePreset(Preset p, BackendInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("Delete preset?")
                .setMessage(p.name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) -> {
                    presets.remove(p);
                    if (p.name.equals(prefs.getString(APPLIED_NAME, null))) {
                        prefs.edit().remove(APPLIED_NAME).remove(APPLIED_VERSION).apply();
                    }
                    savePresets();
                    showMain(info);
                })
                .show();
    }

    private void showPreset(Preset p, boolean isNew) {
        editing = p;
        editingIsNew = isNew;
        setBase();
        addHeader(isNew ? "New Preset" : "Edit Preset", isNew ? "Save to add it to the preset list." : p.name + " v" + p.version);

        MaterialButton back = button("Back", true, v -> showGateThenMain());
        saveButton = button("Save", false, v -> savePresetOnly());
        MaterialButton apply = button("Apply", false, v -> applyPreset());
        root.addView(buttonRow(back, saveButton, apply));

        root.addView(section("Name"));
        nameField = edit(p.name);
        root.addView(nameField, full());

        root.addView(section("Mouse Stick"));
        MaterialCardView mouseCard = surface();
        LinearLayout mouse = cardBody(mouseCard);
        learnStickButton = button("Learn stick", false, v -> learnStick());
        mouse.addView(learnStickButton, full());
        mouse.addView(small("Current: " + p.mouseAxisX + " / " + p.mouseAxisY));

        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        MaterialButton advancedButton = button("Advanced", true, v -> {
            boolean hidden = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(hidden ? View.VISIBLE : View.GONE);
            ((MaterialButton) v).setText(hidden ? "Hide advanced" : "Advanced");
        });
        mouse.addView(space(8));
        mouse.addView(advancedButton, full());

        sourceField = edit(p.sourceName);
        mouseXField = edit(p.mouseAxisX);
        mouseYField = edit(p.mouseAxisY);
        centerXField = edit(String.valueOf(p.centerX));
        centerYField = edit(String.valueOf(p.centerY));
        mouseMinXField = edit(String.valueOf(p.mouseMinX));
        mouseMaxXField = edit(String.valueOf(p.mouseMaxX));
        mouseMinYField = edit(String.valueOf(p.mouseMinY));
        mouseMaxYField = edit(String.valueOf(p.mouseMaxY));
        deadzoneField = edit(String.valueOf(p.deadzone));
        speedField = edit(String.valueOf(p.speed));
        intervalField = edit(String.valueOf(p.intervalMs));
        advanced.addView(fieldRow("Source", sourceField));
        advanced.addView(fieldRow("X axis", mouseXField));
        advanced.addView(fieldRow("Y axis", mouseYField));
        advanced.addView(fieldRow("Center X", centerXField));
        advanced.addView(fieldRow("Center Y", centerYField));
        advanced.addView(fieldRow("Min X", mouseMinXField));
        advanced.addView(fieldRow("Max X", mouseMaxXField));
        advanced.addView(fieldRow("Min Y", mouseMinYField));
        advanced.addView(fieldRow("Max Y", mouseMaxYField));
        advanced.addView(fieldRow("Deadzone", deadzoneField));
        advanced.addView(fieldRow("Speed", speedField));
        advanced.addView(fieldRow("Interval", intervalField));
        mouse.addView(advanced);
        root.addView(mouseCard);

        LinearLayout mappingHeader = splitHeader("Mappings");
        mappingHeader.addView(button("Add map", false, v -> addMappingRow("Listen", "KEY_SPACE")), wrap());
        root.addView(mappingHeader);
        mappingsList = new LinearLayout(this);
        mappingsList.setOrientation(LinearLayout.VERTICAL);
        mappingRows.clear();
        root.addView(mappingsList, full());
        for (MapEntry m : p.maps) addMappingRow(m.input, m.target);
        if (p.maps.isEmpty()) mappingsList.addView(small("No mappings yet."));

        addStatus(isNew ? "Preset is not saved yet." : "");
        attachChangeTracking();
        updateSaveButton(isNew);
        setContentView(scroll);
    }

    private void savePresetOnly() {
        if (editing == null) return;
        collectPreset(editing);
        if (editingIsNew) {
            presets.add(editing);
            editingIsNew = false;
        }
        savePresets();
        updateSaveButton(false);
        setStatus("Saved " + editing.name);
    }

    private void applyPreset() {
        if (editing == null) return;
        if (editingIsNew) {
            setStatus("Save this preset before applying it.");
            return;
        }
        collectPreset(editing);
        String h = editing.contentHash();
        if (!h.equals(editing.lastAppliedHash)) {
            editing.version++;
            editing.lastAppliedHash = h;
        }
        savePresets();
        String config = editing.toConfig();
        String cmd = "cat > " + MODDIR + "/config <<'CFG'\n" + config + "CFG\nsh " + MODDIR + "/mapctl restart";
        runAndShowAsync(cmd, () -> {
            prefs.edit().putString(APPLIED_NAME, editing.name).putInt(APPLIED_VERSION, editing.version).apply();
            showPreset(editing, false);
            setStatus("Applied " + editing.name + " v" + editing.version);
        });
    }

    private void collectPreset(Preset p) {
        p.name = str(nameField, p.name);
        p.sourceName = str(sourceField, "Xbox Wireless Controller");
        p.mouseAxisX = str(mouseXField, "ABS_Z");
        p.mouseAxisY = str(mouseYField, "ABS_RZ");
        p.centerX = num(centerXField, 0);
        p.centerY = num(centerYField, 0);
        p.mouseMinX = num(mouseMinXField, -32768);
        p.mouseMaxX = num(mouseMaxXField, 32767);
        p.mouseMinY = num(mouseMinYField, -32768);
        p.mouseMaxY = num(mouseMaxYField, 32767);
        p.deadzone = num(deadzoneField, 200);
        p.speed = num(speedField, 1);
        p.intervalMs = num(intervalField, 8);
        p.maps.clear();
        for (MappingRow r : mappingRows) {
            String in = r.inputButton.getText().toString().trim();
            String target = r.target.getText().toString().trim();
            if ((in.startsWith("button:") || in.startsWith("axis:")) && target.length() > 0) {
                p.maps.add(new MapEntry(in, target));
            }
        }
    }

    private void attachChangeTracking() {
        EditText[] fields = new EditText[]{nameField, sourceField, mouseXField, mouseYField, centerXField, centerYField, mouseMinXField, mouseMaxXField, mouseMinYField, mouseMaxYField, deadzoneField, speedField, intervalField};
        for (EditText field : fields) {
            field.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { markDirty(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void markDirty() {
        updateSaveButton(true);
    }

    private void updateSaveButton(boolean dirty) {
        if (saveButton == null) return;
        saveButton.setText(dirty ? "Save" : "Saved");
        saveButton.setClickable(dirty);
        saveButton.setEnabled(dirty);
    }

    private void addMappingRow(String input, String target) {
        if (mappingsList.getChildCount() == 1 && mappingsList.getChildAt(0) instanceof TextView && mappingRows.isEmpty()) {
            mappingsList.removeAllViews();
        }

        MaterialCardView card = surface();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.addView(row, frameFull());

        MaterialButton listen = button(input == null || input.length() == 0 ? "Listen" : input, true, null);
        listen.setSingleLine(false);
        listen.setMaxLines(2);
        AutoCompleteTextView targetField = new AutoCompleteTextView(this);
        targetField.setSingleLine(true);
        targetField.setText(target);
        targetField.setHint("Target");
        targetField.setTextSize(15);
        targetField.setTextColor(TEXT);
        targetField.setHintTextColor(MUTED);
        targetField.setInputType(InputType.TYPE_CLASS_TEXT);
        targetField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, TARGETS));
        targetField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { markDirty(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        MaterialButton remove = button("X", true, null);
        MappingRow mr = new MappingRow(card, listen, targetField);
        mappingRows.add(mr);
        listen.setOnClickListener(v -> listenForInput(mr));
        remove.setTextColor(DANGER);
        remove.setStrokeColor(android.content.res.ColorStateList.valueOf(DANGER));
        remove.setOnClickListener(v -> {
            mappingsList.removeView(card);
            mappingRows.remove(mr);
            if (mappingRows.isEmpty()) mappingsList.addView(small("No mappings yet."));
            markDirty();
        });

        row.addView(listen, new LinearLayout.LayoutParams(0, dp(50), 5));
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4);
        targetLp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(targetField, targetLp);
        row.addView(remove, new LinearLayout.LayoutParams(dp(46), dp(46)));
        mappingsList.addView(card);
        markDirty();
    }

    private void listenForInput(MappingRow row) {
        row.inputButton.setClickable(false);
        row.inputButton.setText("Listening...\npress or move");
        String source = sourceField != null ? sourceField.getText().toString() : "Xbox Wireless Controller";
        new Thread(() -> {
            DetectedInput detected = detectInput(source, 12);
            runOnUiThread(() -> {
                row.inputButton.setClickable(true);
                if (detected == null) {
                    row.inputButton.setText("Timed out\nTap to retry");
                    setStatus("No input detected.");
                } else {
                    row.inputButton.setText(detected.input);
                    if (sourceField != null && detected.deviceName.length() > 0) sourceField.setText(detected.deviceName);
                    markDirty();
                    setStatus("Detected " + detected.input);
                }
            });
        }).start();
    }

    private void learnStick() {
        setLearnText("Center stick...");
        setStatus("Learning stick axes...");
        String source = sourceField != null ? sourceField.getText().toString() : "Xbox Wireless Controller";
        if (learnStickButton != null) learnStickButton.setClickable(false);
        new Thread(() -> {
            try {
                DeviceInfo device = findDeviceInfo(source);
                if (device == null) {
                    runOnUiThread(() -> {
                        setLearnText("No controller found - Try again");
                        if (learnStickButton != null) learnStickButton.setClickable(true);
                        setStatus("No input device matched the source name.");
                    });
                    return;
                }
                final String initialDeviceName = device.name;
                runOnUiThread(() -> {
                    if (sourceField != null) sourceField.setText(initialDeviceName);
                    setLearnText("Center stick...");
                });
                sleepMs(1500);
                DeviceInfo refreshed = findDeviceInfo(device.name);
                if (refreshed != null) device = refreshed;
                final DeviceInfo learnedDevice = device;

                AxisSample left = promptAndSample(learnedDevice, "Move LEFT to the edge", null, 0, null);
                AxisSample right = left == null ? null : promptAndSample(learnedDevice, "Move RIGHT to the edge", left.axis, -left.sign, null);
                AxisSample up = promptAndSample(learnedDevice, "Move UP to the edge", null, 0, left == null ? null : left.axis);
                AxisSample down = up == null ? null : promptAndSample(learnedDevice, "Move DOWN to the edge", up.axis, -up.sign, null);

                runOnUiThread(() -> {
                    if (left == null || right == null || up == null || down == null || !validOppositePair(left, right) || !validOppositePair(up, down)) {
                        setLearnText("Incomplete - Tap to retry");
                        if (learnStickButton != null) learnStickButton.setClickable(true);
                        setStatus("Could not detect stable opposite stick directions.");
                        return;
                    }
                    AxisCalibration xCal = calibrateAxis(left, right);
                    AxisCalibration yCal = calibrateAxis(up, down);
                    String xAxis = xCal.axis;
                    String yAxis = yCal.axis;
                    mouseXField.setText(xAxis);
                    mouseYField.setText(yAxis);
                    centerXField.setText(String.valueOf(xCal.center));
                    centerYField.setText(String.valueOf(yCal.center));
                    mouseMinXField.setText(String.valueOf(xCal.min));
                    mouseMaxXField.setText(String.valueOf(xCal.max));
                    mouseMinYField.setText(String.valueOf(yCal.min));
                    mouseMaxYField.setText(String.valueOf(yCal.max));
                    String dz = deadzoneField.getText().toString().trim();
                    if (dz.isEmpty() || "200".equals(dz)) deadzoneField.setText(String.valueOf(defaultDeadzone(xCal.min, xCal.max, yCal.min, yCal.max)));
                    if (speedField.getText().toString().trim().isEmpty()) speedField.setText("1");
                    if (intervalField.getText().toString().trim().isEmpty()) intervalField.setText("8");
                    setLearnText("Learned " + xAxis + " / " + yAxis + "\nTap to relearn");
                    if (learnStickButton != null) learnStickButton.setClickable(true);
                    markDirty();
                    setStatus("Learned stick axes from " + learnedDevice.name);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLearnText("Learn failed - Tap to retry");
                    if (learnStickButton != null) learnStickButton.setClickable(true);
                    setStatus("Learn failed: " + e);
                });
            }
        }).start();
    }

    private AxisSample promptAndSample(DeviceInfo device, String prompt, String requiredAxis, int requiredSign, String ignoredAxis) {
        runOnUiThread(() -> setLearnText(prompt));
        AxisSample sample = sampleAxis(device, requiredAxis, requiredSign, ignoredAxis);
        runOnUiThread(() -> setLearnText(sample == null ? prompt + "\nnot detected" : prompt + "\n" + sample.axis + " = " + sample.value));
        sleepMs(350);
        return sample;
    }

    private void setLearnText(String text) {
        if (learnStickButton != null) learnStickButton.setText(text);
    }

    private AxisSample sampleAxis(DeviceInfo device, String requiredAxis, int requiredSign, String ignoredAxis) {
        return captureAxisSample(device, 8, requiredAxis, requiredSign, ignoredAxis);
    }

    private String centerFor(int a, int b) {
        if ((a < 0 && b > 0) || (a > 0 && b < 0)) {
            if (Math.abs(Math.abs(a) - Math.abs(b)) < Math.max(Math.abs(a), Math.abs(b)) / 3) return "0";
        }
        return String.valueOf((a + b) / 2);
    }

    private boolean sameAxis(AxisSample a, AxisSample b) {
        return a != null && b != null && a.axis.equals(b.axis);
    }

    private boolean validOppositePair(AxisSample a, AxisSample b) {
        return sameAxis(a, b) && a.sign != 0 && b.sign != 0 && a.sign == -b.sign;
    }

    private int defaultDeadzone(int minX, int maxX, int minY, int maxY) {
        int x = Math.max(1, maxX - minX);
        int y = Math.max(1, maxY - minY);
        return Math.max(4, Math.min(x, y) / 12);
    }

    private AxisCalibration calibrateAxis(AxisSample a, AxisSample b) {
        if (a.hasRange && b.hasRange && a.axis.equals(b.axis)) {
            return new AxisCalibration(a.axis, a.center, a.min, a.max);
        }
        int min = Math.min(a.value, b.value);
        int max = Math.max(a.value, b.value);
        int center = (a.center == b.center) ? a.center : Integer.parseInt(centerFor(a.value, b.value));
        int low = Math.max(1, center - min);
        int high = Math.max(1, max - center);
        int greater = Math.max(low, high);
        int smaller = Math.min(low, high);
        if (smaller >= greater * 3 / 4) {
            min = center - greater;
            max = center + greater;
        }
        return new AxisCalibration(a.axis, center, min, max);
    }

    private DetectedInput detectInput(String sourceName, int seconds) {
        DeviceInfo device = findDeviceInfo(sourceName);
        if (device == null) return null;
        return captureDetectedInput(device, seconds);
    }

    private DetectedInput captureDetectedInput(DeviceInfo device, int timeoutSeconds) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", "getevent -l " + device.path).redirectErrorStream(true).start();
            destroyAfterTimeout(p, timeoutSeconds);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                EventLine event = parseEventLine(line);
                if (event == null) continue;
                if ("EV_KEY".equals(event.type) && event.value != 0) {
                    return new DetectedInput("button:" + event.code, device.name);
                }
                if ("EV_ABS".equals(event.type)) {
                    String dir = axisDirection(event.code, event.value);
                    if (dir != null) return new DetectedInput("axis:" + event.code + ":" + dir, device.name);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (p != null) p.destroy();
        }
        return null;
    }

    private AxisSample captureAxisSample(DeviceInfo device, int timeoutSeconds, String requiredAxis, int requiredSign, String ignoredAxis) {
        Process p = null;
        try {
            Map<String, AxisInfo> axes = parseAxisInfo(device);
            p = new ProcessBuilder("su", "-c", "getevent -l " + device.path).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            long lastImproved = 0;
            AxisSample best = null;
            String line;
            while (System.currentTimeMillis() < deadline) {
                if (!reader.ready()) {
                    if (best != null && best.normalized >= 0.82f && System.currentTimeMillis() - lastImproved >= 650) return best;
                    sleepMs(40);
                    continue;
                }
                line = reader.readLine();
                if (line == null) break;
                EventLine event = parseEventLine(line);
                if (event == null || !"EV_ABS".equals(event.type)) continue;
                if (ignoredAxis != null && ignoredAxis.equals(event.code)) continue;
                if (requiredAxis != null && !requiredAxis.equals(event.code)) continue;

                AxisInfo info = axes.get(event.code);
                if (info == null) info = new AxisInfo(event.code, event.value, Math.min(0, event.value), Math.max(0, event.value), 0, false);
                AxisSample sample = sampleFromEvent(event, info);
                if (sample.sign == 0) continue;
                if (requiredSign != 0 && sample.sign != requiredSign) continue;
                if (sample.normalized < 0.22f) continue;

                if (best == null || sample.normalized > best.normalized || (sample.axis.equals(best.axis) && sample.sign == best.sign && Math.abs(sample.value - info.center) > Math.abs(best.value - info.center))) {
                    best = sample;
                    lastImproved = System.currentTimeMillis();
                }
            }
            return best != null && best.normalized >= 0.82f ? best : null;
        } catch (Exception ignored) {
        } finally {
            if (p != null) p.destroy();
        }
        return null;
    }

    private AxisSample sampleFromEvent(EventLine event, AxisInfo info) {
        int diff = event.value - info.center;
        int sign = diff > 0 ? 1 : (diff < 0 ? -1 : 0);
        int extent = sign > 0 ? info.max - info.center : info.center - info.min;
        if (extent <= 0) extent = Math.max(1, info.max - info.min);
        float normalized = Math.min(1f, Math.abs(diff) / (float) extent);
        int value = event.value;
        if (normalized >= 0.82f) value = sign > 0 ? info.max : info.min;
        return new AxisSample(event.code, value, sign, normalized, info.center, info.min, info.max, info.hasRange);
    }

    private void destroyAfterTimeout(Process process, int timeoutSeconds) {
        new Thread(() -> {
            sleepMs(timeoutSeconds * 1000L);
            try {
                process.destroy();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private Map<String, AxisInfo> parseAxisInfo(DeviceInfo device) {
        HashMap<String, AxisInfo> axes = new HashMap<>();
        for (String rawLine : device.block.split("\\n")) {
            String line = rawLine.trim();
            if (!line.contains("ABS_")) continue;
            String axis = null;
            String[] parts = line.replace(':', ' ').split("\\s+");
            for (String part : parts) {
                if (part.startsWith("ABS_")) {
                    axis = part;
                    break;
                }
            }
            if (axis == null) continue;
            Integer min = parseLabeledInt(line, "min");
            Integer max = parseLabeledInt(line, "max");
            Integer value = parseLabeledInt(line, "value");
            Integer flat = parseLabeledInt(line, "flat");
            if (value == null && min != null && max != null) value = (min + max) / 2;
            if (min == null || max == null || value == null) continue;
            axes.put(axis, new AxisInfo(axis, value, min, max, flat == null ? 0 : flat, true));
        }
        return axes;
    }

    private Integer parseLabeledInt(String line, String label) {
        int idx = line.indexOf(label);
        if (idx < 0) return null;
        idx += label.length();
        while (idx < line.length() && (line.charAt(idx) == ' ' || line.charAt(idx) == '=')) idx++;
        int start = idx;
        if (idx < line.length() && line.charAt(idx) == '-') idx++;
        while (idx < line.length() && Character.isDigit(line.charAt(idx))) idx++;
        if (idx == start || (idx == start + 1 && line.charAt(start) == '-')) return null;
        try {
            return Integer.parseInt(line.substring(start, idx));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String axisDirection(String code, int value) {
        int center = 0;
        if (mouseXField != null && code.equals(mouseXField.getText().toString().trim())) center = num(centerXField, 0);
        else if (mouseYField != null && code.equals(mouseYField.getText().toString().trim())) center = num(centerYField, 0);

        if (center != 0) {
            if (value > center) return "+";
            if (value < center) return "-";
            return null;
        }
        if (value > 0) return "+";
        if (value < 0) return "-";
        return null;
    }

    private EventLine parseEventLine(String line) {
        String[] parts = line.trim().split("\\s+");
        for (int i = 0; i < parts.length - 2; i++) {
            if ("EV_KEY".equals(parts[i]) || "EV_ABS".equals(parts[i])) {
                return new EventLine(parts[i], parts[i + 1], parseEventValue(parts[i + 2]));
            }
        }
        return null;
    }

    private int parseEventValue(String s) {
        try {
            if (s == null) return 0;
            s = s.trim().replace("[", "").replace("]", "");
            if ("DOWN".equalsIgnoreCase(s) || "PRESSED".equalsIgnoreCase(s)) return 1;
            if ("UP".equalsIgnoreCase(s) || "RELEASED".equalsIgnoreCase(s)) return 0;
            if ("REPEAT".equalsIgnoreCase(s)) return 2;
            if (s.startsWith("0x") || s.startsWith("0X")) {
                long l = Long.parseLong(s.substring(2), 16);
                if ((l & 0x80000000L) != 0) l -= 0x100000000L;
                return (int) l;
            }
            if (s.length() == 8 && s.matches("[0-9a-fA-F]{8}")) {
                long l = Long.parseLong(s, 16);
                if ((l & 0x80000000L) != 0) l -= 0x100000000L;
                return (int) l;
            }
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private DeviceInfo findDeviceInfo(String preferredName) {
        String out = runRootCaptureSilent("getevent -lp");
        ArrayList<DeviceInfo> devices = parseDevices(out);
        String needle = preferredName == null ? "" : preferredName.trim().toLowerCase(Locale.US);
        if (needle.length() > 0) {
            for (DeviceInfo d : devices) {
                if (d.name.toLowerCase(Locale.US).contains(needle)) return d;
            }
        }
        for (DeviceInfo d : devices) {
            if (isLikelyController(d)) return d;
        }
        return devices.isEmpty() ? null : devices.get(0);
    }

    private ArrayList<DeviceInfo> parseDevices(String out) {
        ArrayList<DeviceInfo> devices = new ArrayList<>();
        String path = null;
        String name = "";
        StringBuilder block = new StringBuilder();
        for (String line : out.split("\\n")) {
            if (line.startsWith("add device")) {
                if (path != null) devices.add(new DeviceInfo(path, name.length() == 0 ? path : name, block.toString()));
                int idx = line.indexOf("/dev/input/event");
                path = idx >= 0 ? line.substring(idx).trim().split("\\s+")[0] : null;
                name = "";
                block.setLength(0);
            } else {
                block.append(line).append('\n');
                if (line.contains("name:")) {
                    int q = line.indexOf('"');
                    int r = line.lastIndexOf('"');
                    if (q >= 0 && r > q) name = line.substring(q + 1, r);
                }
            }
        }
        if (path != null) devices.add(new DeviceInfo(path, name.length() == 0 ? path : name, block.toString()));
        return devices;
    }

    private boolean isLikelyController(DeviceInfo d) {
        String hay = (d.name + "\n" + d.block).toLowerCase(Locale.US);
        if (hay.contains("rg505 mapper") || hay.contains("touch") || hay.contains("keyboard")) return false;
        return hay.contains("xbox")
                || hay.contains("gamepad")
                || hay.contains("controller")
                || d.block.contains("BTN_SOUTH")
                || d.block.contains("BTN_A")
                || d.block.contains("BTN_GAMEPAD")
                || d.block.contains("ABS_HAT0")
                || (d.block.contains("ABS_Z") && d.block.contains("ABS_RZ"));
    }

    private void installBackendThenMain() {
        setStatus("Installing...");
        new Thread(() -> {
            String result;
            try {
                copyAssetFolder("module", getCacheDir());
                result = runRoot("mkdir -p " + MODDIR + " && cp -f " + getCacheDir().getAbsolutePath() + "/module/* " + MODDIR + "/ && chmod 755 " + MODDIR + " " + MODDIR + "/*");
            } catch (Exception e) {
                result = "Install failed: " + e;
            }
            String finalResult = result;
            runOnUiThread(() -> {
                setStatus(finalResult);
                showGateThenMain();
            });
        }).start();
    }

    private void runAndShowAsync(String cmd) {
        runAndShowAsync(cmd, null);
    }

    private void runAndShowAsync(String cmd, Runnable after) {
        setStatus("Running...");
        new Thread(() -> {
            String r = runRootCaptureSilent(cmd);
            runOnUiThread(() -> {
                setStatus(r);
                if (after != null) after.run();
            });
        }).start();
    }

    private String runRootCaptureSilent(String cmd) {
        try {
            return runRoot(cmd);
        } catch (Exception e) {
            return "Command failed: " + e;
        }
    }

    private String runRoot(String cmd) throws Exception {
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = p.getInputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        int rc = p.waitFor();
        return "$ " + cmd + "\nexit=" + rc + "\n" + baos.toString();
    }

    private BackendInfo getBackendInfo() {
        BackendInfo bi = new BackendInfo();
        String out = runRootCaptureSilent("test -f " + MODDIR + "/module.prop && cat " + MODDIR + "/module.prop || true");
        bi.installed = out.contains("id=rg505_dpad_wasd");
        bi.installedCode = parseVersionCode(out);
        bi.assetCode = getAssetVersionCode();
        bi.outdated = bi.installed && bi.assetCode > bi.installedCode;
        return bi;
    }

    private int getAssetVersionCode() {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            InputStream in = getAssets().open("module/module.prop");
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
            return parseVersionCode(b.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseVersionCode(String s) {
        for (String line : s.split("\\n")) {
            line = line.trim();
            if (line.startsWith("versionCode=")) {
                try {
                    return Integer.parseInt(line.substring(12).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    private void loadPresets() {
        presets.clear();
        String raw = prefs.getString(PRESETS_JSON, null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) presets.add(Preset.fromJson(arr.getJSONObject(i)));
            } catch (Exception ignored) {
            }
        }
    }

    private void savePresets() {
        try {
            JSONArray arr = new JSONArray();
            for (Preset p : presets) arr.put(p.toJson());
            prefs.edit().putString(PRESETS_JSON, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void setBase() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addHeader(String title, String subtitle) {
        TextView t = label(title, 25, true);
        t.setPadding(0, dp(2), 0, dp(2));
        root.addView(t);
        root.addView(small(subtitle));
    }

    private void addStatus(String text) {
        output = small(text);
        output.setTextIsSelectable(true);
        output.setPadding(0, dp(14), 0, 0);
        root.addView(output, full());
        setStatus(text);
    }

    private void setStatus(String text) {
        if (output == null) return;
        output.setText(text);
        output.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private LinearLayout splitHeader(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = full();
        lp.setMargins(0, dp(18), 0, dp(4));
        row.setLayoutParams(lp);
        row.addView(label(title, 19, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private TextView section(String s) {
        TextView v = label(s, 19, true);
        LinearLayout.LayoutParams lp = full();
        lp.setMargins(0, dp(18), 0, dp(4));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView label(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private TextView small(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(MUTED);
        v.setTextSize(14);
        return v;
    }

    private TextView bodyText(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(15);
        return v;
    }

    private Space space(int h) {
        Space sp = new Space(this);
        sp.setMinimumHeight(dp(h));
        return sp;
    }

    private EditText edit(String s) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(s);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private View fieldRow(String label, EditText e) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = small(label);
        l.addView(t, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return l;
    }

    private MaterialCardView surface() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardBackgroundColor(CARD);
        c.setStrokeColor(LINE);
        c.setStrokeWidth(dp(1));
        c.setRadius(dp(8));
        c.setCardElevation(0);
        LinearLayout.LayoutParams lp = full();
        lp.setMargins(0, dp(8), 0, 0);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout cardBody(MaterialCardView c) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.addView(body, frameFull());
        return body;
    }

    private MaterialButton button(String text, boolean outlined, View.OnClickListener l) {
        MaterialButton b = outlined
                ? new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinHeight(dp(44));
        b.setMinimumHeight(dp(44));
        b.setCornerRadius(dp(8));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setGravity(Gravity.CENTER);
        if (outlined) {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));
            b.setTextColor(PRIMARY);
            b.setStrokeColor(android.content.res.ColorStateList.valueOf(PRIMARY));
            b.setStrokeWidth(dp(1));
        } else {
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
            b.setTextColor(Color.WHITE);
        }
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private LinearLayout buttonRow(View... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = full();
        rowLp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(rowLp);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            row.addView(buttons[i], lp);
        }
        return row;
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams frameFull() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams compact() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(8), 0, 0, 0);
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String str(EditText e, String d) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? d : s;
    }

    private int num(EditText e, int d) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (Exception ex) {
            return d;
        }
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
        }
    }

    private void copyAssetFolder(String assetPath, File outBase) throws IOException {
        String[] list = getAssets().list(assetPath);
        File out = new File(outBase, assetPath);
        out.mkdirs();
        if (list == null) return;
        for (String name : list) {
            String child = assetPath + "/" + name;
            String[] sub = getAssets().list(child);
            if (sub != null && sub.length > 0) copyAssetFolder(child, outBase);
            else copyAsset(child, new File(out, name));
        }
    }

    private void copyAsset(String asset, File out) throws IOException {
        out.getParentFile().mkdirs();
        try (InputStream in = getAssets().open(asset); OutputStream os = new FileOutputStream(out)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) os.write(b, 0, n);
        }
        out.setReadable(true, false);
        out.setExecutable(true, false);
    }

    private static class BackendInfo {
        boolean installed, outdated;
        int installedCode, assetCode;
    }

    private static class MappingRow {
        MaterialCardView row;
        MaterialButton inputButton;
        AutoCompleteTextView target;
        MappingRow(MaterialCardView r, MaterialButton b, AutoCompleteTextView t) {
            row = r;
            inputButton = b;
            target = t;
        }
    }

    private static class DeviceInfo {
        String path, name, block;
        DeviceInfo(String p, String n, String b) {
            path = p;
            name = n;
            block = b;
        }
    }

    private static class EventLine {
        String type, code;
        int value;
        EventLine(String t, String c, int v) {
            type = t;
            code = c;
            value = v;
        }
    }

    private static class DetectedInput {
        String input, deviceName;
        DetectedInput(String i, String d) {
            input = i;
            deviceName = d;
        }
    }

    private static class AxisSample {
        String axis;
        int value, sign, center, min, max;
        float normalized;
        boolean hasRange;
        AxisSample(String a, int v, int s, float n, int c, int mn, int mx, boolean h) {
            axis = a;
            value = v;
            sign = s;
            normalized = n;
            center = c;
            min = mn;
            max = mx;
            hasRange = h;
        }
    }

    private static class AxisInfo {
        String axis;
        int center, min, max, flat;
        boolean hasRange;
        AxisInfo(String a, int c, int n, int x, int f, boolean h) {
            axis = a;
            center = c;
            min = n;
            max = x;
            flat = f;
            hasRange = h;
        }
    }

    private static class AxisCalibration {
        String axis;
        int center, min, max;
        AxisCalibration(String a, int c, int n, int x) {
            axis = a;
            center = c;
            min = n;
            max = x;
        }
    }

    private static class MapEntry {
        String input, target;
        MapEntry(String i, String t) {
            input = i;
            target = t;
        }
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("input", input);
            o.put("target", target);
            return o;
        }
        static MapEntry fromJson(JSONObject o) {
            return new MapEntry(o.optString("input"), o.optString("target"));
        }
    }

    private static class Preset {
        String name = "Preset", sourceName = "Xbox Wireless Controller", mouseAxisX = "ABS_Z", mouseAxisY = "ABS_RZ", lastAppliedHash = "";
        int version = 0, centerX = 0, centerY = 0, mouseMinX = -32768, mouseMaxX = 32767, mouseMinY = -32768, mouseMaxY = 32767, deadzone = 200, speed = 1, intervalMs = 8;
        ArrayList<MapEntry> maps = new ArrayList<>();

        static Preset create(String name) {
            Preset p = new Preset();
            p.name = name;
            p.maps.add(new MapEntry("axis:ABS_HAT0X:-", "KEY_A"));
            p.maps.add(new MapEntry("axis:ABS_HAT0X:+", "KEY_D"));
            p.maps.add(new MapEntry("axis:ABS_HAT0Y:-", "KEY_W"));
            p.maps.add(new MapEntry("axis:ABS_HAT0Y:+", "KEY_S"));
            return p;
        }

        String toConfig() {
            StringBuilder sb = new StringBuilder();
            sb.append("EVENT_NAME=\"").append(sourceName).append("\"\nOUTPUT_NAME=\"RG505 D-pad WASD\"\nOUTPUT_MOUSE_NAME=\"RG505 Mapper Mouse\"\nDEBUG=0\n");
            sb.append("MOUSE_AXIS_X=\"").append(mouseAxisX).append("\"\nMOUSE_AXIS_Y=\"").append(mouseAxisY).append("\"\nMOUSE_CENTER_X=").append(centerX).append("\nMOUSE_CENTER_Y=").append(centerY).append("\nMOUSE_MIN_X=").append(mouseMinX).append("\nMOUSE_MAX_X=").append(mouseMaxX).append("\nMOUSE_MIN_Y=").append(mouseMinY).append("\nMOUSE_MAX_Y=").append(mouseMaxY).append("\nMOUSE_DEADZONE=").append(deadzone).append("\nMOUSE_SPEED=").append(speed).append("\nMOUSE_INTERVAL_MS=").append(intervalMs).append("\n");
            for (int i = 0; i < maps.size(); i++) sb.append("MAP_").append(i + 1).append("=\"").append(maps.get(i).input).append(":").append(maps.get(i).target).append("\"\n");
            return sb.toString();
        }

        String contentHash() {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] h = md.digest(toConfig().getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format(Locale.US, "%02x", b));
                return sb.toString();
            } catch (Exception e) {
                return String.valueOf(toConfig().hashCode());
            }
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("version", version);
            o.put("sourceName", sourceName);
            o.put("mouseAxisX", mouseAxisX);
            o.put("mouseAxisY", mouseAxisY);
            o.put("centerX", centerX);
            o.put("centerY", centerY);
            o.put("mouseMinX", mouseMinX);
            o.put("mouseMaxX", mouseMaxX);
            o.put("mouseMinY", mouseMinY);
            o.put("mouseMaxY", mouseMaxY);
            o.put("deadzone", deadzone);
            o.put("speed", speed);
            o.put("intervalMs", intervalMs);
            o.put("lastAppliedHash", lastAppliedHash);
            JSONArray a = new JSONArray();
            for (MapEntry m : maps) a.put(m.toJson());
            o.put("maps", a);
            return o;
        }

        static Preset fromJson(JSONObject o) {
            Preset p = new Preset();
            p.name = o.optString("name", "Preset");
            p.version = o.optInt("version", 0);
            p.sourceName = o.optString("sourceName", "Xbox Wireless Controller");
            p.mouseAxisX = o.optString("mouseAxisX", "ABS_Z");
            p.mouseAxisY = o.optString("mouseAxisY", "ABS_RZ");
            p.centerX = o.optInt("centerX", 0);
            p.centerY = o.optInt("centerY", 0);
            p.mouseMinX = o.optInt("mouseMinX", -32768);
            p.mouseMaxX = o.optInt("mouseMaxX", 32767);
            p.mouseMinY = o.optInt("mouseMinY", -32768);
            p.mouseMaxY = o.optInt("mouseMaxY", 32767);
            p.deadzone = o.optInt("deadzone", 200);
            p.speed = o.optInt("speed", 1);
            p.intervalMs = o.optInt("intervalMs", 8);
            p.lastAppliedHash = o.optString("lastAppliedHash", "");
            JSONArray a = o.optJSONArray("maps");
            if (a != null) for (int i = 0; i < a.length(); i++) p.maps.add(MapEntry.fromJson(a.optJSONObject(i)));
            return p;
        }
    }
}
