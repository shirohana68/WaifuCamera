package com.shiroha.waifucamera.ui.photograph;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
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
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.shiroha.waifucamera.CustomAdapter;
import com.shiroha.waifucamera.FgLibAdapter;
import com.shiroha.waifucamera.MainActivity;
import com.shiroha.waifucamera.R;
import com.shiroha.waifucamera.databinding.FragmentPhotographBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotographFragment extends Fragment {
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;

    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private View mControlsView;
    private boolean mVisible;
    private FragmentPhotographBinding binding;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        PhotographViewModel photographViewModel =
                new ViewModelProvider(this).get(PhotographViewModel.class);

        binding = FragmentPhotographBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /* my global */

    private int facing = 1;
    private double overflowExtent = 0.2;
    private final double[] cameraAspectRatioOptions = {1,0.75,0.5625,0};
    int localAspectRatioMode = 1;

    PhotographFragment context = this;

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

    private OnListener mListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnListener) {
            mListener = (OnListener) context;
        }
        else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnListener {
        double[] getLocation();
        boolean getLocationServiceAvailablity();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PhotographViewModel photographViewModel =
                new ViewModelProvider(this).get(PhotographViewModel.class);

        /*final TextView textView = binding.fullscreenContent;
        photographViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        mContentView.setOnClickListener(v -> toggle());
        binding.dummyButton.setOnTouchListener(mDelayHideTouchListener);*/
        previewView = binding.previewView;
        previewContainer = binding.previewContainer;

        dataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();
        fgLibFileName = dataPath + "/fglib.json";
        envFileName = dataPath + "/env.json";
        settingFileName = dataPath + "/setting.json";
        favorFileName = dataPath + "/favor.json";

        /*DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;*/
        WindowManager windowManager = (WindowManager) ((MainActivity) getActivity()).getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        defaultDisplay.getRealSize(outPoint);
        screenWidth = outPoint.x;
        screenHeight = outPoint.y;

        seekBar = binding.seekBar;
        LinearLayout.LayoutParams seekBarLayout = (LinearLayout.LayoutParams) seekBar.getLayoutParams();
        //seekBarLayout.setMargins(40 - seekBarLayout.width / 2,(int)(screenWidth / 0.75 / 2));

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            seekBarLayout.rightMargin = 23 - seekBarLayout.width / 2;
            seekBarLayout.bottomMargin = seekBarLayout.width / 2 - 40;
        }

        loadSetting();

        initializeCamera();

        getFgLib();
        // 设置输出目录
        outputDirectory = getOutputDirectory();
        // 创建一个 ExecutorService 以用于摄像头操作
        cameraExecutor = Executors.newSingleThreadExecutor();

        bindViews();

        loadFavor();
        loadEnv();

        binding.takePhotoButton.setOnClickListener(v -> onTakePhotoButtonClick(v));
        binding.switchCameraButton.setOnClickListener(v -> onSwitchCameraButtonClick(v));
        binding.changeAspectRatioButton.setOnClickListener(v -> onChangeAspectRatioButtonClick(v));

        final boolean[] isScaling = {false}; // 是否正在缩放

        previewContainer.setOnTouchListener(new View.OnTouchListener() {
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

                        break;
                }
                return true;
            }
        });
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
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), context.getString(R.string.fileNotFound), Toast.LENGTH_SHORT).show());
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
            startActivity(intent);equireActivity().runOnUiThread(() ->
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

    /*public void saveSetting(){
        try {
            FileWriter writer = new FileWriter(settingFileName);
            writer.write(settingObj.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }*/

    public void loadSetting(){
        settingObj = new JSONObject();
        try {
            File file = new File(settingFileName);
            /*if (!file.exists()){
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

                overflowExtent = settingObj.getDouble("overflowExtent");

            }*/
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
        double cameraAspectRatio = cameraAspectRatioOptions[localAspectRatioMode];
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            int height = (int)(screenWidth / cameraAspectRatio);
            previewContainer.getLayoutParams().height = height;
            previewContainer.getLayoutParams().width = screenWidth;

            previewView.getLayoutParams().width = screenWidth;
            previewView.getLayoutParams().height = height;
        }
        else {
            previewContainer.getLayoutParams().width = (int)(screenHeight / cameraAspectRatio);
            previewContainer.getLayoutParams().height = screenHeight;

            previewView.getLayoutParams().width = (int)(screenHeight / cameraAspectRatio);
            previewView.getLayoutParams().height = screenHeight;
        }
        if (cameraAspectRatio == 0){
            previewContainer.getLayoutParams().width = screenWidth;
            previewContainer.getLayoutParams().height = screenHeight;

            previewView.getLayoutParams().width = screenWidth;
            previewView.getLayoutParams().height = screenHeight;
        }
    }
    @SuppressLint("SetTextI18n")
    public void onChangeAspectRatioButtonClick(View view) {
        /*View recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setVisibility(View.VISIBLE);*/
        localAspectRatioMode += 1;
        if (localAspectRatioMode >= cameraAspectRatioOptions.length){
            localAspectRatioMode = 0;
        }
        Button button = binding.changeAspectRatioButton;
        String text = "";
        //private final double[] cameraAspectRatioOptions = {1,0.75,0.5625,0};
        switch (Double.toString(cameraAspectRatioOptions[localAspectRatioMode])){
            case "1.0":
                text = "1:1";
                break;
            case "0.75":
                text = "4/3";
                break;
            case "0.5625":
                text = "16/9";
                break;
            case "0.0":
                text = context.getString(R.string.fullScreenButton);
                break;
        }
        button.setText(text);
        adjustPreviewSize();
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
    public int getIndexInFavor(String path){
        for (int i = 0; i < favorObj.length(); i++) {
            try {
                if (favorObj.getString(i).split("@")[1].equals(path)){
                    return i;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
                //return -1;
            }
        }
        return -1;
    }
    /*public void saveToFavor(String path){

    }*/
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

            if (getIndexInFavor(path) != -1){
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), context.getString(R.string.existInFavor), Toast.LENGTH_SHORT).show());
                return;
            }
            String[] result = path.split("/");
            /*JSONObject obj = new JSONObject("{'name': '" + result[result.length-1] + "', 'path': '" + path + "'}");*/
            favorObj.put(result[result.length-1] + "@" + path);

            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), context.getString(R.string.addedToFavor), Toast.LENGTH_SHORT).show());
            saveFavor();
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

    private File getOutputDirectory() {
        File mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File outputDir = new File(mediaDir, context.getString(R.string.app_name));

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        return outputDir;
    }

    public void onTakePhotoButtonClick(View view) {
        if (currentBmp == null){
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), R.string.addLibAndSelectRequst, Toast.LENGTH_SHORT).show());
            return;
        }
        String format;
        try {
            format = settingObj.getString("photoNameFormat");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        Date currentDate = new Date();
        File photoFile = new File(outputDirectory, "temp-"+dateFormat.format(currentDate)+".jpg");
        //Toast.makeText(context, outputDirectory.getPath(), Toast.LENGTH_SHORT).show();

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // 图片保存成功
                String savedUri = photoFile.getAbsolutePath();

                String[] exifKeys = {
                        ExifInterface.TAG_APERTURE,
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_EXPOSURE_TIME,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_IMAGE_LENGTH,
                        ExifInterface.TAG_IMAGE_WIDTH,
                        ExifInterface.TAG_ISO,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_WHITE_BALANCE
                };
                String[] exifData = getExifData(exifKeys,savedUri);

                BitmapFactory.Options options = new BitmapFactory.Options();
                //options.inSampleSize = 8; // 缩小图像以避免OOM
                Bitmap bitmap = BitmapFactory.decodeFile(savedUri, options);
                //Bitmap bitmapToAdd = BitmapFactory.decodeFile(img_path, options);

                //Bitmap bitmapOut = addImageToBitmap(bitmap, bitmapToAdd, marginPoint);
                Bitmap bitmapOut = addImageToBitmap(bitmap, currentBmp, marginPoint);
                saveBitmapToStorage(bitmapOut,exifKeys,exifData);

                try {
                    if (!settingObj.getBoolean("saveOrigin")){
                        File file = new File(savedUri);
                        file.delete();
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // 图片保存失败
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), R.string.savePhotoFailed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private Bitmap toHorizontalMirror(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(-1f, 1f); // 水平镜像翻转
        return Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);
    }
    private Bitmap addImageToBitmap(Bitmap bitmapOrg, Bitmap bitmapToAdd, float[] marginPoint) {
        if (facing == 0){
            bitmapOrg = toHorizontalMirror(bitmapOrg);
        }

        double cameraAspectRatio = cameraAspectRatioOptions[localAspectRatioMode];
        int orientation = getResources().getConfiguration().orientation;
        if (cameraAspectRatio == 0.0){
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                cameraAspectRatio = (double) screenWidth / (double) screenHeight;
            }
            else {
                cameraAspectRatio = (double) screenHeight / (double) screenWidth;
            }
        }
        //int orientation = getResources().getConfiguration().orientation;
        Bitmap bitmap;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (bitmapOrg.getWidth() >= bitmapOrg.getHeight() * cameraAspectRatio) {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        (int) (bitmapOrg.getWidth() / 2 - (bitmapOrg.getHeight() * cameraAspectRatio) / 2),
                        0,
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio),
                        bitmapOrg.getHeight()
                );
            } else {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        0,
                        bitmapOrg.getWidth(),
                        (int) (bitmapOrg.getWidth() * cameraAspectRatio)
                );
            }
        }
        else {
            if (bitmapOrg.getHeight() >= bitmapOrg.getWidth() * cameraAspectRatio) {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        (int) (bitmapOrg.getHeight() / 2 - (bitmapOrg.getWidth() * cameraAspectRatio) / 2),
                        bitmapOrg.getHeight(),
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio)
                );
            } else {
                bitmap = Bitmap.createBitmap(bitmapOrg,
                        0,
                        0,
                        (int) (bitmapOrg.getHeight() * cameraAspectRatio),
                        bitmapOrg.getHeight()
                );
            }
        }

        // 创建一个新的 Bitmap，尺寸和原始图像相同
        Bitmap newBitmap = bitmap.copy(bitmap.getConfig(), true);

        // 创建画布并将其连接到新的 Bitmap 上
        Canvas canvas = new Canvas(newBitmap);

        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, Float.toString(marginPoint[1]), Toast.LENGTH_SHORT).show();
            }
        });*/

        ImageView imageView = binding.imageView;
        View previewContainer = binding.previewContainer;

        float picScale = (float)bitmap.getWidth() / (float)previewContainer.getLayoutParams().width;
        //float potScale = (float)imageView.getWidth() / (float)previewContainer.getLayoutParams().width;

        bitmapToAdd = Bitmap.createScaledBitmap(bitmapToAdd
                , Math.round((float)imageView.getWidth() / (float)previewContainer.getLayoutParams().width * bitmap.getWidth())
                , Math.round((float)imageView.getHeight() / (float)previewContainer.getLayoutParams().height * bitmap.getHeight())
                , true);

        canvas.drawBitmap(bitmapToAdd, marginPoint[0] * picScale, marginPoint[1] * picScale, null);

        try {
            if (settingObj.getBoolean("timeWatermark")){
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setTextSize(50);
                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(settingObj.getString("timeWatermarkFormat"));
                Date currentDate = new Date();
                canvas.drawText(dateFormat.format(currentDate),5,bitmap.getHeight() - 40,paint);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return newBitmap;
    }

    // 保存 Bitmap 到设备存储的方法
    private void saveBitmapToStorage(Bitmap bitmap, String[] exifKeys, String[] exifData) {
        try {
            // 创建一个输出文件来保存图像
            File outputDirectory = getOutputDirectory();
            String format = settingObj.getString("photoNameFormat");
            @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            Date currentDate = new Date();
            String photoName = dateFormat.format(currentDate)+".jpg";

            final File outputFile = new File(outputDirectory, photoName);
            // 创建输出流
            FileOutputStream fos = new FileOutputStream(outputFile);
            // 将 Bitmap 压缩为 JPEG 格式并写入文件
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            // 关闭流
            fos.close();

            String imagePath = outputDirectory + "/" + photoName;

            String toastTexts = context.getString(R.string.picture);
            if (settingObj.getBoolean("exif")){
                saveExifData(imagePath,exifKeys,exifData);
                toastTexts += "+exif";
            }
            if (settingObj.getBoolean("gps") && mListener.getLocationServiceAvailablity()){
                addGPSToImage(imagePath);
                toastTexts += "+gps";
            }
            toastTexts += context.getString(R.string.hasSaved);

            String finalToastTexts = toastTexts;
            requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), finalToastTexts, Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void addGPSToImage(String imagePath) {
        double[] location = mListener.getLocation();
        double latitude = location[0];
        double longitude = location[1];
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);

            double exifLatitude = Math.abs(latitude);
            String exifLatitudeRef = latitude >= 0 ? "N" : "S";

            double exifLongitude = Math.abs(longitude);
            String exifLongitudeRef = longitude >= 0 ? "E" : "W";

            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDms(exifLatitude));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exifLatitudeRef);
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDms(exifLongitude));
            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exifLongitudeRef);
            exifInterface.saveAttributes();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static String convertToDms(double coord) {
        int degrees = (int) coord;
        coord = (coord - degrees) * 60;
        int minutes = (int) coord;
        coord = (coord - minutes) * 60;
        int seconds = (int) (coord * 1000);

        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }
    private String[] getExifData(String[] keys, String path){
        ExifInterface exifInterface;
        try {
            exifInterface = new ExifInterface(path);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] values = new String[keys.length];

        for (int i = 0;i < keys.length;i++) {
            String value = exifInterface.getAttribute(keys[i]);
            values[i] = value;
        }
        return values;
    }
    private void saveExifData(String imagePath, String[] keys, String[] data){
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            for (int i = 0;i < keys.length;i++) {
                exifInterface.setAttribute(keys[i], data[i]);
            }
            exifInterface.saveAttributes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*public void onSettingButtonClick(View view) {
        saveEnv();
        if (lightSensor != null) {
            sensorManager.unregisterListener(this);
        }
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, com.wyywn.fgcam.Settings.class);
        startActivity(intent);
    }*/

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

    public float lx2de(float lx){
        float de;
        float lowest = 0.2f;
        float minLx = 2;
        float maxLx = 102;
        if (lx <= minLx){
            de = lowest;
        } else if (lx > minLx && lx <= maxLx) {
            de = (lx - minLx) * (1 - lowest) / (maxLx - minLx) + lowest;
        }
        else {
            de = 1;
        }
        return de;
    }
    /*@Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lightIntensity = event.values[0];
            TextView lightIntensityTextView = findViewById(R.id.lightValue);
            lightIntensityTextView.setText("光线强度：" + lightIntensity + " lx");
            adjustLight(lx2de(lightIntensity));

            seekBar = binding.seekBar;
            seekBar.setProgress((int)((double)lx2de(lightIntensity) * 100));
        }
    }*/



    @Override
    public void onResume() {
        super.onResume();
        isFragmentVisible = true; // 标记Fragment可见

        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        hide();
        //delayedHide(100);
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

    /*private void toggle() {
        if (!isFragmentVisible) return;
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }*/

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