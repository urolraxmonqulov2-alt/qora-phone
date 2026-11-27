package uz.qora.shahzoda;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Telefon amallari: ilovalar, chiroq, ovoz, yorqinlik, qo'ng'iroq, budilnik va h.k. */
final class Phone {
    private final Context c;

    Phone(Context ctx) {
        c = ctx.getApplicationContext();
    }

    boolean has(String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    boolean start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- chiroq
    boolean torch(boolean on) {
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean f = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    cm.setTorchMode(id, on);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ---------------------------------------------------------------- ovoz
    private AudioManager am() {
        return (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    String volumeStep(boolean up) {
        AudioManager a = am();
        for (int i = 0; i < 3; i++) {
            a.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    i == 2 ? AudioManager.FLAG_SHOW_UI : 0);
        }
        return up ? "Ovozni oshirdim." : "Ovozni kamaytirdim.";
    }

    String volumeSet(int pct) {
        AudioManager a = am();
        int max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        pct = Math.max(0, Math.min(100, pct));
        a.setStreamVolume(AudioManager.STREAM_MUSIC, max * pct / 100, AudioManager.FLAG_SHOW_UI);
        return "Ovoz " + pct + " foiz.";
    }

    // ---------------------------------------------------------------- yorqinlik
    int brightnessNow() {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) * 100 / 255;
        } catch (Exception e) {
            return 50;
        }
    }

    /** null = muvaffaqiyat, aks holda xato matni. */
    String brightnessSet(int pct) {
        if (!Settings.System.canWrite(c)) {
            return "Yorqinlik uchun «Tizim sozlamalarini o'zgartirish» ruxsati kerak. Sozlamalardan bering.";
        }
        pct = Math.max(1, Math.min(100, pct));
        ContentResolver r = c.getContentResolver();
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, Math.max(1, pct * 255 / 100));
        return "Yorqinlik " + pct + " foiz.";
    }

    // ---------------------------------------------------------------- batareya / vaqt
    String battery() {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "Batareya holatini bilolmadim.";
        int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int pct = lvl * 100 / Math.max(1, sc);
        boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        return "Batareya " + pct + " foiz" + (ch ? ", zaryadlanmoqda." : ".");
    }

    String time() {
        return "Soat " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date()) + ".";
    }

    String date() {
        String[] oy = {"yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr",
                "oktabr", "noyabr", "dekabr"};
        String[] kun = {"yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba"};
        java.util.Calendar k = java.util.Calendar.getInstance();
        return "Bugun " + k.get(java.util.Calendar.DAY_OF_MONTH) + "-" + oy[k.get(java.util.Calendar.MONTH)]
                + ", " + kun[k.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ".";
    }

    // ---------------------------------------------------------------- ob-havo
    private static final Map<String, double[]> CITY = new HashMap<>();

    static {
        CITY.put("toshkent", new double[]{41.31, 69.24});
        CITY.put("samarqand", new double[]{39.65, 66.96});
        CITY.put("buhoro", new double[]{39.77, 64.42});
        CITY.put("andijon", new double[]{40.78, 72.34});
        CITY.put("namangan", new double[]{41.0, 71.67});
        CITY.put("fargona", new double[]{40.38, 71.78});
        CITY.put("nukus", new double[]{42.46, 59.61});
        CITY.put("qarshi", new double[]{38.86, 65.79});
        CITY.put("urganch", new double[]{41.55, 60.63});
        CITY.put("termiz", new double[]{37.22, 67.28});
        CITY.put("navoiy", new double[]{40.08, 65.38});
        CITY.put("jizzah", new double[]{40.12, 67.84});
    }

    private static String httpGet(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        InputStream in = con.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        con.disconnect();
        return bo.toString("UTF-8");
    }

    String weather(String normText) {
        String city = "Toshkent";
        double[] ll = CITY.get("toshkent");
        for (Map.Entry<String, double[]> e : CITY.entrySet()) {
            if (normText.contains(e.getKey().substring(0, Math.min(5, e.getKey().length())))) {
                ll = e.getValue();
                String k = e.getKey();
                city = Character.toUpperCase(k.charAt(0)) + k.substring(1);
                break;
            }
        }
        try {
            String s = httpGet("https://api.open-meteo.com/v1/forecast?latitude=" + ll[0] + "&longitude=" + ll[1]
                    + "&current=temperature_2m,weather_code&timezone=auto");
            JSONObject cur = new JSONObject(s).getJSONObject("current");
            double t = cur.getDouble("temperature_2m");
            int code = cur.getInt("weather_code");
            String d;
            if (code == 0) d = "ochiq";
            else if (code <= 3) d = "qisman bulutli";
            else if (code == 45 || code == 48) d = "tumanli";
            else if (code <= 67) d = "yomg'irli";
            else if (code <= 77) d = "qorli";
            else if (code <= 82) d = "jala";
            else d = "momaqaldiroqli";
            return city + "da hozir " + Math.round(t) + " daraja, " + d + ".";
        } catch (Exception e) {
            return "Ob-havo ma'lumotini olib bo'lmadi, internetni tekshiring.";
        }
    }

    // ---------------------------------------------------------------- media
    void mediaKey(int code) {
        AudioManager a = am();
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    // ---------------------------------------------------------------- budilnik / taymer
    boolean alarm(int h, int m) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return start(i);
    }

    boolean timer(int seconds) {
        Intent i = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return start(i);
    }

    // ---------------------------------------------------------------- kontakt / qo'ng'iroq / sms
    static List<String> variants(String name) {
        List<String> v = new ArrayList<>();
        name = name.trim();
        if (name.isEmpty()) return v;
        v.add(name);
        String[] suf = {"ning", "dan", "ga", "ka", "qa", "ni", "da"};
        for (String s : suf) {
            if (name.endsWith(s) && name.length() - s.length() >= 2) {
                v.add(name.substring(0, name.length() - s.length()));
            }
        }
        return v;
    }

    String findNumber(String name) {
        if (!has(android.Manifest.permission.READ_CONTACTS)) return null;
        for (String cand : variants(name)) {
            Cursor cu = null;
            try {
                cu = c.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                        new String[]{"%" + cand + "%"}, null);
                if (cu != null && cu.moveToFirst()) return cu.getString(0);
            } catch (Exception ignored) {
            } finally {
                if (cu != null) cu.close();
            }
        }
        return null;
    }

    boolean call(String number) {
        Uri u = Uri.parse("tel:" + Uri.encode(number));
        if (has(android.Manifest.permission.CALL_PHONE)) {
            return start(new Intent(Intent.ACTION_CALL, u));
        }
        return start(new Intent(Intent.ACTION_DIAL, u));
    }

    boolean smsComposer(String number, String body) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (number == null ? "" : Uri.encode(number))));
        i.putExtra("sms_body", body == null ? "" : body);
        return start(i);
    }

    // ---------------------------------------------------------------- veb / xarita / kamera / panel
    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    boolean youtube(String q) {
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(q))));
    }

    boolean google(String q) {
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + enc(q))));
    }

    boolean maps(String q) {
        return start(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(q))));
    }

    boolean camera() {
        return start(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
    }

    boolean panel(String what) {
        String a;
        switch (what) {
            case "wifi": a = Settings.Panel.ACTION_WIFI; break;
            case "internet": a = Settings.Panel.ACTION_INTERNET_CONNECTIVITY; break;
            case "bluetooth": a = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            default: a = Settings.ACTION_SETTINGS;
        }
        return start(new Intent(a));
    }

    // ---------------------------------------------------------------- ilovalar
    private static final Map<String, String[]> ALIAS = new HashMap<>();

    static {
        ALIAS.put("kamera", new String[]{"camera", "kamera"});
        ALIAS.put("galereya", new String[]{"gallery", "photos", "galereya", "rasmlar"});
        ALIAS.put("sozlamalar", new String[]{"settings", "sozlamalar"});
        ALIAS.put("sozlama", new String[]{"settings", "sozlamalar"});
        ALIAS.put("harita", new String[]{"maps", "harita"});
        ALIAS.put("telefon", new String[]{"phone", "telefon", "dialer"});
        ALIAS.put("kontaktlar", new String[]{"contacts", "kontaktlar"});
        ALIAS.put("habarlar", new String[]{"messages", "sms", "habarlar"});
        ALIAS.put("kalkulyator", new String[]{"calculator", "kalkulyator"});
        ALIAS.put("soat", new String[]{"clock", "soat"});
        ALIAS.put("kalendar", new String[]{"calendar", "kalendar"});
        ALIAS.put("brauzer", new String[]{"chrome", "browser", "brauzer"});
        ALIAS.put("pochta", new String[]{"gmail", "mail", "pochta"});
        ALIAS.put("fayllar", new String[]{"files", "fayllar"});
        ALIAS.put("yutub", new String[]{"youtube"});
        ALIAS.put("youtube", new String[]{"youtube"});
        ALIAS.put("telegram", new String[]{"telegram"});
        ALIAS.put("instagram", new String[]{"instagram"});
        ALIAS.put("vatsap", new String[]{"whatsapp"});
        ALIAS.put("uotsap", new String[]{"whatsapp"});
        ALIAS.put("tiktok", new String[]{"tiktok"});
        ALIAS.put("play market", new String[]{"play store", "play market"});
    }

    /** Ochilgan ilova nomini qaytaradi yoki null. */
    String openApp(String q) {
        q = Text.norm(q);
        if (q.isEmpty()) return null;
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<String> wants = new ArrayList<>();
        wants.add(q);
        for (Map.Entry<String, String[]> e : ALIAS.entrySet()) {
            if (q.startsWith(e.getKey())) {
                for (String a : e.getValue()) wants.add(a);
            }
        }
        double best = 0;
        ResolveInfo bestRi = null;
        String bestLabel = null;
        for (ResolveInfo ri : list) {
            CharSequence cs = ri.loadLabel(pm);
            if (cs == null) continue;
            String nl = Text.norm(cs.toString());
            if (nl.isEmpty()) continue;
            for (String w : wants) {
                double sc;
                if (nl.equals(w)) sc = 1.0;
                else if (w.length() >= 3 && (nl.contains(w) || (nl.length() >= 3 && w.contains(nl)))) sc = 0.9;
                else sc = Text.sim(nl, w);
                if (sc > best) {
                    best = sc;
                    bestRi = ri;
                    bestLabel = cs.toString();
                }
            }
        }
        if (bestRi == null || best < 0.62) return null;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(bestRi.activityInfo.packageName, bestRi.activityInfo.name));
        if (!start(i)) return null;
        return bestLabel;
    }
}
