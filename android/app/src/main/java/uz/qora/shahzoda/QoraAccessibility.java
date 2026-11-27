package uz.qora.shahzoda;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Telefonni to'liq boshqarish: global tugmalar, bosish, yozish, aylantirish, ekranni o'qish. */
public class QoraAccessibility extends AccessibilityService {
    static volatile QoraAccessibility inst;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
        Bus.log("[Maxsus imkoniyatlar] ulandi");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        inst = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        inst = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    boolean global(int action) {
        return performGlobalAction(action);
    }

    // ------------------------------------------------------------ daraxt bo'ylab yurish
    private void walk(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 40) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            walk(n.getChild(i), out, depth + 1);
        }
    }

    private List<AccessibilityNodeInfo> allNodes() {
        List<AccessibilityNodeInfo> out = new ArrayList<>();
        walk(getRootInActiveWindow(), out, 0);
        return out;
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        return t == null ? "" : t.toString();
    }

    /** Ekrandagi matn bo'yicha elementni topib bosadi. Bosilgan yorliqni qaytaradi yoki null. */
    String clickByText(String target) {
        String want = Text.norm(target);
        AccessibilityNodeInfo best = null;
        String bestLabel = null;
        int bestLen = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo n : allNodes()) {
            String l = label(n);
            if (l.isEmpty()) continue;
            String nl = Text.norm(l);
            boolean match = nl.equals(want) || nl.contains(want);
            if (!match && want.length() >= 4 && Text.sim(nl, want) >= 0.8) match = true;
            if (!match) continue;
            int score = nl.equals(want) ? 0 : nl.length();
            if (score < bestLen) {
                bestLen = score;
                best = n;
                bestLabel = l;
            }
        }
        if (best == null) return null;
        AccessibilityNodeInfo cur = best;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (cur.isClickable() && cur.isEnabled()) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return bestLabel;
            }
            cur = cur.getParent();
        }
        // so'nggi chora: elementning markaziga bosish
        android.graphics.Rect r = new android.graphics.Rect();
        best.getBoundsInScreen(r);
        if (tap(r.centerX(), r.centerY())) return bestLabel;
        return null;
    }

    boolean tap(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
        return dispatchGesture(b.build(), null, null);
    }

    boolean swipe(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, ms));
        return dispatchGesture(b.build(), null, null);
    }

    /** up=true: tepaga (kontent pastga suriladi). */
    boolean scroll(boolean up) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;
        if (up) return swipe(w / 2, h * 0.3f, w / 2, h * 0.75f, 350);
        return swipe(w / 2, h * 0.75f, w / 2, h * 0.3f, 350);
    }

    boolean typeText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (node == null) {
            for (AccessibilityNodeInfo n : allNodes()) {
                if (n.isEditable()) {
                    node = n;
                    break;
                }
            }
        }
        if (node == null) return false;
        CharSequence old = node.getText();
        boolean hint = Build.VERSION.SDK_INT >= 26 && node.isShowingHintText();
        String base = (old == null || hint) ? "" : old.toString();
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, base + text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    String readScreen() {
        Set<String> seen = new LinkedHashSet<>();
        for (AccessibilityNodeInfo n : allNodes()) {
            String l = label(n).trim();
            if (!l.isEmpty() && l.length() < 200) seen.add(l);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : seen) {
            if (sb.length() + s.length() > 450) break;
            if (sb.length() > 0) sb.append(". ");
            sb.append(s);
        }
        return sb.toString();
    }
}
