package com.takeabreak.app;

import android.app.KeyguardManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.LocaleList;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class BreakService extends Service {
    public static final String START = "com.takeabreak.START";
    public static final String STOP = "com.takeabreak.STOP";
    public static final String PAUSE = "com.takeabreak.PAUSE";
    public static final String RESUME = "com.takeabreak.RESUME";
    public static final String UNLOCK = "com.takeabreak.UNLOCK";
    public static final String ALARM = "com.takeabreak.ALARM";
    private static final String CHANNEL = "break_timer";
    private static final String ALERT_CHANNEL = "break_alert";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private AlarmManager alarms;
    private PowerManager.WakeLock wakeLock;
    private View overlay;
    private TextView countdown;
    private boolean running;
    private long usedMillis;
    private long cycleOffset;
    private long restDeadline;
    private long lastSampleElapsed;
    private boolean wasActive;
    private int lastNotifiedSecond = -1;
    private int workMinutes, restMinutes, requiredWords;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!running) return;
            sampleUsage();
            reconcile();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!Settings.canDrawOverlays(BreakService.this)) { stopTimer(); return; }
            sampleUsage();
            long now = System.currentTimeMillis();
            if (restDeadline > 0) {
                if (now >= restDeadline) reconcile();
                else if (countdown != null) countdown.setText("还有 " + ((restDeadline - now + 59999) / 60000) + " 分钟自动解锁");
            } else if (usedMillis >= workMinutes * 60_000L) reconcile();
            updateNotification();
            getSharedPreferences("settings", 0).edit().putLong("used", usedMillis).putLong("deadline", restDeadline).apply();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TakeABreak:Timer");
        wakeLock.setReferenceCounted(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "休息提醒", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(ALERT_CHANNEL, "到时休息", NotificationManager.IMPORTANCE_HIGH));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopTimer(); return START_NOT_STICKY; }
        if (intent != null && PAUSE.equals(intent.getAction())) { pauseTimer(); return START_NOT_STICKY; }
        if (intent != null && UNLOCK.equals(intent.getAction())) {
            if (running) { finishRest(); scheduleAlarm(); return START_STICKY; }
            stopSelf(); return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
        workMinutes = prefs.getInt("work", 30);
        restMinutes = prefs.getInt("rest", 15);
        requiredWords = prefs.getInt("words", 20);
        if (intent != null && START.equals(intent.getAction())) {
            finishRest();
            usedMillis = 0;
            cycleOffset = 0;
            restDeadline = 0;
        } else if (intent != null && RESUME.equals(intent.getAction())) {
            usedMillis = prefs.getLong("used", 0);
            cycleOffset = usedMillis;
            restDeadline = 0;
        } else {
            usedMillis = prefs.getLong("used", 0);
            cycleOffset = prefs.getLong("cycleOffset", 0);
            restDeadline = prefs.getLong("deadline", 0);
        }
        running = true;
        if (intent != null && (START.equals(intent.getAction()) || RESUME.equals(intent.getAction())))
            UsageClock.beginCycle(this, System.currentTimeMillis());
        lastSampleElapsed = SystemClock.elapsedRealtime();
        wasActive = isActive();
        prefs.edit().putBoolean("running", true).putBoolean("paused", false).putLong("cycleOffset", cycleOffset).apply();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setContentTitle("歇一会儿正在计时")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pending).setOngoing(true).build();
        startForeground(1, notification);
        if (!wakeLock.isHeld()) wakeLock.acquire();
        handler.removeCallbacks(tick);
        reconcile();
        handler.postDelayed(tick, 1000);
        return START_STICKY;
    }

    private PendingIntent alarmIntent() {
        return PendingIntent.getBroadcast(this, 10, new Intent(this, BreakAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleAlarm() {
        if (!running) return;
        long now = System.currentTimeMillis();
        long target = restDeadline > 0 ? restDeadline : now + Math.max(1000, workMinutes * 60_000L - usedMillis);
        long triggerElapsed = SystemClock.elapsedRealtime() + Math.max(1000, target - now);
        PendingIntent operation = alarmIntent();
        alarms.cancel(operation);
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, operation);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, operation);
        }
        getSharedPreferences("settings", 0).edit().putLong("nextAlarm", target).apply();
    }

    private void reconcile() {
        long now = System.currentTimeMillis();
        if (restDeadline > 0) {
            if (now >= restDeadline) finishRest();
            else showOverlay();
        } else {
            long measured = UsageClock.usedMillis(this, now);
            if (measured >= 0) usedMillis = cycleOffset + measured;
            if (usedMillis >= workMinutes * 60_000L) beginRest();
        }
        getSharedPreferences("settings", 0).edit().putLong("used", usedMillis).putLong("deadline", restDeadline).apply();
        scheduleAlarm();
    }

    private void sampleUsage() {
        long elapsed = SystemClock.elapsedRealtime();
        long delta = Math.max(0, elapsed - lastSampleElapsed);
        lastSampleElapsed = elapsed;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean interactive = power.isInteractive();
        boolean locked = keyguard.isKeyguardLocked();
        if (wasActive && restDeadline == 0) usedMillis += delta;
        wasActive = interactive && !locked && restDeadline == 0 && overlay == null;
        getSharedPreferences("settings", 0).edit()
                .putBoolean("diagInteractive", interactive)
                .putBoolean("diagLocked", locked)
                .putLong("diagDelta", delta).apply();
    }

    private boolean isActive() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return power.isInteractive() && !keyguard.isKeyguardLocked() && restDeadline == 0 && overlay == null;
    }

    private void beginRest() {
        usedMillis = 0;
        cycleOffset = 0;
        restDeadline = System.currentTimeMillis() + restMinutes * 60_000L;
        wasActive = false;
        if (getSharedPreferences("settings", 0).getBoolean("pauseVideo", false)) {
            String result = VideoPauser.pausePlayingVideos(this);
            getSharedPreferences("settings", 0).edit().putString("lastVideoPause", result).apply();
        }
        showOverlay();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(2, new Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("该休息了")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open).setAutoCancel(true).build());
    }

    private void updateNotification() {
        int seconds = (int) (usedMillis / 1000);
        if (seconds / 10 == lastNotifiedSecond / 10) return;
        lastNotifiedSecond = seconds;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String detail = restDeadline > 0 ? "休息中" : "已使用 " + (seconds / 60) + " 分 " + (seconds % 60) + " 秒 / " + workMinutes + " 分钟";
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1,
                new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("歇一会儿正在计时").setContentText(detail)
                        .setContentIntent(open).setOngoing(true).build());
    }

    private TextView text(LinearLayout layout, String value, int size, boolean bold) {
        TextView view = Ui.text(this, value, size, Ui.INK, bold);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = Ui.dp(this, 14);
        layout.addView(view, p); return view;
    }

    private void showOverlay() {
        if (overlay != null) return;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(Ui.dp(this, 26), Ui.dp(this, 38), Ui.dp(this, 26), Ui.dp(this, 38));
        panel.setBackgroundColor(Ui.PAPER);
        scroll.addView(panel);
        TextView mark = text(panel, "休息时间", 13, true);
        mark.setTextColor(Ui.GREEN);
        text(panel, "你现在觉得怎么样？", 29, true);
        TextView suggestion = text(panel, "这段时间里感觉还不错吗？来，深呼吸。这回不知道你是在娱乐，又或者在学习、工作，也不知道是否疲倦或是兴奋，但至少在这段时间里，应该属于你自己。", 18, false);
        suggestion.setTextColor(Ui.MUTED);
        countdown = text(panel, "", 17, true);
        countdown.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        countdown.setBackground(Ui.box(this, Ui.MINT, 16));
        TextView prompt = text(panel, "如果现在要做事情的话，让我们稍微复盘一下吧？你觉得如何呢？说说你现在的想法吧。", 16, true);
        LinearLayout.LayoutParams promptParams = (LinearLayout.LayoutParams) prompt.getLayoutParams();
        promptParams.topMargin = Ui.dp(this, 26); prompt.setLayoutParams(promptParams);
        EditText reflection = new EditText(this);
        reflection.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reflection.setImeHintLocales(new LocaleList(Locale.SIMPLIFIED_CHINESE));
        reflection.setTextSize(17);
        reflection.setMinLines(3);
        reflection.setGravity(Gravity.TOP);
        reflection.setHint("我现在……");
        reflection.setTextColor(Ui.INK);
        reflection.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        reflection.setBackground(Ui.box(this, Ui.WHITE, 16));
        panel.addView(reflection, new LinearLayout.LayoutParams(-1, Ui.dp(this, 128)));
        TextView progress = text(panel, "0 / " + requiredWords + " 字", 14, false);
        progress.setGravity(Gravity.END); progress.setTextColor(Ui.MUTED);
        TextView unlock = Ui.button(this, "我准备好了", Ui.GREEN, Ui.WHITE);
        unlock.setEnabled(false);
        unlock.setAlpha(0.45f);
        panel.addView(unlock, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
        reflection.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s.toString().trim();
                int length = value.codePointCount(0, value.length());
                progress.setText(length + " / " + requiredWords + " 字");
                unlock.setEnabled(length >= requiredWords);
                unlock.setAlpha(length >= requiredWords ? 1f : 0.45f);
            }
            public void afterTextChanged(Editable e) {}
        });
        unlock.setOnClickListener(v -> { finishRest(); scheduleAlarm(); });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { windows.addView(scroll, params); overlay = scroll; }
        catch (RuntimeException e) {
            getSharedPreferences("settings", 0).edit().putString("overlayError", e.getClass().getSimpleName() + ": " + e.getMessage()).apply();
        }
    }

    private void finishRest() {
        restDeadline = 0;
        usedMillis = 0;
        cycleOffset = 0;
        lastSampleElapsed = SystemClock.elapsedRealtime();
        if (overlay != null) {
            try { windows.removeView(overlay); } catch (RuntimeException ignored) {}
            overlay = null;
            countdown = null;
        }
        getSharedPreferences("settings", 0).edit().putLong("used", 0).putLong("deadline", 0).putLong("cycleOffset", 0).apply();
        wasActive = isActive();
        if (running) UsageClock.beginCycle(this, System.currentTimeMillis());
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2);
    }

    private void stopTimer() {
        running = false;
        handler.removeCallbacks(tick);
        alarms.cancel(alarmIntent());
        finishRest();
        getSharedPreferences("settings", 0).edit().putBoolean("running", false).putBoolean("paused", false).putLong("nextAlarm", 0).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (wakeLock.isHeld()) wakeLock.release();
        stopSelf();
    }

    private void pauseTimer() {
        android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
        if (running) sampleUsage();
        else {
            usedMillis = prefs.getLong("used", 0);
            cycleOffset = prefs.getLong("cycleOffset", 0);
        }
        long measured = UsageClock.usedMillis(this, System.currentTimeMillis());
        if (measured >= 0) usedMillis = cycleOffset + measured;
        running = false;
        handler.removeCallbacks(tick);
        alarms.cancel(alarmIntent());
        prefs.edit()
                .putBoolean("running", false).putBoolean("paused", true)
                .putLong("used", usedMillis).putLong("nextAlarm", 0).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopSelf();
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        unregisterReceiver(screenReceiver);
        if (overlay != null) { try { windows.removeView(overlay); } catch (RuntimeException ignored) {} overlay = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
