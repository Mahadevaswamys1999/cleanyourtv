package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.Map;

/**
 * Sends the remote's Home button to this launcher on remotes that emit KEYCODE_ALL_APPS for Home
 * (Xiaomi sticks and boxes), or a physical KEYCODE_HOME exposed to accessibility (some Google TVs).
 * When the system does not deliver KEYCODE_HOME to an accessibility service, this fallback has no
 * effect. All unrelated keys pass through untouched.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 */
public class HomeKeyService extends AccessibilityService {
    private static final String SONY_VH21 = "BRAVIA_VH21";
    private static final String STOCK_HOME_PACKAGE = "com.google.android.apps.tv.launcherx";
    // This firmware reports the stock HOME's window root as FrameLayout rather than its Activity.
    private static final String STOCK_HOME_WINDOW = "android.widget.FrameLayout";
    private static final String STOCK_HOME_ACTIVITY = "com.google.android.apps.tv.launcherx.home.HomeActivity";
    private static final String JIO_TV_PACKAGE = "com.skylake.skytv.jgorunner";
    // Only after a real TV-button launch: a bounded content-aware click of an actual
    // channel card. Jio v1.2.0 keeps its native one-shot autoplay flag set while
    // its foreground server keeps the process alive across app exits.
    private static final long JIO_ENTRY_TIMEOUT_MS = 16_000;
    private static final long JIO_NATIVE_AUTOPLAY_GRACE_MS = 1_300;
    // Wait until the stock launcher has settled: Cast may launch a new player after
    // briefly revealing Home, and new app window events cancel the pending redirect.
    private static final long STOCK_HOME_RETURN_DELAY_MS = 1_200;
    private static final long STOCK_HOME_RETURN_COOLDOWN_MS = 4_000;
    // Sony can deliver a stale launcherx window event after this app already
    // becomes top-resumed. Do not launch Home again from that old event.
    private static final long OWN_HOME_SUPPRESSION_MS = 2_500;

    private final Handler homeHandler = new Handler(Looper.getMainLooper());
    private boolean stockHomeReturnPending;
    private long lastStockHomeReturnUptime;
    private long lastOwnHomeLaunchUptime;
    private boolean jioEntryArmed;
    private boolean jioClickQueued;
    private long jioEntryStartedUptime;
    private String lastForegroundWindowPackage;
    private final Runnable tryJioEntry = this::attemptJioEntry;
    private final Runnable clickJioChannel = this::clickJioChannel;
    private final Runnable returnFromStockHome = () -> {
        stockHomeReturnPending = false;
        if (!isScreenAvailable()
                || SystemClock.uptimeMillis() - lastOwnHomeLaunchUptime
                        < OWN_HOME_SUPPRESSION_MS
                || SystemClock.uptimeMillis() - lastStockHomeReturnUptime
                        < STOCK_HOME_RETURN_COOLDOWN_MS) {
            Log.d("CleanYourTVHomeKey", "Delayed stock HOME event ignored; no redirect");
            return;
        }
        // This runs only after a stock-HOME window is observed with no newer
        // app window event. Clear the arm before opening our own HOME.
        lastStockHomeReturnUptime = SystemClock.uptimeMillis();
        Log.i("CleanYourTVHomeKey", "Sony stock HOME settled; opening CleanYourTV");
        startCustomHome();
    };

    /** Remote buttons that should open Settings (config.xml, settings_key_scancodes and settings_key_keycodes). */
    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;
    /** app_keys, the same map InputKeyService reads, for setups that run only this service. */
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();

    private boolean isScreenAvailable() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return power != null && power.isInteractive()
                && (keyguard == null || !keyguard.isKeyguardLocked());
    }

    private void cancelPendingHomeReturn() {
        if (!stockHomeReturnPending) return;
        homeHandler.removeCallbacks(returnFromStockHome);
        stockHomeReturnPending = false;
    }

    private void startCustomHome() {
        cancelJioEntry();
        lastOwnHomeLaunchUptime = SystemClock.uptimeMillis();
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(home);
    }

    private void cancelJioEntry() {
        jioEntryArmed = false;
        jioClickQueued = false;
        homeHandler.removeCallbacks(tryJioEntry);
        homeHandler.removeCallbacks(clickJioChannel);
    }

    private void openRemoteApp(String pkg) {
        if (SONY_VH21.equalsIgnoreCase(Build.DEVICE) && JIO_TV_PACKAGE.equals(pkg)) {
            armJioEntry();
        } else {
            cancelJioEntry();
        }
        RemoteKeys.launch(this, pkg);
    }

    private void armJioEntry() {
        cancelJioEntry();
        jioEntryArmed = true;
        jioEntryStartedUptime = SystemClock.uptimeMillis();
        // Window/content change events are preferred. Short, bounded fallback
        // retries cover an already-created Compose channel page on reentry.
        homeHandler.postDelayed(tryJioEntry, 650);
    }

    private static boolean isHomeOrNativeTv(String pkg) {
        return "com.gathin.tvhome".equals(pkg) || STOCK_HOME_PACKAGE.equals(pkg)
                || "com.sony.dtv.tvx".equals(pkg);
    }

    private boolean jioEntryIsCurrent() {
        if (!jioEntryArmed) return false;
        if (!isScreenAvailable()
                || SystemClock.uptimeMillis() - jioEntryStartedUptime >= JIO_ENTRY_TIMEOUT_MS) {
            cancelJioEntry();
            return false;
        }
        return true;
    }

    private static boolean hasJioTitle(AccessibilityNodeInfo root, int remaining) {
        if (root == null || remaining <= 0) return false;
        if ("JTV-GO".contentEquals(root.getText() == null ? "" : root.getText())) return true;
        for (int index = 0; index < root.getChildCount(); index++) {
            if (hasJioTitle(root.getChild(index), remaining - 1)) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findFirstJioChannel(AccessibilityNodeInfo node,
                                                              Rect screen, int depth) {
        if (node == null || depth > 12) return null;
        if (node.isVisibleToUser() && node.isClickable() && node.isLongClickable()
                && JIO_TV_PACKAGE.contentEquals(node.getPackageName() == null
                        ? "" : node.getPackageName())) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.top > screen.height() / 5 && bounds.width() > screen.width() / 20
                    && bounds.height() > screen.height() / 12) {
                CharSequence art = null;
                CharSequence title = null;
                // The upstream ChannelGridMain cards expose their image's
                // contentDescription and channel-name TextView as direct children.
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child == null) continue;
                    if ("android.widget.TextView".contentEquals(child.getClassName() == null
                            ? "" : child.getClassName())) title = child.getText();
                    if (child.getContentDescription() != null) art = child.getContentDescription();
                }
                if (JioTvChannelMatcher.isChannelCard(node.isClickable(), node.isLongClickable(),
                        art, title)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findFirstJioChannel(node.getChild(i), screen, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo visibleJioChannel() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !JIO_TV_PACKAGE.contentEquals(root.getPackageName() == null
                ? "" : root.getPackageName()) || !hasJioTitle(root, 9)) return null;
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        if (screen.isEmpty()) return null;
        return findFirstJioChannel(root, screen, 0);
    }

    private void attemptJioEntry() {
        if (!jioEntryIsCurrent() || jioClickQueued) return;
        if (visibleJioChannel() != null) {
            jioClickQueued = true;
            // Native autoplay may still be preparing a fresh first-process launch.
            homeHandler.postDelayed(clickJioChannel, JIO_NATIVE_AUTOPLAY_GRACE_MS);
        } else {
            homeHandler.postDelayed(tryJioEntry, 800);
        }
    }

    private void clickJioChannel() {
        if (!jioEntryIsCurrent()) return;
        jioClickQueued = false;
        // Recheck the active window and tile at action time. If native autoplay
        // has reached ExoPlayJet, the player has no JTV-GO channel-grid title.
        AccessibilityNodeInfo channel = visibleJioChannel();
        if (channel == null) {
            homeHandler.postDelayed(tryJioEntry, 800);
            return;
        }
        boolean clicked = channel.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.i("CleanYourTVRemote", "Sony TV live-channel card action: " + clicked);
        cancelJioEntry(); // Exactly one attempted channel click per TV button press.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // Keycodes and the panel choice can also come from a theme; theme.sh restarts this service.
        Config config = Config.load(this);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // A scancode entry is more specific than any keycode, so it goes first.
        String scanApp = appScancodes.get(event.getScanCode());
        if (scanApp != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openRemoteApp(scanApp);
            return true;
        }
        // The stock launcher handled the remote's Settings button; with it disabled the button does nothing.
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        String app = appKeys.get(event.getKeyCode());
        // Keep Sony's native Input selector, tuning controls and HDMI key behavior intact.
        // Log physical TV presses even if the vendor firmware remaps TV -> TV_INPUT;
        // we never consume unmapped keys. No telemetry or screen content is recorded.
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_TV
                        || event.getKeyCode() == KeyEvent.KEYCODE_TV_INPUT)) {
            Log.i("CleanYourTVRemote", "Sony TV key=" + event.getKeyCode()
                    + " scan=" + event.getScanCode()
                    + " mapped=" + (app != null));
        }
        if (app != null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) openRemoteApp(app);
            return true;
        }
        int keycode = event.getKeyCode();
        if (keycode != KeyEvent.KEYCODE_ALL_APPS && keycode != KeyEvent.KEYCODE_HOME) return false;
        // A real Home press already has a direct handler; cancel any stock-Home fallback.
        cancelPendingHomeReturn();
        if (keycode == KeyEvent.KEYCODE_HOME && event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i("CleanYourTVHomeKey", "Remote HOME delivered to accessibility service");
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            startCustomHome();
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (jioEntryArmed && SONY_VH21.equalsIgnoreCase(Build.DEVICE)
                && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                && JIO_TV_PACKAGE.contentEquals(event.getPackageName() == null
                        ? "" : event.getPackageName()) && !jioClickQueued) {
            // Coalesce potentially frequent Compose content updates to one retry.
            homeHandler.removeCallbacks(tryJioEntry);
            homeHandler.postDelayed(tryJioEntry, 120);
        }
        // A Home tile/open-app action does not pass through the TV key handler.
        // Treat a *new* Jio foreground window after our Home, stock Home or Sony
        // TV as a deliberate live-TV entry as well. Ignore internal Jio player,
        // dialog and channel-page transitions, and never trigger from Cast/AirPlay.
        if (SONY_VH21.equalsIgnoreCase(Build.DEVICE)
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            String foreground = event.getPackageName().toString();
            if (JIO_TV_PACKAGE.equals(foreground)
                    && !jioEntryArmed && isHomeOrNativeTv(lastForegroundWindowPackage)
                    && isScreenAvailable()) {
                armJioEntry();
                Log.d("CleanYourTVRemote", "Live-TV entry detected from Home/TV");
            }
            if (!"com.android.systemui".equals(foreground)) {
                lastForegroundWindowPackage = foreground;
            }
        }
        // On this Sony model the Cast receiver opens the stock launcher explicitly
        // after casting stops. Real Cast sessions don't always emit a ZEE5 window
        // event, so checking the previous foreground package misses real stops.
        // Instead treat ONLY a confirmed stock HOME window as a fallback to our
        // selected Home, with a delay cancelled if any other app becomes visible.
        // This also handles other system-initiated returns to stock HOME. No Cast
        // app or casting service is touched and no screen content is inspected.
        if (!SONY_VH21.equalsIgnoreCase(Build.DEVICE)
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence eventPackage = event.getPackageName();
        if (eventPackage == null) return;
        String pkg = eventPackage.toString();
        if (STOCK_HOME_PACKAGE.equals(pkg)) {
            CharSequence type = event.getClassName();
            Log.d("CleanYourTVHomeKey", "Sony launcher window class=" + type
                    + " windowId=" + event.getWindowId());
            if (type != null
                    && (STOCK_HOME_WINDOW.contentEquals(type)
                            || STOCK_HOME_ACTIVITY.contentEquals(type))
                    && !stockHomeReturnPending
                    && isScreenAvailable()
                    && SystemClock.uptimeMillis() - lastOwnHomeLaunchUptime
                            >= OWN_HOME_SUPPRESSION_MS
                    && SystemClock.uptimeMillis() - lastStockHomeReturnUptime
                            >= STOCK_HOME_RETURN_COOLDOWN_MS) {
                stockHomeReturnPending = true;
                homeHandler.postDelayed(returnFromStockHome, STOCK_HOME_RETURN_DELAY_MS);
                Log.i("CleanYourTVHomeKey", "Sony stock HOME window detected; delayed return queued");
            }
            return;
        }
        // SystemUI may overlay a brief volume/notification indicator on the
        // actual HOME screen. A new Cast player, AirPlay, HDMI/settings, or any
        // other app window cancels the fallback before it can steal playback.
        if (!"com.android.systemui".equals(pkg)) {
            if (stockHomeReturnPending) {
                Log.d("CleanYourTVHomeKey", "Home return canceled by app window: " + pkg);
            }
            cancelPendingHomeReturn();
        }
    }

    @Override
    public void onInterrupt() {
        cancelPendingHomeReturn();
        cancelJioEntry();
    }

    @Override
    public void onDestroy() {
        cancelPendingHomeReturn();
        cancelJioEntry();
        super.onDestroy();
    }
}
