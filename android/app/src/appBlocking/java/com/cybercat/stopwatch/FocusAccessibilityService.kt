package com.cybercat.stopwatch

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import android.os.SystemClock

/** App names only; screen contents and typed text are never requested or read. */
class FocusAccessibilityService:AccessibilityService() {
    private var lastNotice=0L
    private var refreshed=0L
    private var launchers=emptySet<String>()
    private var allowedSystem=emptySet<String>()
    private var browsers=emptySet<String>()
    private fun refreshApps() {
        val now=SystemClock.elapsedRealtime()
        if(refreshed!=0L && now-refreshed<30000)return
        refreshed=now
        val pm=packageManager
        launchers=pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER),0).map { it.activityInfo.packageName }.toSet()
        val home=pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),0).map { it.activityInfo.packageName }.toSet()
        val keyboards=(getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).enabledInputMethodList.map { it.packageName }.toSet()
        allowedSystem=home+keyboards
        browsers=pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://example.com")).addCategory(android.content.Intent.CATEGORY_BROWSABLE),android.content.pm.PackageManager.GET_RESOLVED_FILTER).filter { it.filter?.countDataAuthorities()==0 }.map { it.activityInfo.packageName }.toSet()
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return
        Repository.init(this);Repository.advancePomodoro()
        if(!focusBlockingActive(Repository.pomodoroEnabled,Repository.engine.phase,Repository.pomodoro.stage,Repository.prefs.getBoolean("focusBlocking",false)))return
        val name=event.packageName?.toString() ?: return
        if(name==packageName || name in setOf("android","com.android.systemui","com.android.settings","com.android.phone","com.google.android.dialer","com.android.dialer","com.android.permissioncontroller","com.google.android.permissioncontroller"))return
        val prefs=Repository.prefs
        if(name in (prefs.getStringSet("excludedApps",emptySet()) ?: emptySet()))return
        refreshApps()
        if(!focusAppBlocked(name,prefs.getBoolean("focusBlockAll",false),prefs.getStringSet("blockedApps",emptySet()) ?: emptySet(),prefs.getStringSet("excludedApps",emptySet()) ?: emptySet(),browsers,allowedSystem,launchers))return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now=SystemClock.elapsedRealtime()
        if(now-lastNotice>2000) { lastNotice=now;Toast.makeText(this,"Focus is growing. Pause the timer or disable app blocking to allow this app.",Toast.LENGTH_LONG).show() }
    }
    override fun onInterrupt() {}
}
