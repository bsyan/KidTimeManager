package com.example.kidtimemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class MonitorService extends Service {

    private static final String CHANNEL_ID = "KidTimeMonitorChannel";
    private WindowManager windowManager;
    private View lockView;
    private boolean isLocked = false;
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable monitorRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("KidTimePrefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(1, getNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initLockView();

        // 初始化监控循环
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                checkMonitor();
                handler.postDelayed(this, 1000); // 每秒检查一次
            }
        };
        handler.post(monitorRunnable);
    }

    private void checkMonitor() {
        // 检查是否是新的一天，重置时长
        checkAndResetDailyTime();

        // 获取当前前台应用
        String foregroundPackage = getForegroundApp();
        if (foregroundPackage == null) {
            return;
        }

        // 如果是自己的应用，跳过
        if (foregroundPackage.equals(getPackageName())) {
            removeLockView();
            return;
        }

        // 获取允许的应用
        Set<String> allowedApps = prefs.getStringSet("allowed_apps", null);
        if (allowedApps == null) {
            // 没有允许的应用，锁定
            showLockView();
            return;
        }

        // 检查应用是否在白名单
        if (!allowedApps.contains(foregroundPackage)) {
            showLockView();
            return;
        }

        // 检查剩余时长
        long remaining = prefs.getLong("remaining_time", 0);
        if (remaining <= 0) {
            showLockView();
            return;
        }

        // 检查屏幕是否亮着
        if (isScreenOn()) {
            // 扣减剩余时长
            remaining--;
            prefs.edit().putLong("remaining_time", remaining).apply();
        }

        // 移除锁定
        removeLockView();
    }

    private void checkAndResetDailyTime() {
        long lastReset = prefs.getLong("last_reset_time", 0);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        if (lastReset < today.getTimeInMillis()) {
            // 新的一天，重置
            int dailyDuration = prefs.getInt("daily_duration", 60);
            prefs.edit()
                    .putLong("remaining_time", dailyDuration * 60L)
                    .putLong("last_reset_time", today.getTimeInMillis())
                    .apply();
        }
    }

    private String getForegroundApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, time - 10000, time);
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            UsageStats recentStats = null;
            for (UsageStats stat : stats) {
                if (recentStats == null || stat.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                    recentStats = stat;
                }
            }
            return recentStats == null ? null : recentStats.getPackageName();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            android.view.Display display = windowManager.getDefaultDisplay();
            return display.getState() == android.view.Display.STATE_ON;
        } else {
            @SuppressWarnings("deprecation")
            boolean screenOn = ((android.os.PowerManager) getSystemService(Context.POWER_SERVICE)).isScreenOn();
            return screenOn;
        }
    }

    private void initLockView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        lockView = inflater.inflate(R.layout.lock_view, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        // 拦截按键
        lockView.setFocusableInTouchMode(true);
        lockView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                return true;
            }
            return false;
        });

        // 解锁按钮
        EditText etPassword = lockView.findViewById(R.id.et_password);
        Button btnUnlock = lockView.findViewById(R.id.btn_unlock);
        btnUnlock.setOnClickListener(v -> {
            String password = etPassword.getText().toString();
            String hash = MainActivity.hashPassword(password);
            String savedHash = prefs.getString("password_hash", null);
            if (hash.equals(savedHash)) {
                // 解锁成功，停止监控
                stopSelf();
                prefs.edit().putBoolean("is_monitoring", false).apply();
                Toast.makeText(MonitorService.this, "解锁成功，监控已停止", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MonitorService.this, "密码错误", Toast.LENGTH_SHORT).show();
            }
            etPassword.setText("");
        });
    }

    private void showLockView() {
        if (!isLocked && lockView.getParent() == null) {
            windowManager.addView(lockView, lockView.getLayoutParams());
            isLocked = true;
        }
    }

    private void removeLockView() {
        if (isLocked && lockView.getParent() != null) {
            windowManager.removeView(lockView);
            isLocked = false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "儿童时间监控",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("后台监控儿童设备使用情况");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("儿童时间监控中")
                .setContentText("正在监控儿童的设备使用情况")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable);
        removeLockView();
    }
}
