package com.cls.morningalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    private static final String PREFS_NAME = "AlarmPrefs";
    private static final String KEY_HOUR = "alarm_hour";
    private static final String KEY_MINUTE = "alarm_minute";
    private static final String KEY_ENABLED = "alarm_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
            
            if (enabled) {
                int hour = prefs.getInt(KEY_HOUR, 7);
                int minute = prefs.getInt(KEY_MINUTE, 0);
                
                Intent alarmIntent = new Intent(context, AlarmReceiver.class);
                android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                        context, 0, alarmIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.setTimeInMillis(System.currentTimeMillis());
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
                calendar.set(java.util.Calendar.MINUTE, minute);
                calendar.set(java.util.Calendar.SECOND, 0);

                if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, 1);
                }

                android.app.AlarmManager alarmManager = (android.app.AlarmManager)
                        context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.setRepeating(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        android.app.AlarmManager.INTERVAL_DAY,
                        pendingIntent
                );
            }
        }
    }
}
