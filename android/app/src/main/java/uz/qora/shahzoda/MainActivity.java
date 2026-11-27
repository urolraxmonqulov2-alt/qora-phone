package uz.qora.shahzoda;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Bus.L {
    private static final int GOLD = 0xFFD4AF37;
    private static final int REQ_MIC = 11;
    private static final int REQ_ALL = 12;

    private TextView log;
    private TextView status;
    private ScrollView sv;
    private EditText input;
    private Button listenBtn;
    private boolean startAfterPerm = false;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07070C);

        ImageView bg = new ImageView(this);
        bg.setImageResource(R.drawable.hero);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0.35f);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(36), dp(16), dp(12));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("QORA SHAHZODA");
        title.setTextColor(GOLD);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.15f);
        col.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFFB8B8C0);
        status.setTextSize(12);
        status.setPadding(0, dp(4), 0, dp(8));
        col.addView(status);

        sv = new ScrollView(this);
        log = new TextView(this);
        log.setTextColor(0xFFEDEDF2);
        log.setTextSize(15);
        log.setLineSpacing(0, 1.2f);
        log.setPadding(dp(4), dp(4), dp(4), dp(4));
        sv.addView(log);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        listenBtn = mkBtn("Tinglashni yoqish");
        Button set = mkBtn("Sozlamalar");
        btns.addView(listenBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(set, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(btns);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(this);
        input.setHint("Buyruq yoki savol yozing…");
        input.setHintTextColor(0xFF777784);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent e) {
                sendTyped();
                return true;
            }
        });
        Button send = mkBtn("Yuborish");
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(send, new LinearLayout.LayoutParams(-2, -2));
        col.addView(row);

        setContentView(root);

        listenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleListen();
            }
        });
        set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTyped();
            }
        });

        Speaker.get(this);
        for (String s : Bus.history()) append(s);
        if (Bus.history().isEmpty()) {
            append("Qora: Salom! Sozlamalardan Groq kalitini kiriting va ruxsatlarni bering, "
                    + "so'ng «Qora shahzoda» deb chaqiring.");
        }
        if (Prefs.key(this).isEmpty()) showSettings();
    }

    private Button mkBtn(String t) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setTextColor(0xFF0A0A0F);
        bt.setBackgroundColor(GOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        bt.setLayoutParams(lp);
        return bt;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bus.add(this);
    }

    @Override
    protected void onStop() {
        Bus.remove(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void on(String line) {
        append(line);
    }

    private void append(String s) {
        log.append(s + "\n\n");
        sv.post(new Runnable() {
            @Override
            public void run() {
                sv.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private boolean granted(String p) {
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void refresh() {
        String ok = "✓", no = "✗";
        status.setText("Mikrofon " + (granted(Manifest.permission.RECORD_AUDIO) ? ok : no)
                + "   Maxsus imkoniyatlar " + (QoraAccessibility.inst != null ? ok : no)
                + "   Ustida ko'rsatish " + (Settings.canDrawOverlays(this) ? ok : no)
                + "   Kalit " + (Prefs.key(this).isEmpty() ? no : ok));
        listenBtn.setText(ListenService.running ? "Tinglashni o'chirish" : "Tinglashni yoqish");
    }

    private void sendTyped() {
        String t = input.getText().toString().trim();
        if (t.isEmpty()) return;
        input.setText("");
        Engine.get(this).submit(t, null);
    }

    private void toggleListen() {
        if (ListenService.running) {
            stopService(new Intent(this, ListenService.class));
            listenBtn.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            }, 400);
            return;
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            startAfterPerm = true;
            requestAllPerms();
            return;
        }
        startListening();
    }

    private void startListening() {
        try {
            startForegroundService(new Intent(this, ListenService.class));
        } catch (Exception e) {
            append("[Xato] tinglashni boshlab bo'lmadi: " + e.getMessage());
        }
        listenBtn.postDelayed(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        }, 600);
    }

    private void requestAllPerms() {
        List<String> need = new ArrayList<>();
        String[] all = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS};
        for (String p : all) if (!granted(p)) need.add(p);
        if (Build.VERSION.SDK_INT >= 33 && !granted("android.permission.POST_NOTIFICATIONS")) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (need.isEmpty()) {
            refresh();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_ALL);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
        if (startAfterPerm && granted(Manifest.permission.RECORD_AUDIO)) {
            startAfterPerm = false;
            startListening();
        }
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView lbl = new TextView(this);
        lbl.setText("Groq API kaliti (faqat shu telefonda saqlanadi):");
        box.addView(lbl);

        final EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(Prefs.key(this));
        box.addView(key, new LinearLayout.LayoutParams(-1, -2));

        box.addView(dlgBtn("1. Mikrofon, qo'ng'iroq, kontakt ruxsatlari", new Runnable() {
            @Override public void run() { requestAllPerms(); }
        }));
        box.addView(dlgBtn("2. Maxsus imkoniyatlarni yoqish (telefonni boshqarish)", new Runnable() {
            @Override public void run() { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        }));
        box.addView(dlgBtn("3. Boshqa ilovalar ustida ko'rsatish", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        box.addView(dlgBtn("4. Tizim sozlamalarini o'zgartirish (yorqinlik)", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }));
        box.addView(dlgBtn("5. Batareya cheklovini olib tashlash", new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            }
        }));

        ScrollView s = new ScrollView(this);
        s.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("Sozlamalar")
                .setView(s)
                .setPositiveButton("Saqlash", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setKey(MainActivity.this, key.getText().toString());
                        refresh();
                    }
                })
                .setNegativeButton("Yopish", null)
                .show();
    }

    private Button dlgBtn(String t, final Runnable r) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    r.run();
                } catch (Exception e) {
                    append("[Xato] " + e.getMessage());
                }
            }
        });
        return bt;
    }
}
