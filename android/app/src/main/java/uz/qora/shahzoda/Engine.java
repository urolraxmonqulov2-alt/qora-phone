package uz.qora.shahzoda;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Markaziy miya: buyruqni tushunadi, tasdiq kutadi, kerak bo'lsa Groq'dan so'raydi, javobni o'qiydi. */
final class Engine {
    private static Engine I;

    static synchronized Engine get(Context c) {
        if (I == null) I = new Engine(c.getApplicationContext());
        return I;
    }

    private final Context c;
    private final Router router;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile Router.Out pending;
    private volatile long pendingAt;
    private final List<String[]> hist = new ArrayList<>();

    private Engine(Context c) {
        this.c = c;
        router = new Router(c);
    }

    /** Tasdiq javobi (ha/yo'q) kutilyaptimi? */
    boolean isPending() {
        return pending != null && System.currentTimeMillis() - pendingAt < 30000;
    }

    void submit(final String text, final Runnable done) {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                String reply;
                try {
                    reply = handle(text);
                } catch (Throwable t) {
                    reply = "Xatolik: " + t.getMessage();
                }
                Bus.log("Qora: " + reply);
                Speaker.get(c).say(reply, done);
            }
        });
    }

    private String handle(String text) {
        Bus.log("Siz: " + text);
        String n = Text.norm(text);

        Router.Out p = pending;
        if (p != null) {
            pending = null;
            if (System.currentTimeMillis() - pendingAt < 30000) {
                if (Text.isYes(n)) return p.onYes.run();
                if (Text.isNo(n)) return "Bekor qilindi.";
            }
        }

        Router.Out o = router.route(text);
        if (o != null) {
            if (o.confirm != null) {
                pending = o;
                pendingAt = System.currentTimeMillis();
                return o.confirm;
            }
            return o.reply;
        }
        return chat(text);
    }

    private String chat(String q) {
        String ans = Groq.chat(Prefs.key(c), hist, q);
        if (ans == null) return Groq.explain();
        ans = ans.replace("*", "").replace("#", "").replace("`", "").trim();
        hist.add(new String[]{"user", q});
        hist.add(new String[]{"assistant", ans});
        while (hist.size() > 8) hist.remove(0);
        return ans;
    }
}
