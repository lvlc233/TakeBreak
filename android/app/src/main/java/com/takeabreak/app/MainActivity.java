package com.takeabreak.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private EditText workInput, restInput, wordsInput;
    private TextView status, usage, homeAction, primary, homeTab, settingsTab, readyTab;
    private LinearLayout hero;
    private TimerRingView ring;
    private ObjectAnimator ringPulse;
    private ValueAnimator holdFill;
    private final ArrayList<Runnable> holdPulses = new ArrayList<>();
    private final Runnable holdFinish = this::completeHold;
    private final Runnable clearHoldRing = () -> { if (ring != null) ring.setHoldProgress(0f); };
    private static final long HOLD_MS = 1600;
    private boolean holdActive, holdCompleted, holdCanceled, holdEligible;
    private Vibrator vibrator;
    private int orbMode = -1;
    private TextView setupSummary, setupHint;
    private final TextView[] stepStatuses = new TextView[6];
    private ScrollView homePage, settingsPage, readyPage;
    private Switch pauseSwitch;
    private int pageIndex;
    private boolean setupActive;
    private int awaitingStep = -1;
    private final DeviceSetup device = DeviceSetup.current();
    private static final String[] STEP_TITLES = {"悬浮窗", "使用情况访问", "精确提醒", "电池优化", "通知", "视频控制"};
    private static final String[] STEP_DETAILS = {
            "让休息页盖住视频应用", "统计亮屏使用时间", "按时唤醒休息提醒",
            "减少后台计时被暂停", "显示正在计时的通知", "仅在开启视频暂停时需要"
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 31)
            vibrator = ((VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
        else vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        getWindow().setStatusBarColor(Ui.PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setNavigationBarColor(Ui.PAPER);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.PAPER);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 24));
        setContentView(root);

        add(root, Ui.text(this, "TAKE A BREATH", 12, Ui.GREEN, true), 0, 4);
        add(root, Ui.text(this, "歇一会儿", 32, Ui.INK, true), 0, 18);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(0);
        tabs.setBackground(Ui.box(this, Ui.MINT, 15));
        homeTab = tab("计时"); settingsTab = tab("设置"); readyTab = tab("准备");
        tabs.addView(homeTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        tabs.addView(settingsTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        tabs.addView(readyTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        add(root, tabs, 0, 14);
        homeTab.setOnClickListener(v -> showPage(0));
        settingsTab.setOnClickListener(v -> showPage(1));
        readyTab.setOnClickListener(v -> showPage(2));

        homePage = page(); settingsPage = page(); readyPage = page();
        root.addView(homePage, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(settingsPage, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(readyPage, new LinearLayout.LayoutParams(-1, 0, 1));
        buildHome((LinearLayout) homePage.getChildAt(0));
        buildSettings((LinearLayout) settingsPage.getChildAt(0));
        buildReady((LinearLayout) readyPage.getChildAt(0));

        primary = Ui.button(this, "开启休息提醒", Ui.GREEN, Ui.WHITE);
        primary.setTextSize(20); primary.setMinHeight(Ui.dp(this, 76));
        primary.setOnClickListener(v -> {
            if (pageIndex == 2) {
                if (isReady()) showPage(0);
                else beginSetup();
                return;
            }
            toggleTimer();
        });
        add(root, primary, 14, 0);
        showPage(0); refresh();
    }

    private void toggleTimer() {
        android.content.SharedPreferences p = getSharedPreferences("settings", 0);
        if (p.getBoolean("running", false)) pause();
        else if (p.getBoolean("paused", false)) resumeTimer();
        else start();
    }

    private TextView tab(String label) {
        TextView t = Ui.text(this, label, 15, Ui.INK, true);
        t.setGravity(Gravity.CENTER); t.setClickable(true); return t;
    }
    private ScrollView page() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(1);
        body.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 20));
        scroll.addView(body); return scroll;
    }
    private void showPage(int page) {
        if (page != 1 && workInput != null
                && !getSharedPreferences("settings", 0).getBoolean("running", false)
                && !getSharedPreferences("settings", 0).getBoolean("paused", false)) saveSettings();
        pageIndex = page;
        homePage.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        readyPage.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
        primary.setVisibility(page == 0 ? View.GONE : View.VISIBLE);
        homeTab.setBackground(page == 0 ? Ui.box(this, Ui.WHITE, 15) : null);
        settingsTab.setBackground(page == 1 ? Ui.box(this, Ui.WHITE, 15) : null);
        readyTab.setBackground(page == 2 ? Ui.box(this, Ui.WHITE, 15) : null);
        if (page != 0) stopRingPulse();
        refresh();
    }
    private LinearLayout card(int color) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(1);
        card.setPadding(Ui.dp(this, 21), Ui.dp(this, 22), Ui.dp(this, 21), Ui.dp(this, 22));
        card.setBackground(Ui.box(this, color, 22)); return card;
    }
    private void add(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = Ui.dp(this, top); p.bottomMargin = Ui.dp(this, bottom); parent.addView(child, p);
    }
    private void buildHome(LinearLayout body) {
        int diameter = Math.min(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48), Ui.dp(this, 332));
        FrameLayout orb = new FrameLayout(this);
        ring = new TimerRingView(this);
        orb.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER);
        hero.setPadding(Ui.dp(this, 26), 0, Ui.dp(this, 26), 0);
        hero.setFocusable(true);
        status = Ui.text(this, "启动", 34, Ui.WHITE, true);
        status.setGravity(Gravity.CENTER); add(hero, status, 0, 14);
        usage = Ui.text(this, "", 15, Ui.MINT, false);
        usage.setGravity(Gravity.CENTER); add(hero, usage, 0, 12);
        homeAction = Ui.text(this, "轻点开始", 15, Ui.WHITE, true);
        homeAction.setGravity(Gravity.CENTER); add(hero, homeAction, 0, 0);
        FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(diameter - Ui.dp(this, 20), diameter - Ui.dp(this, 20), Gravity.CENTER);
        orb.addView(hero, inner);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(diameter, diameter);
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        orbParams.topMargin = Ui.dp(this, 34); orbParams.bottomMargin = Ui.dp(this, 26);
        body.addView(orb, orbParams);
        hero.setClickable(true);
        hero.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                holdCompleted = false;
                holdCanceled = false;
                beginHold(event.getX(), event.getY());
            } else if (action == MotionEvent.ACTION_MOVE && holdActive) {
                int slack = Ui.dp(this, 32);
                if (event.getX() < -slack || event.getY() < -slack
                        || event.getX() > v.getWidth() + slack || event.getY() > v.getHeight() + slack) {
                    holdCanceled = true;
                    cancelHold();
                }
            } else if (action == MotionEvent.ACTION_UP) {
                boolean shortTap = holdActive && !holdCompleted && !holdCanceled;
                if (!holdCompleted) cancelHold();
                v.setPressed(false);
                if (!holdCompleted) releaseScale();
                if (shortTap) v.performClick();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelHold();
                v.setPressed(false);
                releaseScale();
            }
            return true;
        });
        hero.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            toggleTimer();
        });
        hero.setLongClickable(true);
        hero.setOnLongClickListener(v -> {
            beginHold(0, 0);
            if (holdEligible) completeHold();
            else { cancelHold(); v.performClick(); }
            return true;
        });
        TextView hold = Ui.text(this, "长按结束并清零", 14, Ui.MUTED, false);
        hold.setGravity(Gravity.CENTER); add(body, hold, 0, 24);
        add(body, Ui.text(this, "这是一个用来防止你刷刷手机的软件，献给无法停下来的我们。", 19, Ui.INK, false), 0, 0);
    }

    private void beginHold(float x, float y) {
        cancelHold();
        handler.removeCallbacks(clearHoldRing);
        holdActive = true;
        android.content.SharedPreferences p = getSharedPreferences("settings", 0);
        holdEligible = p.getBoolean("running", false) || p.getBoolean("paused", false);
        hero.setPressed(true);
        if (hero.getBackground() != null) hero.getBackground().setHotspot(x, y);
        hero.animate().cancel();
        hero.animate().scaleX(.955f).scaleY(.955f).setDuration(95).start();
        homeAction.setText(holdEligible ? "继续按住…" : "轻点开始");
        if (!holdEligible) return;
        holdFill = ValueAnimator.ofFloat(0f, 1f);
        holdFill.setDuration(HOLD_MS);
        holdFill.addUpdateListener(animation -> ring.setHoldProgress((float) animation.getAnimatedValue()));
        holdFill.start();
        long[] at = {300, 500, 680, 840, 980, 1100, 1200, 1280};
        int[] strength = {48, 56, 65, 75, 88, 101, 116, 132};
        for (int i = 0; i < at.length; i++) {
            final int amplitude = strength[i];
            Runnable pulse = () -> { if (holdActive) vibrate(25, amplitude); };
            holdPulses.add(pulse);
            handler.postDelayed(pulse, at[i]);
        }
        handler.postDelayed(holdFinish, HOLD_MS);
    }

    private void vibrate(long durationMs, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int strength = vibrator.hasAmplitudeControl() ? amplitude : VibrationEffect.DEFAULT_AMPLITUDE;
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, strength));
    }

    private void clearHoldCallbacks() {
        handler.removeCallbacks(holdFinish);
        for (Runnable pulse : holdPulses) handler.removeCallbacks(pulse);
        holdPulses.clear();
        if (holdFill != null) { holdFill.cancel(); holdFill = null; }
    }

    private void cancelHold() {
        if (!holdActive) return;
        holdActive = false;
        clearHoldCallbacks();
        ring.setHoldProgress(0f);
        if (vibrator != null) vibrator.cancel();
        refresh();
    }

    private void completeHold() {
        if (!holdActive || !holdEligible) return;
        holdActive = false;
        holdCompleted = true;
        clearHoldCallbacks();
        ring.setHoldProgress(1f);
        hero.setPressed(false);
        vibrate(165, 220);
        stop();
        hero.animate().cancel();
        hero.animate().scaleX(1.035f).scaleY(1.035f).setDuration(120)
                .withEndAction(this::releaseScale).start();
        handler.postDelayed(clearHoldRing, 440);
    }

    private void releaseScale() {
        hero.animate().cancel();
        hero.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.7f)).start();
    }

    private void buildSettings(LinearLayout body) {
        add(body, Ui.text(this, "设定节奏", 22, Ui.INK, true), 0, 14);
        LinearLayout timing = card(Ui.WHITE);
        workInput = number(timing, "多久提醒一次", "分钟 · 累计亮屏时间（1–240 分钟）", "work", 30);
        restInput = number(timing, "休息多久", "分钟 · 到时自动解锁（1–120 分钟）", "rest", 15);
        wordsInput = number(timing, "提前解锁写多少字", "字 · 可用输入法语音转文字（1–200 字）", "words", 20);
        add(body, timing, 0, 20);
        add(body, Ui.text(this, "运行中会锁定时长设置；停止提醒后可调整。", 13, Ui.MUTED, false), 0, 18);

        LinearLayout media = card(Ui.WHITE);
        add(media, Ui.text(this, "休息时暂停视频", 18, Ui.INK, true), 0, 8);
        add(media, Ui.text(this, "支持 Bilibili、YouTube、抖音、快手；不会向网易云音乐等音乐应用发送暂停。", 14, Ui.MUTED, false), 0, 12);
        pauseSwitch = new Switch(this); pauseSwitch.setText("开启视频暂停");
        pauseSwitch.setTextColor(Ui.INK); pauseSwitch.setTextSize(16);
        pauseSwitch.setChecked(getSharedPreferences("settings", 0).getBoolean("pauseVideo", false));
        pauseSwitch.setOnCheckedChangeListener((v, checked) -> {
            getSharedPreferences("settings", 0).edit().putBoolean("pauseVideo", checked).apply();
            refresh();
        });
        add(media, pauseSwitch, 0, 10);
        add(media, Ui.text(this, "视频控制需要单独授权。安卓会授予读取所有通知的能力；本应用只查询媒体播放会话，不读取或保存通知内容。到「准备」页查看权限。", 12, Ui.MUTED, false), 0, 0);
        add(body, media, 0, 0);
    }
    private void buildReady(LinearLayout body) {
        LinearLayout overview = card(Ui.INK);
        add(overview, Ui.text(this, "运行准备", 14, Ui.MINT, true), 0, 12);
        setupSummary = Ui.text(this, "正在检查…", 25, Ui.WHITE, true);
        add(overview, setupSummary, 0, 12);
        setupHint = Ui.text(this, "点下方按钮，按顺序打开需要授权的系统页面。", 14, Ui.MINT, false);
        add(overview, setupHint, 0, 0);
        add(body, overview, 0, 20);

        add(body, Ui.text(this, "系统权限", 21, Ui.INK, true), 0, 10);
        add(body, Ui.text(this, "已开启的项目会自动跳过；返回应用后继续检查下一项。", 13, Ui.MUTED, false), 0, 12);
        for (int i = 0; i < STEP_TITLES.length; i++) {
            final int step = i;
            LinearLayout row = card(Ui.WHITE);
            row.setPadding(Ui.dp(this, 17), Ui.dp(this, 14), Ui.dp(this, 17), Ui.dp(this, 14));
            add(row, Ui.text(this, STEP_TITLES[i], 16, Ui.INK, true), 0, 3);
            add(row, Ui.text(this, STEP_DETAILS[i], 12, Ui.MUTED, false), 0, 6);
            stepStatuses[i] = Ui.text(this, "正在检查…", 13, Ui.GREEN, true);
            add(row, stepStatuses[i], 0, 0);
            row.setClickable(true); row.setOnClickListener(v -> openStep(step, false));
            add(body, row, 0, 8);
        }

        add(body, Ui.text(this, device.brand + " 后台设置", 21, Ui.INK, true), 16, 10);
        LinearLayout vendor = card(Ui.WHITE);
        add(vendor, Ui.text(this, device.path, 15, Ui.INK, false), 0, 15);
        add(vendor, Ui.text(this, "这项由手机厂商管理。建议按上方路径检查，系统权限完成后即可开始计时。", 12, Ui.MUTED, false), 0, 12);
        TextView open = Ui.button(this, "打开本机应用设置", Ui.MINT, Ui.INK);
        open.setOnClickListener(v -> openVendorSettings()); add(vendor, open, 0, 0);
        add(body, vendor, 0, 0);
    }
    private EditText number(LinearLayout parent, String title, String hint, String key, int fallback) {
        add(parent, Ui.text(this, title, 16, Ui.INK, true), 0, 2);
        add(parent, Ui.text(this, hint, 12, Ui.MUTED, false), 0, 8);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setSingleLine(true);
        input.setText(String.valueOf(getSharedPreferences("settings", 0).getInt(key, fallback)));
        input.setTextSize(19); input.setTextColor(Ui.INK);
        input.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        input.setBackground(Ui.box(this, Ui.PAPER, 12));
        add(parent, input, 0, 18); return input;
    }
    private int read(EditText input, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(input.getText().toString()))); }
        catch (NumberFormatException e) { return min; }
    }
    private void saveSettings() {
        int work = read(workInput, 1, 240);
        int rest = read(restInput, 1, 120);
        int words = read(wordsInput, 1, 200);
        if (!workInput.getText().toString().equals(String.valueOf(work))) workInput.setText(String.valueOf(work));
        if (!restInput.getText().toString().equals(String.valueOf(rest))) restInput.setText(String.valueOf(rest));
        if (!wordsInput.getText().toString().equals(String.valueOf(words))) wordsInput.setText(String.valueOf(words));
        getSharedPreferences("settings", 0).edit().putInt("work", work).putInt("rest", rest).putInt("words", words).apply();
    }
    private void openMediaAccess() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 30) {
            intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            intent.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new android.content.ComponentName(this, MediaAccessService.class).flattenToString());
        } else intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException e) { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
    }
    private boolean relevant(int step) {
        if (step == 2) return Build.VERSION.SDK_INT >= 31;
        if (step == 4) return Build.VERSION.SDK_INT >= 33;
        if (step == 5) return pauseSwitch.isChecked();
        return true;
    }
    private boolean granted(int step) {
        switch (step) {
            case 0: return Settings.canDrawOverlays(this);
            case 1: return UsageClock.hasAccess(this);
            case 2: return Build.VERSION.SDK_INT < 31 || ((AlarmManager) getSystemService(ALARM_SERVICE)).canScheduleExactAlarms();
            case 3: return ((PowerManager) getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());
            case 4: return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            case 5: return VideoPauser.hasAccess(this);
            default: return true;
        }
    }
    private boolean isReady() {
        for (int i = 0; i < STEP_TITLES.length; i++) if (relevant(i) && !granted(i)) return false;
        return true;
    }
    private void beginSetup() {
        setupActive = true;
        awaitingStep = -1;
        showPage(2);
        continueSetup();
    }
    private void continueSetup() {
        if (!setupActive) return;
        refresh();
        for (int i = 0; i < STEP_TITLES.length; i++) {
            if (relevant(i) && !granted(i)) { openStep(i, true); return; }
        }
        setupActive = false;
        setupHint.setText("运行准备已完成。可以返回计时页开启提醒。若后台计时被手机暂停，请检查下方的" + device.brand + "后台设置。");
        refresh();
    }
    private void openStep(int step, boolean fromFlow) {
        if (!relevant(step)) { setupHint.setText("这一项当前不需要设置。"); return; }
        if (granted(step)) { setupHint.setText(STEP_TITLES[step] + "已开启。"); return; }
        setupActive = fromFlow;
        awaitingStep = fromFlow ? step : -1;
        setupHint.setText("请在系统页面开启「" + STEP_TITLES[step] + "」，然后返回应用。");
        try {
            Intent intent;
            switch (step) {
                case 0:
                    intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())); break;
                case 1:
                    intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS); break;
                case 2:
                    intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())); break;
                case 3:
                    intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())); break;
                case 4:
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41); return;
                case 5:
                    openMediaAccess(); return;
                default: return;
            }
            startActivity(intent);
        } catch (RuntimeException e) {
            setupActive = false; awaitingStep = -1;
            setupHint.setText("无法打开这项系统设置，请点对应项目单独尝试。");
        }
    }
    private void openVendorSettings() {
        setupHint.setText("请按照下方路径检查 " + device.brand + " 的后台运行设置。");
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (RuntimeException e) { setupHint.setText("无法直达应用设置，请在手机设置中搜索「歇一会儿」。"); }
    }
    private void start() {
        for (int i = 0; i < STEP_TITLES.length; i++) {
            if (relevant(i) && !granted(i)) { beginSetup(); return; }
        }
        saveSettings();
        startForegroundService(new Intent(this, BreakService.class).setAction(BreakService.START));
        getSharedPreferences("settings", 0).edit().putBoolean("running", true).putBoolean("paused", false).apply(); refresh();
    }
    private void pause() {
        startService(new Intent(this, BreakService.class).setAction(BreakService.PAUSE));
        getSharedPreferences("settings", 0).edit().putBoolean("running", false).putBoolean("paused", true).apply(); refresh();
    }
    private void resumeTimer() {
        startForegroundService(new Intent(this, BreakService.class).setAction(BreakService.RESUME));
        getSharedPreferences("settings", 0).edit().putBoolean("running", true).putBoolean("paused", false).apply(); refresh();
    }
    private void stop() {
        startService(new Intent(this, BreakService.class).setAction(BreakService.STOP));
        getSharedPreferences("settings", 0).edit().putBoolean("running", false).putBoolean("paused", false).putLong("used", 0).apply(); refresh();
    }
    private void refresh() {
        if (status == null) return;
        android.content.SharedPreferences p = getSharedPreferences("settings", 0);
        boolean running = p.getBoolean("running", false);
        boolean paused = p.getBoolean("paused", false);
        long deadline = p.getLong("deadline", 0);
        status.setText(deadline > System.currentTimeMillis() ? "休息一下下" : running ? "正在计时" : paused ? "已暂停" : "启动");
        int work = p.getInt("work", 30);
        long used = p.getLong("used", 0);
        usage.setVisibility(running || paused ? View.VISIBLE : View.GONE);
        usage.setText((running || paused) ? "已使用 " + (used / 1000) + " 秒 / " + work + " 分钟" : "");
        homeAction.setText(holdActive && holdEligible ? "继续按住…" : running ? "轻点暂停" : paused ? "轻点继续" : "轻点开始");
        int mode = deadline > System.currentTimeMillis() ? 3 : running ? 2 : paused ? 1 : 0;
        if (orbMode != mode) {
            orbMode = mode;
            int color = mode == 1 ? Color.rgb(65, 78, 84) : mode == 2 ? Ui.GREEN : Ui.INK;
            hero.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                    Ui.circle(color), Ui.circle(Color.WHITE)));
        }
        ring.setProgress(mode == 3 ? 1f : used / (work * 60_000f), mode == 1);
        hero.setContentDescription(status.getText() + "。" + usage.getText() + "。" + homeAction.getText() + "。长按结束并清零");
        if (pageIndex == 0 && mode == 2) startRingPulse();
        else stopRingPulse();
        boolean ready = isReady();
        primary.setText(pageIndex == 2 ? ready ? "准备已完成 · 返回计时" : "逐项配置运行准备" : running ? "暂停计时" : paused ? "继续计时" : "开启休息提醒");
        boolean pale = pageIndex != 2 && running;
        primary.setBackground(Ui.box(this, pale ? Ui.MINT : Ui.GREEN, 18));
        primary.setTextColor(pale ? Ui.INK : Ui.WHITE);
        if (setupSummary != null) {
            int total = 0, done = 0;
            for (int i = 0; i < STEP_TITLES.length; i++) if (relevant(i)) { total++; if (granted(i)) done++; }
            setupSummary.setText(ready ? "已就绪，可以开始" : done + " / " + total + " 项已完成");
            if (ready && !setupActive && awaitingStep < 0)
                setupHint.setText("运行准备已完成。可以返回计时页开启提醒。");
            for (int i = 0; i < STEP_TITLES.length; i++) {
                if (!relevant(i)) stepStatuses[i].setText(i == 5 ? "未启用视频暂停" : "本机无需设置");
                else stepStatuses[i].setText(granted(i) ? "✓ 已开启" : "待开启 · 点击前往");
            }
        }
        workInput.setEnabled(!running && !paused); restInput.setEnabled(!running && !paused); wordsInput.setEnabled(!running && !paused);
    }
    private void startRingPulse() {
        if (ringPulse != null) return;
        ringPulse = ObjectAnimator.ofFloat(ring, View.ALPHA, .65f, 1f);
        ringPulse.setDuration(1500);
        ringPulse.setRepeatCount(ValueAnimator.INFINITE);
        ringPulse.setRepeatMode(ValueAnimator.REVERSE);
        ringPulse.start();
    }
    private void stopRingPulse() {
        if (ringPulse != null) { ringPulse.cancel(); ringPulse = null; }
        if (ring != null) ring.setAlpha(1f);
    }
    private void returnedFromStep(int step) {
        if (awaitingStep != step) return;
        awaitingStep = -1;
        if (granted(step)) handler.postDelayed(this::continueSetup, 350);
        else {
            setupActive = false;
            setupHint.setText("「" + STEP_TITLES[step] + "」尚未开启。可以点下方按钮继续配置。");
            refresh();
        }
    }
    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTick); handler.post(refreshTick);
        if (awaitingStep >= 0 && awaitingStep != 4) {
            final int step = awaitingStep;
            handler.postDelayed(() -> returnedFromStep(step), 350);
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 41) returnedFromStep(4);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(refreshTick);
        cancelHold();
        stopRingPulse();
        if (!getSharedPreferences("settings", 0).getBoolean("running", false)
                && !getSharedPreferences("settings", 0).getBoolean("paused", false)) saveSettings();
        super.onPause();
    }

    @Override protected void onDestroy() {
        clearHoldCallbacks();
        handler.removeCallbacks(clearHoldRing);
        super.onDestroy();
    }
}
