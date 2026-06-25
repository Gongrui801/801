package com.cls.morningalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmService extends Service {

    public static final String ACTION_START_ALARM = "com.cls.morningalarm.START_ALARM";
    public static final String ACTION_STOP_ALARM = "com.cls.morningalarm.STOP_ALARM";
    private static final String CHANNEL_ID = "morning_alarm_channel";
    private static final int NOTIFICATION_ID = 1001;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private TTSManager ttsManager;
    private BriefingManager briefingManager;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        ttsManager = new TTSManager(this);
        briefingManager = new BriefingManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_ALARM.equals(action)) {
                startForeground(NOTIFICATION_ID, buildNotification());
                startAlarm();
            } else if (ACTION_STOP_ALARM.equals(action)) {
                stopAlarm();
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startAlarm() {
        playAlarmSound();
        vibrate();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                stopAlarmSound();
                fetchAndPlayBriefing();
            }
        }, 10000);
    }

    private void playAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            ringtone = RingtoneManager.getRingtone(this, alarmUri);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }

            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            ringtone.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAlarmSound() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void vibrate() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = {0, 1000, 1000, 1000, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void fetchAndPlayBriefing() {
        briefingManager.fetchMorningBriefing(new BriefingManager.BriefingCallback() {
            @Override
            public void onSuccess(String title, String date, String content) {
                updateNotification(title, "正在播放早报...");
                ttsManager.speak(content);
            }

            @Override
            public void onError(String error) {
                String fallback = getFallbackBriefing();
                updateNotification("财经早报", "正在播放早报...");
                ttsManager.speak(fallback);
            }
        });
    }

    private String getFallbackBriefing() {
        return "早上好，现在为您播放财经早报。" +
                "市场概述：昨日A股市场整体震荡运行，沪指小幅收涨，创业板指表现相对较弱。" +
                "宏观经济：国家经济保持稳步复苏态势，主要经济指标持续改善。" +
                "行业动态：新能源汽车产业持续向好，多家车企销量同比大幅增长。" +
                "公司要闻：多家上市公司发布重要公告，投资者需关注相关影响。" +
                "海外市场：美股昨夜涨跌互现，科技股表现分化，市场关注美联储货币政策走向。" +
                "以上就是今日早报的主要内容，祝您一天愉快。";
    }

    private void stopAlarm() {
        stopAlarmSound();
        if (ttsManager != null) {
            ttsManager.stop();
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(getString(R.string.alarm_notification_title))
                .setContentText(getString(R.string.alarm_notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        return builder.build();
    }

    private void updateNotification(String title, String text) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("财联社早报闹钟通知");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarm();
        if (ttsManager != null) {
            ttsManager.shutdown();
        }
    }
}
