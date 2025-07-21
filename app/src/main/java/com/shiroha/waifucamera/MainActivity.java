package com.shiroha.waifucamera;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.shiroha.waifucamera.databinding.ActivityMainBinding;
import com.shiroha.waifucamera.ui.PhotographFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements LocationListener, PhotographFragment.OnListener {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_photograph, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // 延伸显示区域到刘海
        Window window = getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(lp);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                startActivity(intent);
            }
        }

        context = this;
        dataPath = Objects.requireNonNull(getExternalFilesDir("")).getAbsolutePath();
        settingFileName = dataPath + "/setting.json";
        favorFileName = dataPath + "/favor.json";
        loadSetting();
        initFavor();

        // 修改权限请求部分
        if (!requestPermission()){
            ActivityCompat.requestPermissions(this, permissions, 1001);
        } else {
            // 权限已授予时初始化位置服务
            initLocationService(); // <-- 新增方法
        }
    }

    Context context;
    String dataPath;
    String settingFileName;
    String favorFileName;

    // 新增初始化位置服务方法
    private void initLocationService() {
        try {
            /*if (settingObj.getBoolean("autoLight")) {
                sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }*/
            if (settingObj.getBoolean("gps") &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (!locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                    // Fallback to GPS or handle the error
                    Log.e("Location", "Network provider unavailable");
                    return;
                }

                //locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                // 正确使用监听器
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        this // 因为实现了LocationListener
                );
            }
        } catch (JSONException e) {
            Log.e("MainActivity", "初始化服务失败", e);
        }
    }

    // 在权限回调中处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initLocationService(); // 权限授予后初始化
            }
        }
    }

    public void initFavor(){
        File file = new File(favorFileName);
        if (!file.exists()){
            try {
                FileWriter writer = new FileWriter(favorFileName);
                writer.write("[]");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void loadSetting(){
        settingObj = new JSONObject();
        try {
            // 添加详细的路径诊断
            Log.d("FILE_DEBUG", "原始路径: " + settingFileName);

            // 转换为绝对路径
            File file = new File(settingFileName);
            String absolutePath = file.getAbsolutePath();
            Log.d("FILE_DEBUG", "绝对路径: " + absolutePath);

            // 检查存储权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("FILE_DEBUG", "缺少存储权限");
            }

            // 使用更可靠的存在检查方法
            boolean exists = false;
            try {
                exists = file.exists();
            } catch (SecurityException e) {
                Log.e("FILE_DEBUG", "安全异常: " + e.getMessage());
            }
            Log.d("FILE_DEBUG", "存在状态: " + exists);

            if (!exists) {
                Log.d("FILE_DEBUG", "进入资源加载流程");

                InputStream inputStream = context.getResources().openRawResource(R.raw.template_setting);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }

                //Toast.makeText(context,stringBuilder.toString(),Toast.LENGTH_LONG).show();
                JSONArray template = new JSONArray(stringBuilder.toString());

                for (int i = 0;i < template.length();i++){
                    JSONObject item = template.getJSONObject(i);
                    if (item == null || !item.has("valueType") || !item.has("name") || !item.has("defaultValue")) {
                        continue;
                    }
                    switch (item.getString("valueType")){
                        case "boolean":
                            settingObj.put(item.getString("name"),item.getBoolean("defaultValue"));
                            break;
                        case "double":
                            settingObj.put(item.getString("name"),item.getDouble("defaultValue"));
                            break;
                        case "string":
                            settingObj.put(item.getString("name"),item.getString("defaultValue"));
                            break;
                    }
                }
                saveSetting();
            }
            else {
                settingObj = new JSONObject(readFile("{}",settingFileName));
                //overflowExtent = settingObj.getDouble("overflowExtent");
            }
        /*} catch (Exception e) {
            Log.e("MainActivity", "加载设置失败", e);
            // 创建空设置对象防止崩溃
            settingObj = new JSONObject();
            try {
                settingObj.put("autoLight", false);
                settingObj.put("gps", false);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }*/
        } catch (Throwable t) { // 捕获包括Error在内的所有异常
            Log.e("MainActivity", "加载设置失败", t);
            // ...错误处理
            Toast.makeText(context, "加载设置失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public String readFile(String empty,String fileName){
        try {
            File file = new File(fileName);
            String jsonString = empty;
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                StringBuilder text = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append("\n");
                }
                jsonString = text.toString();
            }

            return jsonString;

        } catch (IOException e) {
            // Handle file read errors
            e.printStackTrace();
        }
        return null;
    }

    public void saveSetting(){
        try {
            FileWriter writer = new FileWriter(settingFileName);
            writer.write(settingObj.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private final String[] permissions = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
    };

    private boolean requestPermission() {
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        return allPermissionsGranted;
    }

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private LocationManager locationManager;
    private Double longitude,latitude;

    JSONObject settingObj;

    public boolean getLocationServiceAvailablity(){
        if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            return true;
        }
        else {
            return false;
        }
    }
    public double[] getLocation() {

        double[] location = {latitude,longitude};
        return location;
    }

    // 当位置改变时执行，除了移动设置距离为 0时
    //@Override
    public void onLocationChanged(@NonNull Location location) {
        // 获取当前纬度
        latitude = location.getLatitude();
        // 获取当前经度
        longitude = location.getLongitude();
        //Toast.makeText(context, Double.toString(latitude), Toast.LENGTH_SHORT).show();

        // 移除位置管理器
        // 需要一直获取位置信息可以去掉这个
        locationManager.removeUpdates(this);
    }

    // 当前定位提供者状态
    //@Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.e("onStatusChanged", provider);
    }

    // 任意定位提高者启动执行
    //@Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.e("onProviderEnabled", provider);
    }

    // 任意定位提高者关闭执行
    //@Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.e("onProviderDisabled", provider);
    }

    @Override
    public void onBackPressed() {
        // 这里处理逻辑代码，大家注意：该方法仅适用于2.0或更新版的sdk
        super.onBackPressed();
        findViewById(R.id.nav_view).setVisibility(View.VISIBLE);
        return;
    }

}