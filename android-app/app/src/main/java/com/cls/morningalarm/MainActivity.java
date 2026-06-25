package com.cls.morningalarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "AlarmPrefs";
    private static final String KEY_HOUR = "alarm_hour";
    private static final String KEY_MINUTE = "alarm_minute";
    private static final String KEY_ENABLED = "alarm_enabled";
    private static final String KEY_REPEAT = "alarm_repeat";

    private TimePicker timePicker;
    private Switch alarmSwitch;
    private Switch repeatSwitch;
    private Button saveAlarmBtn;
    private Button fetchBriefingBtn;
    private Button playBtn;
    private Button stopBtn;
    private TextView briefingTitle;
    private TextView briefingDate;
    private TextView briefingContent;

    private SharedPreferences prefs;
    private BriefingManager briefingManager;
    private TTSManager ttsManager;
    private String briefingText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initManagers();
        loadPreferences();
    }

    private void initViews() {
        timePicker = (TimePicker) findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);
        alarmSwitch = (Switch) findViewById(R.id.alarmSwitch);
        repeatSwitch = (Switch) findViewById(R.id.repeatSwitch);
        saveAlarmBtn = (Button) findViewById(R.id.saveAlarmBtn);
        fetchBriefingBtn = (Button) findViewById(R.id.fetchBriefingBtn);
        playBtn = (Button) findViewById(R.id.playBtn);
        stopBtn = (Button) findViewById(R.id.stopBtn);
        briefingTitle = (TextView) findViewById(R.id.briefingTitle);
        briefingDate = (TextView) findViewById(R.id.briefingDate);
        briefingContent = (TextView) findViewById(R.id.briefingContent);

        saveAlarmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAlarm();
            }
        });
        fetchBriefingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchBriefing();
            }
        });
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playBriefing();
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopBriefing();
            }
        });
    }

    private void initManagers() {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        briefingManager = new BriefingManager();
        ttsManager = new TTSManager(this);
    }

    private void loadPreferences() {
        int hour = prefs.getInt(KEY_HOUR, 7);
        int minute = prefs.getInt(KEY_MINUTE, 0);
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        boolean repeat = prefs.getBoolean(KEY_REPEAT, true);

        timePicker.setHour(hour);
        timePicker.setMinute(minute);
        alarmSwitch.setChecked(enabled);
        repeatSwitch.setChecked(repeat);
    }

    private void saveAlarm() {
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();
        boolean enabled = alarmSwitch.isChecked();
        boolean repeat = repeatSwitch.isChecked();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_HOUR, hour);
        editor.putInt(KEY_MINUTE, minute);
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.putBoolean(KEY_REPEAT, repeat);
        editor.apply();

        if (enabled) {
            setAlarm(hour, minute, repeat);
            Toast.makeText(this, "闹钟已设置为 " + String.format("%02d:%02d", hour, minute), Toast.LENGTH_SHORT).show();
        } else {
            cancelAlarm();
            Toast.makeText(this, "闹钟已关闭", Toast.LENGTH_SHORT).show();
        }
    }

    private void setAlarm(int hour, int minute, boolean repeat) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (repeat) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        }
    }

    private void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    private void fetchBriefing() {
        fetchBriefingBtn.setEnabled(false);
        briefingContent.setText("加载中...");

        briefingManager.fetchMorningBriefing(new BriefingManager.BriefingCallback() {
            @Override
            public void onSuccess(final String title, final String date, final String content) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        briefingTitle.setText(title);
                        briefingDate.setText(date);
                        briefingContent.setText(content);
                        briefingText = content;
                        playBtn.setEnabled(true);
                        fetchBriefingBtn.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        briefingContent.setText("获取失败: " + error);
                        fetchBriefingBtn.setEnabled(true);
                    }
                });
            }
        });
    }

    private void playBriefing() {
        if (!briefingText.isEmpty()) {
            ttsManager.speak(briefingText);
            playBtn.setEnabled(false);
            stopBtn.setEnabled(true);
        }
    }

    private void stopBriefing() {
        ttsManager.stop();
        playBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ttsManager.shutdown();
    }
}
