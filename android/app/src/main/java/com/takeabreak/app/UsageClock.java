package com.takeabreak.app;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.Process;

final class UsageClock {
    private UsageClock() {}

    static boolean hasAccess(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    static void beginCycle(Context context, long now) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        context.getSharedPreferences("settings", 0).edit()
                .putLong("cycleStart", now)
                .putBoolean("cycleInteractive", power.isInteractive())
                .putBoolean("cycleUnlocked", !keyguard.isKeyguardLocked())
                .apply();
    }

    static long usedMillis(Context context, long now) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("settings", 0);
        long start = prefs.getLong("cycleStart", 0);
        if (start <= 0 || now <= start) return 0;
        if (!hasAccess(context)) return -1;
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = manager.queryEvents(start, now);
        if (events == null) return -1;
        boolean interactive = prefs.getBoolean("cycleInteractive", true);
        boolean unlocked = prefs.getBoolean("cycleUnlocked", true);
        long cursor = start, used = 0;
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            long at = Math.max(cursor, Math.min(now, event.getTimeStamp()));
            if (interactive && unlocked) used += at - cursor;
            cursor = at;
            switch (event.getEventType()) {
                case UsageEvents.Event.SCREEN_INTERACTIVE: interactive = true; break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE: interactive = false; break;
                case UsageEvents.Event.KEYGUARD_SHOWN: unlocked = false; break;
                case UsageEvents.Event.KEYGUARD_HIDDEN: unlocked = true; break;
                case UsageEvents.Event.DEVICE_SHUTDOWN: interactive = false; break;
                default: break;
            }
        }
        if (interactive && unlocked) used += now - cursor;
        return Math.max(0, used);
    }
}
