package com.netprime.app;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/** Blocks requests to known ad and tracking servers (list in assets/adhosts.txt). */
final class AdBlock {

    private static volatile Set<String> hosts;

    private AdBlock() {}

    /** Loads the list in the background so the app opens quickly. */
    static void load(final Context context) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Set<String> set = new HashSet<String>(100000);
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(
                            app.getAssets().open("adhosts.txt"), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.length() > 0) set.add(line);
                    }
                    r.close();
                } catch (Exception ignored) {
                    // Without the list the app still works, just without ad blocking.
                }
                hosts = set;
            }
        }, "adblock-load").start();
    }

    static boolean isAd(String host) {
        Set<String> set = hosts;
        if (set == null || host == null) return false;
        host = host.toLowerCase();
        if (MainActivity.isAllowedSite(host)) return false;
        // Check the host and each parent domain: a.b.ads.com -> b.ads.com -> ads.com
        while (true) {
            if (set.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0) return false;
            host = host.substring(dot + 1);
        }
    }

    static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }
}
