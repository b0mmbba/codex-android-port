package com.codexandroid;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public final class CodexAccessibilityService extends AccessibilityService {
    private static volatile boolean active;

    public static boolean isActive() {
        return active;
    }

    @Override
    protected void onServiceConnected() {
        active = true;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        active = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        active = true;
    }

    @Override
    public void onInterrupt() {
        active = false;
    }

    public void tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }
}
