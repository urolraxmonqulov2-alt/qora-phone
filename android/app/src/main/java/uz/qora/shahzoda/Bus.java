package uz.qora.shahzoda;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ekrandagi jurnal uchun oddiy xabar shinasi. */
final class Bus {
    private Bus() {}

    interface L {
        void on(String line);
    }

    private static final CopyOnWriteArrayList<L> LS = new CopyOnWriteArrayList<>();
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static final List<String> HIST = new ArrayList<>();

    static void add(L l) {
        LS.addIfAbsent(l);
    }

    static void remove(L l) {
        LS.remove(l);
    }

    static List<String> history() {
        synchronized (HIST) {
            return new ArrayList<>(HIST);
        }
    }

    static void log(final String s) {
        synchronized (HIST) {
            HIST.add(s);
            while (HIST.size() > 200) HIST.remove(0);
        }
        H.post(new Runnable() {
            @Override
            public void run() {
                for (L l : LS) l.on(s);
            }
        });
    }
}
