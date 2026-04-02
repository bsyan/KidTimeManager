package com.example.kidtimemanager;

import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectActivity extends AppCompatActivity {

    private PackageManager packageManager;
    private List<AppInfo> appList;
    private AppAdapter adapter;
    private Set<String> allowedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);

        // 获取已保存的允许的应用
        allowedApps = getSharedPreferences("KidTimePrefs", MODE_PRIVATE)
                .getStringSet("allowed_apps", new HashSet<>());

        packageManager = getPackageManager();
        appList = new ArrayList<>();

        // 获取所有已安装的应用
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            String packageName = app.packageName;
            String name = packageManager.getApplicationLabel(app).toString();
            Drawable icon = packageManager.getApplicationIcon(app);
            boolean isAllowed = allowedApps.contains(packageName);
            appList.add(new AppInfo(name, packageName, icon, isAllowed));
        }

        // 按名称排序
        appList.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        ListView listView = findViewById(R.id.list_apps);
        adapter = new AppAdapter();
        listView.setAdapter(adapter);

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> saveAllowedApps());
    }

    private void saveAllowedApps() {
        Set<String> newAllowed = new HashSet<>();
        for (AppInfo app : appList) {
            if (app.isChecked) {
                newAllowed.add(app.packageName);
            }
        }

        getSharedPreferences("KidTimePrefs", MODE_PRIVATE)
                .edit()
                .putStringSet("allowed_apps", newAllowed)
                .apply();

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        finish();
    }

    private class AppInfo {
        String name;
        String packageName;
        Drawable icon;
        boolean isChecked;

        public AppInfo(String name, String packageName, Drawable icon, boolean isChecked) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
            this.isChecked = isChecked;
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return appList.size();
        }

        @Override
        public Object getItem(int position) {
            return appList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(AppSelectActivity.this)
                        .inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder();
                holder.checkBox = convertView.findViewById(R.id.checkbox);
                holder.ivIcon = convertView.findViewById(R.id.iv_icon);
                holder.tvName = convertView.findViewById(R.id.tv_name);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo app = appList.get(position);
            holder.tvName.setText(app.name);
            holder.ivIcon.setImageDrawable(app.icon);
            holder.checkBox.setChecked(app.isChecked);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                app.isChecked = isChecked;
            });

            return convertView;
        }

        class ViewHolder {
            CheckBox checkBox;
            ImageView ivIcon;
            TextView tvName;
        }
    }
}
