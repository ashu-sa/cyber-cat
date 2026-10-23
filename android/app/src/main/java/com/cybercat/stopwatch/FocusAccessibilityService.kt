package com.cybercat.stopwatch

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import android.os.SystemClock

/** App names only; screen contents and typed text are never requested or read. */
class FocusAccessibilityService:AccessibilityService() {
    private var lastNotice=0L
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return
        Repository.init(this);Repository.advancePomodoro()
        if(!focusBlockingActive(Repository.pomodoroEnabled,Repository.engine.phase,Repository.pomodoro.stage,Repository.prefs.getBoolean("focusBlocking",false)))return
        val name=event.packageName?.toString() ?: return
        if(name==packageName || name in setOf("android","com.android.systemui","com.android.settings","com.android.phone","com.google.android.dialer","com.android.dialer","com.android.permissioncontroller","com.google.android.permissioncontroller"))return
        if(name !in (Repository.prefs.getStringSet("blockedApps",emptySet()) ?: emptySet()))return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now=SystemClock.elapsedRealtime()
        if(now-lastNotice>2000) { lastNotice=now;Toast.makeText(this,"Flower focus · ${focusText(Repository.displayTime())} left. Pause Pomodoro to allow this app.",Toast.LENGTH_LONG).show() }
    }
    override fun onInterrupt() {}
}
