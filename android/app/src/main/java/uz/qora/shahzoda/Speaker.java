package uz.qora.shahzoda;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Ovozli javob (TextToSpeech). O'zbek ovozi bo'lmasa turkchaga o'tadi. */
final class Speaker {
    private static Speaker I;

    static synchronized Speaker get(Context c) {
        if (I == null) I = new Speaker(c.getApplicationContext());
        return I;
    }

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private final Map<String, Runnable> done = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    private Speaker(Context c) {
        tts = new TextToSpeech(c, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    Bus.log("[TTS] ishga tushmadi");
                    return;
                }
                Locale[] tries = {new Locale("uz", "UZ"), new Locale("uz"), new Locale("tr", "TR")};
                boolean ok = false;
                for (int i = 0; i < tries.length; i++) {
                    int r = tts.setLanguage(tries[i]);
                    if (r >= TextToSpeech.LANG_AVAILABLE) {
                        ok = true;
                        if (i == 2) Bus.log("[TTS] o'zbek ovozi yo'q, turkcha ishlatilmoqda");
                        break;
                    }
                }
                if (!ok) Bus.log("[TTS] mos til topilmadi, standart ovoz");
                tts.setPitch(0.65f);
                tts.setSpeechRate(0.9f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { fire(id); }
                    @Override public void onError(String id) { fire(id); }
                });
                ready = true;
            }
        });
    }

    private void fire(String id) {
        Runnable r = done.remove(id);
        if (r != null) r.run();
    }

    /** Matnni o'qiydi; tugagach onDone chaqiriladi (xato bo'lsa ham). */
    void say(String text, Runnable onDone) {
        if (text == null || text.trim().isEmpty() || !ready) {
            if (onDone != null) onDone.run();
            return;
        }
        String id = "u" + ids.incrementAndGet();
        if (onDone != null) done.put(id, onDone);
        Bundle b = new Bundle();
        int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, b, id);
        if (r != TextToSpeech.SUCCESS) fire(id);
    }

    void stop() {
        try { tts.stop(); } catch (Exception ignored) {}
    }
}
