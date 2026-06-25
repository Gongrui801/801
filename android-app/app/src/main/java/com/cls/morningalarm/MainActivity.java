package com.cls.morningalarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.IOException;
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
    private TextView playModeText;

    private SharedPreferences prefs;
    private BriefingManager briefingManager;
    private TTSManager ttsManager;
    private MediaPlayer mediaPlayer;
    private BriefingManager.BriefingData currentBriefing;
    private boolean isPlaying = false;
    private boolean useAudio = false;

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
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                isPlaying = false;
                playBtn.setEnabled(true);
                stopBtn.setEnabled(false);
            }
        });
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
        playBtn.setEnabled(false);
        stopBtn.setEnabled(false);

        briefingManager.fetchMorningBriefing(new BriefingManager.BriefingCallback() {
            @Override
            public void onSuccess(BriefingManager.BriefingData data) {
                currentBriefing = data;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        briefingTitle.setText(data.title);
                        briefingDate.setText(data.date);
                        
                        String displayContent = data.content;
                        if (data.audioUrl != null && !data.audioUrl.isEmpty()) {
                            displayContent += "\n\n🎵 已获取原声播报音频";
                            useAudio = true;
                            playBtn.setText("播放原声早报");
                        } else {
                            useAudio = false;
                            playBtn.setText("语音播报");
                        }
                        
                        briefingContent.setText(displayContent);
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
                        briefingContent.setText("获取失败: " + error + "\n\n将使用备用内容");
                        currentBriefing = new BriefingManager.BriefingData();
                        currentBriefing.title = "财经早报";
                        currentBriefing.date = "";
                        currentBriefing.content = getFallbackBriefing();
                        currentBriefing.audioUrl = "";
                        useAudio = false;
                        playBtn.setText("语音播报");
                        playBtn.setEnabled(true);
                        fetchBriefingBtn.setEnabled(true);
                    }
                });
            }
        });
    }

    private String getFallbackBriefing() {
        return "早上好，现在为您播报财经早报。" +
                "市场概述：昨日A股市场整体震荡运行，沪指小幅收涨，创业板指表现相对较弱。" +
                "宏观经济：国家经济保持稳步复苏态势，主要经济指标持续改善。" +
                "行业动态：新能源汽车产业持续向好，多家车企销量同比大幅增长。" +
                "公司要闻：多家上市公司发布重要公告，投资者需关注相关影响。" +
                "海外市场：美股昨夜涨跌互现，科技股表现分化，市场关注美联储货币政策走向。" +
                "以上就是今日早报的主要内容，祝您一天愉快。";
    }

    private void playBriefing() {
        if (currentBriefing == null) return;

        if (useAudio && currentBriefing.audioUrl != null && !currentBriefing.audioUrl.isEmpty()) {
            playAudio(currentBriefing.audioUrl);
        } else {
            playTTS(currentBriefing.content);
        }

        isPlaying = true;
        playBtn.setEnabled(false);
        stopBtn.setEnabled(true);
    }

    private void playAudio(String url) {
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            if (currentBriefing != null && currentBriefing.content != null) {
                playTTS(currentBriefing.content);
            }
        }
    }

    private void playTTS(String text) {
        if (text != null && !text.isEmpty()) {
            ttsManager.speak(text);
        }
    }

    private void stopBriefing() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        ttsManager.stop();
        isPlaying = false;
        playBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        ttsManager.shutdown();
    }
}
