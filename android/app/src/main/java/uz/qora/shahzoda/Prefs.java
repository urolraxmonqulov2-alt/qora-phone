package uz.qora.shahzoda;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("qora", Context.MODE_PRIVATE);
    }

    static String key(Context c) {
        return sp(c).getString("groq_key", "").trim();
    }

    static void setKey(Context c, String k) {
        sp(c).edit().putString("groq_key", k == null ? "" : k.trim()).apply();
    }
}
