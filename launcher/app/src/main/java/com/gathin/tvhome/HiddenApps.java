package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** User-hidden Home tiles, separate from theme-defined hidden packages and Android app state. */
final class HiddenApps {
    private static final String STORE = "home_hidden_apps";
    private static final String PACKAGES = "packages";

    private HiddenApps() { }

    static Set<String> load(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                .getStringSet(PACKAGES, Collections.emptySet()));
    }

    static boolean setHidden(Context ctx, String pkg, boolean hidden) {
        if (pkg == null || pkg.isEmpty()) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        Set<String> packages = load(ctx);
        if (hidden == packages.contains(pkg)) return true;
        if (hidden) packages.add(pkg);
        else packages.remove(pkg);
        return prefs.edit().putStringSet(PACKAGES, packages).commit();
    }
}
