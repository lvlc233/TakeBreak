package com.takeabreak.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BreakAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!context.getSharedPreferences("settings", 0).getBoolean("running", false)) return;
        context.startForegroundService(new Intent(context, BreakService.class).setAction(BreakService.ALARM));
    }
}
