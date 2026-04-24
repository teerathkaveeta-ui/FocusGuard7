package com.focusguard.pro;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FocusVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    private final Handler handler = new Handler();
    private int minutesLimit = 30;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            minutesLimit = intent.getIntExtra("limit", 30);
        }
        startMonitoringLoop();
        return START_STICKY;
    }

    private void startMonitoringLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getSocialUsageTime() > (minutesLimit * 60 * 1000L)) {
                    activateBlock();
                } else {
                    deactivateBlock();
                }
                handler.postDelayed(this, 10000); // Check every 10 seconds
            }
        }, 1000);
    }

    private long getSocialUsageTime() {
        long total = 0;
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        // Get stats for today (last 24h)
        Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - 86400000, now);
        
        List<String> targetPkgs = Arrays.asList(
            "com.facebook.katana", 
            "com.instagram.android", 
            "com.google.android.youtube"
        );

        for (String pkg : targetPkgs) {
            if (stats.containsKey(pkg)) {
                total += stats.get(pkg).getTotalTimeInForeground();
            }
        }
        return total;
    }

    private void activateBlock() {
        if (vpnInterface != null) return;
        Builder builder = new Builder();
        try {
            // Block data by routing it to a local dead-end
            builder.setSession("FocusGuardBlocker")
                   .addAddress("10.0.0.2", 32)
                   .addDnsServer("127.0.0.1"); // Point DNS to nowhere
            
            // Allow everything EXCEPT these apps (VPN logic)
            // Or explicitly add disallowed apps to the interface
            builder.addAllowedApplication("com.facebook.katana");
            builder.addAllowedApplication("com.instagram.android");
            builder.addAllowedApplication("com.google.android.youtube");

            vpnInterface = builder.establish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deactivateBlock() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        deactivateBlock();
    }
}