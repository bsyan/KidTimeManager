package com.example.kidtimemanager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_USAGE = 1001;
    private static final int REQUEST_PERMISSION_OVERLAY = 1002;
    private static final int REQUEST_PERMISSION_DEVICE_ADMIN = 1003;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("KidTimePrefs", MODE_PRIVATE);

        // 首先检查密码，如果没设置，让用户设置，否则验证密码
        String passwordHash = prefs.getString("password_hash", null);
        if (passwordHash == null) {
            // 第一次使用，设置密码
            showSetPasswordDialog();
        } else {
            // 验证密码
            showPasswordDialog();
        }
    }

    private void showSetPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置管理密码");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_password_set, null);
        EditText etPassword = view.findViewById(R.id.et_password);
        EditText etConfirm = view.findViewById(R.id.et_confirm);
        builder.setView(view);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = etPassword.getText().toString();
            String confirm = etConfirm.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                showSetPasswordDialog();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                showSetPasswordDialog();
                return;
            }
            // 保存密码哈希
            String hash = hashPassword(password);
            prefs.edit().putString("password_hash", hash).apply();
            initMainUI();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入管理密码");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_password_input, null);
        EditText etPassword = view.findViewById(R.id.et_password);
        builder.setView(view);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = etPassword.getText().toString();
            String hash = hashPassword(password);
            String savedHash = prefs.getString("password_hash", null);
            if (hash.equals(savedHash)) {
                initMainUI();
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                showPasswordDialog();
            }
        });
        builder.setNegativeButton("退出", (dialog, which) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private void initMainUI() {
        setContentView(R.layout.activity_main);

        // 检查权限
        checkPermissions();

        // 初始化按钮
        Button btnSetDuration = findViewById(R.id.btn_set_duration);
        Button btnSelectApps = findViewById(R.id.btn_select_apps);
        Button btnChangePassword = findViewById(R.id.btn_change_password);
        Button btnStartMonitor = findViewById(R.id.btn_start_monitor);
        Button btnStopMonitor = findViewById(R.id.btn_stop_monitor);
        TextView tvStatus = findViewById(R.id.tv_status);

        // 更新状态
        boolean isMonitoring = prefs.getBoolean("is_monitoring", false);
        tvStatus.setText(isMonitoring ? "当前状态：监控中" : "当前状态：未监控");

        btnSetDuration.setOnClickListener(v -> showSetDurationDialog());
        btnSelectApps.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppSelectActivity.class);
            startActivity(intent);
        });
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnStartMonitor.setOnClickListener(v -> startMonitor());
        btnStopMonitor.setOnClickListener(v -> stopMonitor());
    }

    private void showSetDurationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置每日使用时长（分钟）");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_duration, null);
        EditText etDuration = view.findViewById(R.id.et_duration);
        int current = prefs.getInt("daily_duration", 60);
        etDuration.setText(String.valueOf(current));
        builder.setView(view);
        builder.setPositiveButton("确定", (dialog, which) -> {
            try {
                int duration = Integer.parseInt(etDuration.getText().toString());
                if (duration <= 0) {
                    Toast.makeText(this, "时长必须大于0", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putInt("daily_duration", duration).apply();
                // 重置剩余时长
                prefs.edit().putLong("remaining_time", duration * 60L).apply();
                Toast.makeText(this, "设置成功", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "输入无效", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改管理密码");
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_password_set, null);
        EditText etPassword = view.findViewById(R.id.et_password);
        EditText etConfirm = view.findViewById(R.id.et_confirm);
        builder.setView(view);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String password = etPassword.getText().toString();
            String confirm = etConfirm.getText().toString();
            if (password.isEmpty()) {
                Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }
            String hash = hashPassword(password);
            prefs.edit().putString("password_hash", hash).apply();
            Toast.makeText(this, "密码修改成功", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void checkPermissions() {
        // 检查Usage权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "需要授予使用访问权限才能监控应用", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(intent, REQUEST_PERMISSION_USAGE);
            return;
        }

        // 检查Overlay权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要授予显示在其他应用上层的权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_PERMISSION_OVERLAY);
            return;
        }

        // 检查设备管理员权限
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(this, android.app.admin.DeviceAdminReceiver.class);
        if (!dpm.isAdminActive(cn)) {
            Toast.makeText(this, "需要激活设备管理员权限防止应用被卸载", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "激活后可以防止儿童卸载本应用");
            startActivityForResult(intent, REQUEST_PERMISSION_DEVICE_ADMIN);
        }
    }

    private boolean hasUsageStatsPermission() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAY, time - 1000*1000, time);
        return stats != null && !stats.isEmpty();
    }

    private void startMonitor() {
        // 检查允许的应用是否为空
        java.util.Set<String> allowed = prefs.getStringSet("allowed_apps", null);
        if (allowed == null || allowed.isEmpty()) {
            Toast.makeText(this, "请先选择允许的应用", Toast.LENGTH_SHORT).show();
            return;
        }

        // 启动服务
        Intent serviceIntent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        prefs.edit().putBoolean("is_monitoring", true).apply();
        TextView tvStatus = findViewById(R.id.tv_status);
        tvStatus.setText("当前状态：监控中");
        Toast.makeText(this, "监控已启动", Toast.LENGTH_SHORT).show();
        finish(); // 关闭自己，让儿童无法进入
    }

    private void stopMonitor() {
        // 停止服务
        Intent serviceIntent = new Intent(this, MonitorService.class);
        stopService(serviceIntent);

        prefs.edit().putBoolean("is_monitoring", false).apply();
        TextView tvStatus = findViewById(R.id.tv_status);
        tvStatus.setText("当前状态：未监控");
        Toast.makeText(this, "监控已停止", Toast.LENGTH_SHORT).show();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return password;
        }
    }
}
