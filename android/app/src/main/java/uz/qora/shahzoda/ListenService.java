package uz.qora.shahzoda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;

/** Doim tinglovchi xizmat: «Qora shahzoda» deganini kutadi, keyin buyruqni bajaradi. */
public class ListenService extends Service {
    static volatile boolean running = false;

    private static final int WAKE = 0, COMMAND = 1, BUSY = 2;
    private static final String CH = "qora_listen";

    private final Handler h = new Handler(Looper.getMainLooper());
    private SpeechRecognizer sr;
    private int state = WAKE;
    private Engine eng;
    private View dot;
    private WindowManager wm;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH, "Tinglash", NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, CH)
                    .setContentTitle("Qora Shahzoda tinglayapti")
                    .setContentText("«Qora shahzoda» deb chaqiring")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(1, n);
            }
        } catch (Exception e) {
            Bus.log("[Xizmat] ishga tushmadi: " + e.getMessage());
            stopSelf();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Bus.log("[Xizmat] telefonda ovoz tanish xizmati yo'q (Google ilovasini o'rnating)");
            stopSelf();
            return;
        }
        running = true;
        eng = Engine.get(this);
        Speaker.get(this);
        addDot();
        Bus.log("[Tinglash] yoqildi — «Qora shahzoda» deng");
        state = WAKE;
        listen(600);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        destroyRecognizer();
        removeDot();
        Speaker.get(this).stop();
        Bus.log("[Tinglash] o'chirildi");
        super.onDestroy();
    }

    // ------------------------------------------------------------ indikator nuqta
    private void addDot() {
        try {
            if (!Settings.canDrawOverlays(this)) return;
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFFD4AF37);
            v.setBackground(g);
            int s = (int) (8 * getResources().getDisplayMetrics().density);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(s, s,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = s;
            lp.y = s;
            wm.addView(v, lp);
            dot = v;
        } catch (Exception ignored) {
            dot = null;
        }
    }

    private void setDot(int color) {
        try {
            if (dot != null) ((GradientDrawable) dot.getBackground()).setColor(color);
        } catch (Exception ignored) {
        }
    }

    private void removeDot() {
        try {
            if (dot != null && wm != null) wm.removeView(dot);
        } catch (Exception ignored) {
        }
        dot = null;
    }

    // ------------------------------------------------------------ tinglash sikli
    private void destroyRecognizer() {
        try {
            if (sr != null) {
                sr.cancel();
                sr.destroy();
            }
        } catch (Exception ignored) {
        }
        sr = null;
    }

    private void listen(long delayMs) {
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running || state == BUSY) return;
                try {
                    if (sr == null) {
                        sr = SpeechRecognizer.createSpeechRecognizer(ListenService.this);
                        sr.setRecognitionListener(listener);
                    }
                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ");
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uz-UZ");
                    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
                    setDot(state == COMMAND ? 0xFF4FC3F7 : 0xFFD4AF37);
                    sr.startListening(i);
                } catch (Exception e) {
                    destroyRecognizer();
                    listen(2000);
                }
            }
        }, delayMs);
    }

    private void process(String cmd) {
        state = BUSY;
        setDot(0xFFE57373);
        eng.submit(cmd, new Runnable() {
            @Override
            public void run() {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        state = eng.isPending() ? COMMAND : WAKE;
                        listen(300);
                    }
                });
            }
        });
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override
        public void onError(int error) {
            if (state == BUSY) return;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    if (state == COMMAND) state = WAKE;
                    listen(150);
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    destroyRecognizer();
                    listen(900);
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    Bus.log("[Tinglash] mikrofon ruxsati yo'q");
                    stopSelf();
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    listen(500);
                    break;
                default:
                    destroyRecognizer();
                    listen(2500);
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r == null || r.isEmpty()) {
                if (state == COMMAND) state = WAKE;
                listen(150);
                return;
            }
            if (state == COMMAND) {
                String cmd = r.get(0);
                String rest = Text.afterWake(cmd);
                if (rest != null) cmd = rest;
                if (cmd.trim().isEmpty()) {
                    state = WAKE;
                    listen(150);
                } else {
                    process(cmd);
                }
                return;
            }
            // WAKE holati
            for (String cand : r) {
                String rest = Text.afterWake(cand);
                if (rest == null) continue;
                if (rest.trim().isEmpty()) {
                    state = BUSY;
                    Bus.log("Qora: Eshitaman");
                    Speaker.get(ListenService.this).say("Eshitaman", new Runnable() {
                        @Override
                        public void run() {
                            h.post(new Runnable() {
                                @Override
                                public void run() {
                                    state = COMMAND;
                                    listen(100);
                                }
                            });
                        }
                    });
                } else {
                    process(rest);
                }
                return;
            }
            listen(100);
        }
    };
}
