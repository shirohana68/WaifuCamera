package com.shiroha.waifucamera.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.shiroha.waifucamera.CustomAdapter;
import com.shiroha.waifucamera.FgLibAdapter;
import com.shiroha.waifucamera.MainActivity;
import com.shiroha.waifucamera.R;
import com.shiroha.waifucamera.databinding.FragmentTableBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TableFragment extends Fragment {
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;

    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private View mControlsView;
    private boolean mVisible;
    private boolean isToggled = false;
    private FragmentTableBinding binding;

    // 修复1: 添加Fragment是否可见的标记
    private boolean isFragmentVisible = false;

    // 修复2: 修改Runnable，添加视图检查
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            if (!isFragmentVisible || getActivity() == null) return;

            int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

            Activity activity = getActivity();
            if (activity != null && activity.getWindow() != null) {
                activity.getWindow().getDecorView().setSystemUiVisibility(flags);
            }

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }

            // 添加空检查
            if (mControlsView != null) {
                mControlsView.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            if (!isFragmentVisible || getActivity() == null) return;

            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }

            // 添加空检查
            if (mControlsView != null) {
                mControlsView.setVisibility(View.VISIBLE);
            }
        }
    };

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final View.OnTouchListener mDelayHideTouchListener = (view, motionEvent) -> {
        if (AUTO_HIDE) {
            delayedHide(AUTO_HIDE_DELAY_MILLIS);
        }
        return false;
    };

    private int facing = 1;
    private double overflowExtent = 0.2;
    int localAspectRatioMode = 4;

    TableFragment context = this;

    private PreviewView previewView;
    private Preview preview;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ProcessCameraProvider cameraProvider;
    FrameLayout previewContainer;

    SeekBar seekBar;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    private File outputDirectory;


    Bitmap currentBmp;
    Bitmap currentBmpOrg;

    int screenWidth;
    int screenHeight;

    float[] marginPoint = {0,0};
    float[] startPoint = {0,0};
    double scale;
    float darkExtentGlobal = 1;

    double prevDistance;

    private String img_path;
    JSONArray fgLibObj;
    JSONObject envObj;
    JSONObject settingObj;
    JSONArray favorObj;
    String exPath = Environment.getExternalStorageDirectory().getPath();
    String dataPath;
    String fgLibFileName;
    String envFileName;
    String favorFileName;
    String settingFileName;

    String currentFgLibPath;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        /*TableViewModel TableViewModel =
                new ViewModelProvider(this).get(TableViewModel.class);*/

        binding = FragmentTableBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = binding.previewView;
        previewContainer = binding.previewContainer;

        dataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();
        fgLibFileName = dataPath + "/fglib.json";
        envFileName = dataPath + "/env.json";
        settingFileName = dataPath + "/setting.json";
        favorFileName = dataPath + "/favor.json";

        WindowManager windowManager = (WindowManager) ((MainActivity) getActivity()).getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        defaultDisplay.getRealSize(outPoint);
        screenWidth = outPoint.x;
        screenHeight = outPoint.y;

        seekBar = binding.seekBar;
        LinearLayout.LayoutParams seekBarLayout = (LinearLayout.LayoutParams) seekBar.getLayoutParams();

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            seekBarLayout.rightMargin = 23 - seekBarLayout.width / 2;
            seekBarLayout.bottomMargin = seekBarLayout.width / 2 - 40;
        }

        loadSetting();

        initializeCamera();

        getFgLib();

        cameraExecutor = Executors.newSingleThreadExecutor();

        bindViews();

        loadFavor();
        loadEnv();

        loadWeb();

        binding.switchCameraButton.setOnClickListener(v -> onSwitchCameraButtonClick(v));
        binding.toggleButton.setOnClickListener(v -> onToggleClick(v));
        binding.previewView.setOnLongClickListener(v -> onToggleClick(v));

        final boolean[] isScaling = {false}; // 是否正在缩放
        final long[] touchStartTime = new long[1];

        binding.previewView.setOnTouchListener(new View.OnTouchListener() {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (currentBmp == null){
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), R.string.addLibAndSelectRequst, Toast.LENGTH_SHORT).show());
                    return false;
                }
                ImageView imageView = binding.imageView;
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) imageView.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartTime[0] = System.currentTimeMillis();
                        startPoint[0] = event.getX();
                        startPoint[1] = event.getY();

                        marginPoint[0] = layout.leftMargin;
                        marginPoint[1] = layout.topMargin;

                        break;
                    case MotionEvent.ACTION_MOVE:
                        /*seekBar = findViewById(R.id.seekBar);
                        seekBar.bringToFront();*/
                        switch (event.getPointerCount()){
                            case 1:
                                if (isScaling[0]){
                                    return false;
                                }

                                float x = event.getX();
                                float y = event.getY();

                                /*if (Math.abs(x - startPoint[0]) > 20 && Math.abs(y - startPoint[1]) > 20){
                                    return false;
                                }*/

                                int left = (int) (marginPoint[0] + (x - startPoint[0]) * 1);
                                int top = (int) (marginPoint[1] + (y - startPoint[1]) * 1);

                                layout.setMargins(left, top, 0, -imageView.getHeight() * 2);
                                imageView.setLayoutParams(layout);

                                break;
                            case 2:
                                isScaling[0] = true;
                                float x1 = event.getX(0);
                                float y1 = event.getY(0);
                                float x2 = event.getX(1);
                                float y2 = event.getY(1);

                                float dx = x2 - x1;
                                float dy = y2 - y1;
                                double distance = Math.sqrt(dx * dx + dy * dy);

                                if (prevDistance != 0) {
                                    double scaleFactor = distance / prevDistance;
                                    int width = imageView.getWidth();
                                    int height = imageView.getHeight();
                                    int newWidth = (int) (width * scaleFactor);
                                    int newHeight = (int) (height * scaleFactor);
                                    layout.width = newWidth;
                                    layout.height = newHeight;
                                    //Log.d("MyActivity",Double.toString(newHeight/newWidth));
                                    imageView.setLayoutParams(layout);
                                    //scale = scaleFactor;
                                }

                                prevDistance = distance;
                                break;
                        }
                        int cutBackX = layout.leftMargin + (int)(imageView.getWidth() * overflowExtent) - previewView.getLayoutParams().width;
                        int cutBackY = layout.topMargin + (int)(imageView.getHeight() * overflowExtent) - previewView.getLayoutParams().height;
                        int cutFowardX = layout.leftMargin + (int)(imageView.getWidth() * (1 - overflowExtent));
                        int cutFowardY = layout.topMargin + (int)(imageView.getHeight() * (1 - overflowExtent));
                        if (cutBackX > 0){
                            layout.leftMargin -= cutBackX;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutBackY > 0){
                            layout.topMargin -= cutBackY;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutFowardX < 0){
                            layout.leftMargin -= cutFowardX;
                            imageView.setLayoutParams(layout);
                        }
                        if (cutFowardY < 0){
                            layout.topMargin -= cutFowardY;
                            imageView.setLayoutParams(layout);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isScaling[0] = false;
                        marginPoint[0] = layout.leftMargin;
                        marginPoint[1] = layout.topMargin;
                        prevDistance = 0;

                        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        imageView.requestLayout();

                        scale = (double) imageView.getWidth() / (double) currentBmp.getWidth();

                        saveEnv();

                        long pressDuration = System.currentTimeMillis() - touchStartTime[0];

                        // 检查移动距离（防止滑动误触发）
                        float moveX = Math.abs(event.getX() - startPoint[0]);
                        float moveY = Math.abs(event.getY() - startPoint[1]);
                        int touchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();

                        // 满足长按条件：时间超过阈值且移动距离在允许范围内
                        if (pressDuration >= 600 &&
                                moveX < touchSlop && moveY < touchSlop) {
                            // 执行长按操作
                            onToggleClick(v);
                        }
                        break;
                }
                return true;
            }
        });
    }

    // 继承自Object类
    public class AndroidtoJs extends Object {

        // 定义JS需要调用的方法
        // 被JS调用的方法必须加入@JavascriptInterface注解
        @JavascriptInterface
        public void jstoggle() {

            /*requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "click!", Toast.LENGTH_SHORT).show());*/
            onToggleClick(null);

            /*binding.webview.clearFocus();
            binding.webview.post(() -> {
                binding.webview.requestFocus(View.FOCUS_DOWN); // 将焦点转移到其他视图
            });*/
        }
    }

    public void loadWeb(){
        try {
            if (!settingObj.getBoolean("showClock")){
                return;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        WebView webView = binding.webview;
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setBackgroundColor(0); // 设置背景色
        webView.getBackground().setAlpha(0); // 设置填充透明度 范围：0-255
        webView.addJavascriptInterface(new AndroidtoJs(), "android");
        webView.loadUrl("file:///android_asset/time/index.html");
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
    public void getFgLib(){
        /*File file = new File(fgLibFileName);
        if (!file.exists()) {
            Intent intent = new Intent();
            intent.setClass((requireContext()), com.shiroha.waifucamera.Settings.class);
            startActivity(intent);
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), R.string.addLibRequst, Toast.LENGTH_SHORT).show());
            return;
        }*/
        try {

            fgLibObj = new JSONArray(readFile("[]",fgLibFileName));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        RecyclerView recyclerView = binding.fgLibRecycler;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        FgLibAdapter adapter = getFgLibAdapter();
        recyclerView.setAdapter(adapter);
    }

    @NonNull
    private FgLibAdapter getFgLibAdapter() {
        FgLibAdapter.OnItemClickListener itemClickListener = position -> {
            // 处理点击事件
            try {
                if (position == 0){
                    currentFgLibPath = "favor";
                    display("favor");
                }
                else {
                    JSONObject item = fgLibObj.getJSONObject(position - 1);
                    //Toast.makeText(context, item.getString("name"), Toast.LENGTH_SHORT).show();
                    currentFgLibPath = item.getString("path");
                    display(item.getString("path"));
                }
                saveEnv();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };

        return new FgLibAdapter(fgLibObj,itemClickListener);
    }

    public void loadEnv(){
        File file = new File(envFileName);
        if (!file.exists()) {
            return;
        }
        try {
            String jsonString = readFile("{}",envFileName);
            if (Objects.equals(jsonString, "{}")){
                return;
            }
            envObj = new JSONObject(jsonString);

            facing = envObj.getInt("facing");

            currentFgLibPath = envObj.getString("currentFgLibPath");
            file = new File(envFileName);
            if (file.exists()) {
                display(currentFgLibPath);
            }

            img_path = envObj.getString("currentFgPath");
            file = new File(img_path);
            if (file.exists()) {
                clickFg(img_path);
            }

            JSONArray marginPointjsonArray = new JSONArray(envObj.getString("marginPoint"));
            float[] marginPointfloatArray = new float[marginPointjsonArray.length()];
            for (int i = 0; i < marginPointjsonArray.length(); i++) {
                marginPointfloatArray[i] = (float) marginPointjsonArray.getDouble(i);
            }
            marginPoint = marginPointfloatArray;
            ImageView imageView = binding.imageView;
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) imageView.getLayoutParams();
            layout.leftMargin = (int)marginPoint[0];
            layout.topMargin = (int)marginPoint[1];

            if (!envObj.getString("scale").equals("0.0")){
                scale = Double.parseDouble(envObj.getString("scale"));
                layout.width  = (int) (currentBmp.getWidth() * scale);
                layout.height = (int) (currentBmp.getHeight() * scale);
                imageView.setLayoutParams(layout);
            }
        } catch (JSONException e) {
            //return;
            throw new RuntimeException(e);
        }

    }
    public void saveEnv(){
        if (currentFgLibPath == null || img_path == null){
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject();

            JSONArray marginPointjsonArray = new JSONArray();
            for (float value : marginPoint) {
                marginPointjsonArray.put(value);
            }
            jsonObject.put("marginPoint",marginPointjsonArray.toString());

            jsonObject.put("scale", Double.toString(scale));
            jsonObject.put("currentFgLibPath", currentFgLibPath);
            jsonObject.put("currentFgPath", img_path);
            jsonObject.put("facing",facing);

            String jsonString = jsonObject.toString();

            FileWriter writer = new FileWriter(envFileName);
            writer.write(jsonString);
            writer.close();
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void loadSetting(){
        settingObj = new JSONObject();
        try {
            File file = new File(settingFileName);

            if (file.exists()){
                settingObj = new JSONObject(readFile("{}",settingFileName));
                overflowExtent = settingObj.getDouble("overflowExtent");
            }
        } catch (JSONException | NullPointerException e){
            String errorMessage = "E " + e.getMessage();
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show());
        }
    }

    public static List<String> listFiles(String directoryPath) {
        List<String> fileList = new ArrayList<>();
        File directory = new File(directoryPath);

        // 检查目录是否存在
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            Arrays.sort(files);
            if (files != null) {
                for (File file : files) {
                    // 如果是文件，将文件路径添加到列表中
                    if (file.isFile()) {
                        String name = file.getName();
                        String[] arr = name.split("\\.");
                        String[] allowed = {"png","jpg","bmp","webp"};
                        if (Arrays.asList(allowed).contains(arr[arr.length - 1])){
                            fileList.add(file.getAbsolutePath());
                        }
                    }
                }
            } else {
                System.err.println("Failed to list files. Directory may be empty or inaccessible.");
            }
        } else {
            System.err.println("Directory does not exist or is not a directory.");
        }

        return fileList;
    }

    public void adjustPreviewSize(){
        //Toast.makeText(context, Integer.toString(screenWidth), Toast.LENGTH_SHORT).show();
        /*previewView.getLayoutParams().height = screenWidth * 4 / 3;
        previewView.getLayoutParams().width = screenWidth;*/
        View previewContainer = binding.previewContainer;

        previewContainer.getLayoutParams().width = screenWidth;
        previewContainer.getLayoutParams().height = screenHeight;

        previewView.getLayoutParams().width = screenWidth;
        previewView.getLayoutParams().height = screenHeight;

    }

    public void clickFg(String path){
        if (path.split("@").length == 2){
            img_path = path.split("@")[1];
        }
        else {
            img_path = path;
        }

        saveEnv();
        currentBmpOrg = BitmapFactory.decodeFile(img_path);
        currentBmp = currentBmpOrg;

        ImageView iv= binding.imageView;
        iv.setImageBitmap(currentBmp);

        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) iv.getLayoutParams();
        layout.width = currentBmp.getWidth();
        layout.height = currentBmp.getHeight();
        iv.setLayoutParams(layout);

        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.requestLayout();

        adjustLight(darkExtentGlobal);

        //Toast.makeText(context, Double.toString((float)iv.getHeight() / (float)iv.getWidth()), Toast.LENGTH_SHORT).show();
    }
    public void saveFavor(){
        try {
            FileWriter writer = new FileWriter(favorFileName);
            writer.write(favorObj.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void saveToFavor(String path){
        String[] result = path.split("/");
        /*JSONObject obj = new JSONObject("{'name': '" + result[result.length-1] + "', 'path': '" + path + "'}");*/
        favorObj.put(result[result.length-1] + "@" + path);

        saveFavor();
    }
    public void loadFavor(){
        try {
            favorObj = new JSONArray(readFile("[]",favorFileName));
            FileWriter writer = new FileWriter(favorFileName);
            writer.write(favorObj.toString());
            writer.close();
        } catch (IOException | JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public void deleteFavor(String name){
        try {
            int n;
            for (int i = 0; i < favorObj.length(); i++) {
                if (favorObj.getString(i).equals(name)) {
                    favorObj.remove(i);
                    break;
                }
            }
            saveFavor();
            display(currentFgLibPath);
        } catch (Exception e){
            throw new RuntimeException(e);
        }

    }
    public void longClickFg(String path){

        if (path.split("@").length == 2){
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.delete)
                    .setMessage(R.string.confirmDeleteFavor)
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteFavor(path);
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
        else {
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), path, Toast.LENGTH_SHORT).show());
            saveToFavor(path);
        }
    }
    public List<String> convertJsonArrayToList(JSONArray jsonArray) {
        try {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                // 获取每个元素并转换为字符串
                list.add(jsonArray.getString(i));
            }
            return list;
        } catch (JSONException e){
            throw new RuntimeException(e);
        }

    }
    public void display(String location){
        List<String> fileList;
        int type = 1;
        if (location.equals("favor")){
            fileList = convertJsonArrayToList(favorObj);
            /*for (int i = 0; i < favorObj.length(); i++) {
                fileList.add(favorObj.getJSONObject(i).getString("path"))
            }*/
            type = 0;
        }
        else {
            String path = exPath + location;
            if (location.contains(exPath)){
                path = location;
            }
            fileList = listFiles(path);
        }

        ArrayList<String> arrayList = new ArrayList<>(fileList);

        RecyclerView recyclerView = binding.recyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        List<String> finalFileList = fileList;
        CustomAdapter.OnItemClickListener itemClickListener = position -> {
            // 处理点击事件
            String selectedItem = finalFileList.get(position);
            // 进行相应操作
            clickFg(selectedItem);
        };
        CustomAdapter.OnItemLongClickListener itemLongClickListener = position -> {
            // 处理点击事件
            String selectedItem = finalFileList.get(position);
            // 进行相应操作
            longClickFg(selectedItem);
        };

        CustomAdapter adapter = new CustomAdapter(arrayList, itemClickListener, itemLongClickListener, type);
        recyclerView.setAdapter(adapter);
    }

    private void initializeCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider,facing);
                adjustPreviewSize();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor((requireContext())));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider, int facingLocal) {
        preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(facingLocal)
                .build();
        //Toast.makeText(context, Integer.toString(facingLocal), Toast.LENGTH_SHORT).show();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
    }

    public boolean onToggleClick(View view){
        /*requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), "get!", Toast.LENGTH_SHORT).show());*/
        //toggle();
        if (!isToggled){
            binding.centerLayer.setVisibility(View.INVISIBLE);
            binding.seekBar.setVisibility(View.INVISIBLE);
            getActivity().findViewById(R.id.nav_view).setVisibility(View.INVISIBLE);

            isToggled = true;
        }
        else {
            isToggled = false;

            showUi();
        }
        return isToggled;
    }
    public void showUi(){
        binding.centerLayer.setVisibility(View.VISIBLE);
        binding.seekBar.setVisibility(View.VISIBLE);
        getActivity().findViewById(R.id.nav_view).setVisibility(View.VISIBLE);
    }

    public void onSwitchCameraButtonClick(View view) {
        if (facing == 1){
            facing = 0;
        }
        else if(facing == 0){
            facing = 1;
        }
        cameraProviderFuture.addListener(() -> {
            cameraProvider.unbind(preview, imageCapture);
            initializeCamera();
        }, ContextCompat.getMainExecutor((requireContext())));
        saveEnv();
    }

    private void bindViews() {
        seekBar = binding.seekBar;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustLight((float) progress / 100);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

                //Toast.makeText(context, "触碰SeekBar", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(context, "放开SeekBar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void adjustLight(float darkExtent){
        if (currentBmpOrg == null){
            return;
        }
        ImageView imageView = binding.imageView;
        Bitmap newBitmap = Bitmap.createBitmap(currentBmpOrg.getWidth(),currentBmpOrg.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();

        ColorMatrix light = new ColorMatrix(new float[]{
                darkExtent, 0, 0, 0, 0,
                0, darkExtent, 0, 0, 0,
                0, 0, darkExtent, 0, 0,
                0, 0, 0, 1, 0,
        });

        paint.setColorFilter(new ColorMatrixColorFilter(light));
        canvas.drawBitmap(currentBmpOrg, 0, 0, paint);

        currentBmp = newBitmap;
        imageView.setImageBitmap(currentBmp);
        darkExtentGlobal = darkExtent;
    }



    @Override
    public void onResume() {
        super.onResume();

        isFragmentVisible = true; // 标记Fragment可见

        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        delayedHide(100);
    }

    @Override
    public void onPause() {
        super.onPause();
        isFragmentVisible = false; // 标记Fragment不可见

        // 修复3: 在暂停时移除所有Handler回调
        mHideHandler.removeCallbacksAndMessages(null);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        }
        show();
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getActivity().getWindow().getDecorView().setSystemUiVisibility(0);
        }

    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentVisible = false; // 确保标记为不可见

        // 修复4: 移除所有Handler回调
        mHideHandler.removeCallbacksAndMessages(null);

        // 清除视图引用
        mContentView = null;
        mControlsView = null;
        binding = null;
    }

    private void toggle() {
        if (!isFragmentVisible) return;
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        if (!isFragmentVisible) return;

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        if (mControlsView != null) {
            mControlsView.setVisibility(View.GONE);
        }
        mVisible = false;

        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        if (!isFragmentVisible) return;

        if (mContentView != null) {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);


    }

    private void delayedHide(int delayMillis) {
        if (!isFragmentVisible) return;
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Nullable
    private ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }
}