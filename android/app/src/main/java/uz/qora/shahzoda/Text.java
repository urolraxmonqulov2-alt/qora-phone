package uz.qora.shahzoda;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Matn yordamchilari: normallashtirish, o'xshashlik, uyg'otish so'zi, raqamlar. */
final class Text {
    private Text() {}

    private static final String CYR = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяўқғҳ";
    private static final String[] LAT = {"a", "b", "v", "g", "d", "e", "yo", "j", "z", "i", "y", "k", "l", "m",
            "n", "o", "p", "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sh", "", "i", "", "e", "yu", "ya",
            "o", "q", "g", "h"};

    /** Kichik harf, kirill->lotin, apostroflar olib tashlanadi, x->h. */
    static String norm(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            int i = CYR.indexOf(c);
            if (i >= 0 && i < LAT.length) {
                sb.append(LAT[i]);
            } else if (c == '\'' || c == 'ʻ' || c == 'ʼ' || c == '’' || c == '‘' || c == '`') {
                // tashlab ketiladi
            } else if (c == 'x') {
                sb.append('h');
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    static int lev(String a, String b) {
        int[] p = new int[b.length() + 1];
        int[] q = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) p[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            q[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                q[j] = Math.min(Math.min(q[j - 1] + 1, p[j] + 1), p[j - 1] + cost);
            }
            int[] t = p;
            p = q;
            q = t;
        }
        return p[b.length()];
    }

    static double sim(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int m = Math.max(a.length(), b.length());
        return 1.0 - (double) lev(a, b) / m;
    }

    static String join(String[] t, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < t.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(t[i]);
        }
        return sb.toString();
    }

    private static boolean shahLike(String w) {
        return sim(w, "shahzoda") >= 0.62 || sim(w, "shahzada") >= 0.62
                || w.startsWith("shahz") || w.startsWith("shehz") || w.startsWith("shoz");
    }

    /**
     * "Qora shahzoda ..." uyg'otish so'zi topilsa, undan keyingi matnni qaytaradi (bo'sh bo'lishi mumkin).
     * Topilmasa null.
     */
    static String afterWake(String raw) {
        String n = norm(raw);
        if (n.isEmpty()) return null;
        String[] t = n.split(" ");
        for (int i = 0; i < t.length && i < 4; i++) {
            if (sim(t[i], "qorashahzoda") >= 0.7 || t[i].startsWith("qorashah")) {
                return join(t, i + 1);
            }
            boolean qora = sim(t[i], "qora") >= 0.72 || t[i].equals("kora") || t[i].equals("gora");
            if (qora && i + 1 < t.length) {
                if (shahLike(t[i + 1])) return join(t, i + 2);
                if ((t[i + 1].equals("shoh") || t[i + 1].equals("shah")) && i + 2 < t.length
                        && t[i + 2].startsWith("zod")) {
                    return join(t, i + 3);
                }
            }
        }
        return null;
    }

    static boolean isYes(String n) {
        for (String w : n.split(" ")) {
            if (w.equals("ha") || w.equals("hop") || w.equals("mayli") || w.equals("tasdiqlayman")
                    || w.equals("yes") || w.equals("albatta") || w.equals("boladi") || w.equals("ok")
                    || w.equals("tasdiqla") || w.equals("qil")) return true;
        }
        return false;
    }

    static boolean isNo(String n) {
        for (String w : n.split(" ")) {
            if (w.equals("yoq") || w.equals("bekor") || w.equals("no") || w.equals("kerakmas")
                    || w.equals("hechqachon") || w.equals("toqta") || w.equals("tohtat")) return true;
        }
        return n.contains("kerak emas");
    }

    private static int wordNum(String w) {
        switch (w) {
            case "nol": return 0;
            case "bir": return 1;
            case "ikki": return 2;
            case "uch": return 3;
            case "tort": return 4;
            case "besh": return 5;
            case "olti": return 6;
            case "yetti": case "etti": return 7;
            case "sakkiz": return 8;
            case "toqqiz": case "tohqiz": return 9;
            case "on": return 10;
            case "yigirma": return 20;
            case "ottiz": return 30;
            case "qirq": return 40;
            case "ellik": return 50;
            case "oltmish": return 60;
            case "yetmish": return 70;
            case "sakson": return 80;
            case "toqson": return 90;
            case "yuz": return 100;
            default: return -1;
        }
    }

    /** Matndagi sonlar (raqam yoki so'z bilan) ketma-ketligi. */
    static List<Integer> nums(String n) {
        List<Integer> out = new ArrayList<>();
        int acc = -1;
        for (String w : n.split(" ")) {
            if (w.matches("\\d{1,9}")) {
                if (acc >= 0) { out.add(acc); acc = -1; }
                out.add(Integer.parseInt(w));
                continue;
            }
            int v = wordNum(w);
            if (v < 0) {
                if (acc >= 0) { out.add(acc); acc = -1; }
            } else if (acc >= 10 && acc % 10 == 0 && v < 10) {
                out.add(acc + v);
                acc = -1;
            } else {
                if (acc >= 0) out.add(acc);
                acc = v;
            }
        }
        if (acc >= 0) out.add(acc);
        return out;
    }
}
