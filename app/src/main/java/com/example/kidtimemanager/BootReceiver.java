package com.example.kidtimemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 检查是否之前是监控状态，如果是，开机后自动启动
            SharedPreferences prefs = context.getSharedPreferences("KidTimePrefs", Context.MODE_PRIVATE);
            boolean isMonitoring = prefs.getBoolean("is_monitoring", false);
            if (isMonitoring) {
                Intent serviceIntent = new Intent(context, MonitorService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
