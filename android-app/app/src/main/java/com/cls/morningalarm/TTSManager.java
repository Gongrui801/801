package com.cls.morningalarm;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.Locale;

public class TTSManager {

    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Context context;

    public TTSManager(Context context) {
        this.context = context;
        tts = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.CHINESE);
                    if (result != TextToSpeech.LANG_MISSING_DATA
                            && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        isInitialized = true;
                        tts.setPitch(1.0f);
                        tts.setSpeechRate(1.0f);
                    }
                }
            }
        });
    }

    public void speak(String text) {
        if (isInitialized && tts != null && text != null && !text.isEmpty()) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "morning_briefing");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    public boolean isSpeaking() {
        return tts != null && tts.isSpeaking();
    }

    public void setSpeechRate(float rate) {
        if (tts != null) {
            tts.setSpeechRate(rate);
        }
    }

    public void setPitch(float pitch) {
        if (tts != null) {
            tts.setPitch(pitch);
        }
    }
}
