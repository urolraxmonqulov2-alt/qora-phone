package uz.qora.shahzoda;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Buyruqni tushunadi va telefonda bajaradi. Tushunmasa null (Engine Groq'ga yuboradi). */
final class Router {
    interface Act {
        String run();
    }

    static final class Out {
        String reply;
        String confirm;
        Act onYes;

        Out(String r) {
            reply = r;
        }

        static Out ask(String q, Act a) {
            Out o = new Out(null);
            o.confirm = q;
            o.onYes = a;
            return o;
        }
    }

    private static final String NOACC = "Maxsus imkoniyatlar yoqilmagan. Sozlamalardan yoqing.";
    private final Phone p;

    Router(Context c) {
        p = new Phone(c);
    }

    private static boolean pre(String[] t, String... ps) {
        for (String w : t) for (String x : ps) if (w.startsWith(x)) return true;
        return false;
    }

    private static boolean eq(String[] t, String... ps) {
        for (String w : t) for (String x : ps) if (w.equals(x)) return true;
        return false;
    }

    private static boolean sub(String n, String... ps) {
        for (String x : ps) if (n.contains(x)) return true;
        return false;
    }

    private static Set<String> set(String... w) {
        return new HashSet<>(Arrays.asList(w));
    }

    private static String strip(String[] t, Set<String> drop) {
        StringBuilder sb = new StringBuilder();
        for (String w : t) {
            if (drop.contains(w)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    private Out nav(int act, String ok) {
        QoraAccessibility a = QoraAccessibility.inst;
        if (a == null) return new Out(NOACC);
        return new Out(a.global(act) ? ok : "Bajara olmadim.");
    }

    Out route(String raw) {
        String n = Text.norm(raw);
        if (n.isEmpty()) return null;
        String[] t = n.split(" ");
        QoraAccessibility a = QoraAccessibility.inst;
        List<Integer> nums = Text.nums(n);

        // ---- chiroq
        if (pre(t, "chiroq", "fonar", "flesh")) {
            if (pre(t, "ochir", "yop")) return new Out(p.torch(false) ? "Chiroq o'chirildi." : "Chiroqni boshqara olmadim.");
            if (pre(t, "yoq") || eq(t, "och", "ochish", "ochgin"))
                return new Out(p.torch(true) ? "Chiroq yondi." : "Chiroqni boshqara olmadim.");
            return new Out("Chiroqni yoqaymi yoki o'chiraymi?");
        }

        // ---- ovoz
        if (pre(t, "ovoz") && !sub(n, "ovozli")) {
            if (pre(t, "ovozsiz", "jim") || eq(t, "mute", "ochir", "ochirib")) return new Out(p.volumeSet(0));
            if (pre(t, "maksimal", "toliq")) return new Out(p.volumeSet(100));
            if (!nums.isEmpty()) return new Out(p.volumeSet(nums.get(0)));
            if (pre(t, "oshir", "kopaytir", "balandla", "baland", "kotar")) return new Out(p.volumeStep(true));
            if (pre(t, "kamaytir", "pasaytir", "past", "tushir")) return new Out(p.volumeStep(false));
            return new Out("Ovozni oshiraymi yoki kamaytiraymi?");
        }

        // ---- yorqinlik
        if (pre(t, "yorqinlik", "yorugl", "yoruglik")) {
            String r;
            if (pre(t, "maksimal", "toliq")) r = p.brightnessSet(100);
            else if (pre(t, "minimal")) r = p.brightnessSet(5);
            else if (!nums.isEmpty()) r = p.brightnessSet(nums.get(0));
            else if (pre(t, "oshir", "kopaytir", "balandla")) r = p.brightnessSet(p.brightnessNow() + 25);
            else if (pre(t, "kamaytir", "pasaytir", "past", "tushir")) r = p.brightnessSet(p.brightnessNow() - 25);
            else r = "Yorqinlikni oshiraymi yoki kamaytiraymi?";
            return new Out(r);
        }

        // ---- batareya, vaqt, ob-havo
        if (pre(t, "batareya", "zaryad", "quvvat")) return new Out(p.battery());

        if (pre(t, "budilnik", "uygotgich", "uygot")) {
            if (nums.isEmpty()) return new Out("Qaysi soatga qo'yay?");
            int h = nums.get(0);
            int m = nums.size() > 1 ? nums.get(1) : 0;
            if (h < 12 && sub(n, "kechqurun", "kechasi", "tushdan keyin")) h += 12;
            if (h > 23 || m > 59) return new Out("Vaqtni tushunmadim.");
            return new Out(p.alarm(h, m) ? String.format(Locale.US, "Budilnik %d:%02d ga qo'yildi.", h, m)
                    : "Budilnikni qo'ya olmadim.");
        }
        if (pre(t, "taymer", "timer")) {
            if (nums.isEmpty()) return new Out("Necha daqiqaga qo'yay?");
            int v = nums.get(0);
            int mult = 60;
            if (sub(n, "soniya", "sekund")) mult = 1;
            else if (sub(n, "soatlik", "soatga")) mult = 3600;
            return new Out(p.timer(v * mult) ? "Taymer qo'yildi." : "Taymerni qo'ya olmadim.");
        }

        if (sub(n, "soat necha", "soat nechi", "hozir vaqt", "vaqt necha", "vaqt nechi")) return new Out(p.time());
        if (sub(n, "bugun") && sub(n, "sana", "nechanchi", "qanaqa kun", "qaysi kun", "hafta")) return new Out(p.date());
        if (pre(t, "havo", "obhavo") || sub(n, "ob havo")) return new Out(p.weather(n));

        // ---- media
        if (eq(t, "pauza") || (pre(t, "musiqa", "qoshiq", "trek") && pre(t, "tohtat", "toxtat", "ochir", "yop")))
            return pauseMedia();
        if (pre(t, "musiqa", "qoshiq", "trek") && (pre(t, "davom", "ijro") || eq(t, "yoq", "qoy")))
            return playMedia();
        if (pre(t, "keyingi", "oldingi", "avvalgi") && pre(t, "musiqa", "qoshiq", "trek")) {
            boolean next = pre(t, "keyingi");
            p.mediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return new Out(next ? "Keyingisi." : "Oldingisi.");
        }

        // ---- qo'ng'iroq
        if (pre(t, "qongiroq", "chaqir", "zvonok") || sub(n, "telefon qil")) {
            Set<String> drop = set("qongiroq", "qongirogi", "qongiroqni", "qil", "qilish", "qilgin", "qiling",
                    "chaqir", "chaqiring", "chaqirgin", "telefon", "telefonga", "zvonok", "iltimos", "menga",
                    "ga", "ni", "bilan", "boglan", "boglaning", "uchun");
            String name = strip(t, drop);
            String digits = name.replace(" ", "");
            String number = null;
            String shown = name;
            if (digits.matches("\\+?\\d{5,}")) {
                number = digits;
            } else if (!name.isEmpty()) {
                number = p.findNumber(name);
            }
            if (name.isEmpty()) return new Out("Kimga qo'ng'iroq qilay?");
            if (number == null) return new Out(name + " nomli kontakt topilmadi.");
            final String num = number;
            return Out.ask(shown + " ga qo'ng'iroq qilaymi?",
                    new Act() {
                        @Override
                        public String run() {
                            return p.call(num) ? "Qo'ng'iroq qilinmoqda." : "Qo'ng'iroq qila olmadim.";
                        }
                    });
        }

        // ---- sms
        if (sub(n, "sms") || (pre(t, "habar") && pre(t, "yubor", "yoz", "jonat"))) {
            Set<String> kw = set("sms", "habar", "habarni", "yubor", "yoz", "jonat", "ga", "ni", "deb", "ayt");
            String name = "";
            for (String w : t) {
                if (!kw.contains(w) && !w.startsWith("yubor") && !w.startsWith("jonat") && !w.startsWith("yoz")) {
                    name = w;
                    break;
                }
            }
            String body = "";
            int di = Arrays.asList(t).indexOf("deb");
            if (di >= 0) body = Text.join(t, di + 1);
            String number = name.isEmpty() ? null : (name.matches("\\d{5,}") ? name : p.findNumber(name));
            boolean ok = p.smsComposer(number, body);
            if (!ok) return new Out("SMS oynasini ocha olmadim.");
            return new Out(number == null && !name.isEmpty()
                    ? name + " topilmadi, SMS oynasini ochdim."
                    : "SMS oynasini ochdim, yuborishni o'zingiz bosing.");
        }

        // ---- ekran boshqaruvi (Maxsus imkoniyatlar)
        if (eq(t, "orqaga", "ortga") || sub(n, "orqaga qayt", "ortga qayt")) return nav(AccessibilityService.GLOBAL_ACTION_BACK, "Orqaga.");
        if (sub(n, "bosh ekran", "asosiy ekran", "uyga qayt", "home ekran") || (t.length <= 3 && pre(t, "yop")))
            return nav(AccessibilityService.GLOBAL_ACTION_HOME, "Bosh ekran.");
        if (sub(n, "oxirgi ilova", "songgi ilova", "ilovalar royhati", "oxirgilar"))
            return nav(AccessibilityService.GLOBAL_ACTION_RECENTS, "Oxirgi ilovalar.");
        if (pre(t, "bildirishnoma", "habarnoma") && !pre(t, "oqi"))
            return nav(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Bildirishnomalar.");
        if (sub(n, "tez sozlama", "boshqaruv markazi"))
            return nav(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Tez sozlamalar.");
        if (pre(t, "skrinshot", "skrenshot") || sub(n, "ekran rasm", "ekranni rasm", "ekranni suratga")) {
            if (Build.VERSION.SDK_INT < 28) return new Out("Bu Android versiyasida skrinshot qo'llab-quvvatlanmaydi.");
            return nav(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Skrinshot olindi.");
        }
        if (pre(t, "qulfla") || (pre(t, "ekran") && pre(t, "ochir"))) {
            if (Build.VERSION.SDK_INT < 28) return new Out("Bu Android versiyasida ekranni qulflab bo'lmaydi.");
            return nav(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Ekran qulflandi.");
        }

        if (pre(t, "pastga", "tepaga", "yuqoriga", "aylantir", "skroll")) {
            if (a == null) return new Out(NOACC);
            boolean up = pre(t, "tepaga", "yuqoriga");
            a.scroll(up);
            return new Out(up ? "Tepaga." : "Pastga.");
        }

        if (sub(n, "ekranda nima", "ekranni oqi", "ekran oqi", "ekranni ayt")) {
            if (a == null) return new Out(NOACC);
            String s = a.readScreen();
            return new Out(s.isEmpty() ? "Ekranda matn topilmadi." : s);
        }

        // "<narsa> ni bos"
        for (int i = 0; i < t.length; i++) {
            if (t[i].equals("bos") || t[i].equals("bosing") || t[i].equals("bosgin") || t[i].equals("tanla")) {
                String target = strip(Arrays.copyOfRange(t, 0, i),
                        set("ni", "ga", "tugmasini", "tugmani", "tugma", "ustiga", "iltimos"));
                if (target.isEmpty()) break;
                if (a == null) return new Out(NOACC);
                String hit = a.clickByText(target);
                return new Out(hit != null ? hit + " bosildi." : "Ekranda «" + target + "» topilmadi.");
            }
        }

        // "yoz ..." -> maydonga matn kiritish
        Matcher m = Pattern.compile("(?iu)\\byoz(?:ing|gin)?\\b\\s*(.+)$").matcher(raw);
        if (m.find()) {
            if (a == null) return new Out(NOACC);
            String txt = m.group(1).trim();
            return new Out(a.typeText(txt) ? "Yozdim." : "Yozish uchun matn maydonini tanlang.");
        }

        // ---- YouTube / musiqa
        if (pre(t, "youtube", "yutub", "yutyub") || (pre(t, "qoshiq") && pre(t, "qoy", "top", "eshit", "qoying"))) {
            String q = strip(t, set("youtube", "youtubeda", "youtubedan", "yutub", "yutubda", "yutyub", "da", "dan",
                    "ochish", "och", "ochib", "qidir", "top", "qoy", "qoying", "ni", "ga", "ilovasini", "ilovasi",
                    "ilovani", "eshit", "qoshiq", "iltimos"));
            if (q.isEmpty() && !pre(t, "qoshiq")) {
                String app = p.openApp("youtube");
                return new Out(app != null ? "YouTube ochildi." : "YouTube topilmadi.");
            }
            if (q.isEmpty()) q = "qo'shiq";
            return new Out(p.youtube(q) ? "YouTube'da qidiryapman." : "YouTube'ni ocha olmadim.");
        }

        // ---- xarita, kamera, wifi, bluetooth
        if (pre(t, "harita", "navigator", "manzil")) {
            String q = strip(t, set("haritada", "harita", "haritani", "navigator", "manzil", "manzilni", "ochish", "och",
                    "ni", "ga", "yol", "top", "korsat", "iltimos"));
            if (q.isEmpty()) {
                String app = p.openApp("harita");
                return new Out(app != null ? "Xarita ochildi." : "Xarita topilmadi.");
            }
            return new Out(p.maps(q) ? q + " xaritada." : "Xaritani ocha olmadim.");
        }
        if (pre(t, "kamera", "selfi") || sub(n, "rasmga ol", "suratga ol")) {
            return new Out(p.camera() ? "Kamera ochildi." : "Kamerani ocha olmadim.");
        }
        if (pre(t, "wifi", "vayfay", "internet") && !pre(t, "qidir")) {
            return new Out(p.panel(pre(t, "wifi", "vayfay") ? "wifi" : "internet") ? "Panelni ochdim." : "Ocha olmadim.");
        }
        if (pre(t, "bluetooth", "blyutuz")) {
            return new Out(p.panel("bluetooth") ? "Bluetooth sozlamalarini ochdim." : "Ocha olmadim.");
        }

        // ---- Google qidiruv
        if (pre(t, "google", "gugl", "qidir", "izla") && !pre(t, "ilova")) {
            String q = strip(t, set("google", "googleda", "gugl", "guglda", "qidir", "qidiring", "izla", "ni", "ga",
                    "internetda", "iltimos", "haqida", "da"));
            if (q.isEmpty()) return new Out("Nimani qidiray?");
            return new Out(p.google(q) ? "Qidiryapman." : "Brauzerni ocha olmadim.");
        }

        // ---- ilova ochish
        boolean openVerb = eq(t, "och", "ochish", "ochib", "ochgin", "ochgil", "ochsang", "ochsangiz", "ochingiz",
                "ochaqol", "kir") || (eq(t, "ishga") && eq(t, "tushir", "tushirgin"));
        if (openVerb) {
            String name = strip(t, set("och", "ochish", "ochib", "ochgin", "ochgil", "ochsang", "ochsangiz", "ochingiz",
                    "ochaqol", "ishga", "tushir", "tushirgin", "ilovasini", "ilovani", "ilova", "ilovasi", "ni", "ga",
                    "iltimos", "menga", "kir", "bir", "ozi", "ayt"));
            if (name.isEmpty()) return null;
            String app = p.openApp(name);
            return new Out(app != null ? app + " ochildi." : "«" + name + "» ilovasi topilmadi.");
        }
        return null;
    }

    private Out pauseMedia() {
        p.mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        return new Out("To'xtatdim.");
    }

    private Out playMedia() {
        p.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        return new Out("Davom ettiraman.");
    }
}
