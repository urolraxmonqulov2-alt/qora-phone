package uz.qora.shahzoda;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** Groq (OpenAI mos) chat so'rovi. Kalit ilovada saqlanadi, kodga yozilmagan. */
final class Groq {
    private Groq() {}

    static volatile String lastError = "";

    private static final String URL_CHAT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String SYS =
            "Sen Qora Shahzoda ismli ovozli yordamchisan, telefonda ishlaysan. "
                    + "Doim o'zbek tilida (lotin yozuvida), qisqa va aniq javob ber: odatda 1-3 gap. "
                    + "Markdown, ro'yxat va maxsus belgilar ishlatma, javob ovoz chiqarib o'qiladi.";

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    static String chat(String key, List<String[]> hist, String q) {
        lastError = "";
        if (key == null || key.isEmpty()) {
            lastError = "nokey";
            return null;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JSONArray msgs = new JSONArray();
                msgs.put(new JSONObject().put("role", "system").put("content", SYS));
                for (String[] h : hist) {
                    msgs.put(new JSONObject().put("role", h[0]).put("content", h[1]));
                }
                msgs.put(new JSONObject().put("role", "user").put("content", q));
                JSONObject body = new JSONObject()
                        .put("model", MODEL)
                        .put("messages", msgs)
                        .put("max_tokens", 700)
                        .put("reasoning_effort", "low");

                HttpURLConnection con = (HttpURLConnection) new URL(URL_CHAT).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(40000);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Authorization", "Bearer " + key);
                OutputStream os = con.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = con.getResponseCode();
                String s = read(code < 400 ? con.getInputStream() : con.getErrorStream());
                con.disconnect();
                if (code == 429) { lastError = "limit"; return null; }
                if (code == 401 || code == 403) { lastError = "key"; return null; }
                if (code == 404) { lastError = "model"; return null; }
                if (code >= 500) {
                    lastError = "server";
                    if (attempt == 0) { Thread.sleep(1500); continue; }
                    return null;
                }
                if (code >= 400) { lastError = "http" + code; return null; }
                JSONObject j = new JSONObject(s);
                String out = j.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "").trim();
                if (out.isEmpty()) { lastError = "empty"; return null; }
                return out;
            } catch (IOException e) {
                lastError = "network";
                try { Thread.sleep(1500); } catch (InterruptedException ie) { return null; }
            } catch (Exception e) {
                lastError = "parse";
                return null;
            }
        }
        return null;
    }

    static String explain() {
        switch (lastError) {
            case "nokey": return "Groq kaliti kiritilmagan. Sozlamalardan kalitni kiriting.";
            case "key": return "Groq kaliti noto'g'ri yoki eskirgan.";
            case "limit": return "Groq bepul limiti tugadi. Bir ozdan keyin urinib ko'ring.";
            case "network": return "Internet yo'q yoki Groq'ga ulanib bo'lmadi.";
            case "server": return "Groq serveri band. Birozdan keyin urinib ko'ring.";
            case "model": return "Model topilmadi.";
            case "empty": return "Model bo'sh javob qaytardi.";
            default: return "Javob olib bo'lmadi (" + lastError + ").";
        }
    }
}
