package com.shiroha.waifucamera.ui.notifications;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shiroha.waifucamera.MainActivity;
import com.shiroha.waifucamera.R;
import com.shiroha.waifucamera.databinding.FragmentNotificationsBinding;
import com.shiroha.waifucamera.ui.SettingAdapter;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NotificationsFragment extends Fragment {

    NotificationsFragment context;

    JSONObject settingObj;
    JSONArray template;
    //String exPath = Environment.getExternalStorageDirectory().getPath();
    String dataPath;

    String envFileName;
    String settingFileName;

    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        NotificationsViewModel notificationsViewModel =
                new ViewModelProvider(this).get(NotificationsViewModel.class);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        binding.saveButton.setOnClickListener(v -> onSaveButtonClick(v));
        binding.restoreButton.setOnClickListener(v -> onRestoreButtonClick(v));

        dataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();
        envFileName = dataPath + "/env.json";
        settingFileName = dataPath + "/setting.json";

        context = this;

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            settingObj = new JSONObject(readFile("{}",settingFileName));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        loadAndRenderSettingsToPage();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSetting();
    }

    public String readFile(String empty ,String fileName){
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
            e.printStackTrace();
        }
        return null;
    }

    public void loadAndRenderSettingsToPage(){
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.template_setting);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            template = new JSONArray(stringBuilder.toString());

            RecyclerView recyclerView = binding.recyclerViewSetting;
            LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
            recyclerView.setLayoutManager(layoutManager);
            SettingAdapter.OnItemClickListener itemClickListener = position -> {};

            SettingAdapter adapter2 = new SettingAdapter(settingObj,template,itemClickListener);
            recyclerView.setAdapter(adapter2);

        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void saveSetting() {
        SettingAdapter adapter = (SettingAdapter) binding.recyclerViewSetting.getAdapter();
        List<SettingAdapter.SettingItem> items = adapter.getSettingItems();

        try {
            for (SettingAdapter.SettingItem item : items) {
                switch (item.valueType) {
                    case "boolean":
                        settingObj.put(item.name, (Boolean) item.currentValue);
                        break;
                    case "double":
                        settingObj.put(item.name, (Double) item.currentValue);
                        break;
                    case "string":
                        settingObj.put(item.name, (String) item.currentValue);
                        break;
                }
            }

            FileWriter writer = new FileWriter(settingFileName);
            writer.write(settingObj.toString());
            writer.close();
        } catch (JSONException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void onSaveButtonClick(View view){
        saveSetting();
    }

    public void onRestoreButtonClick(View view){
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.restore)
                .setMessage(R.string.confirmRestore)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File file = new File(settingFileName);
                        file.delete();
                        settingObj = new JSONObject();
                        try {
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
                            loadAndRenderSettingsToPage();
                            saveSetting();
                            //settingObj = new JSONObject(readFile("{}",settingFileName));
                        } catch (Throwable t) { // 捕获包括Error在内的所有异常
                            Log.e("MainActivity", "加载设置失败", t);
                        }


                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}