package com.shiroha.waifucamera.ui;

import static com.shiroha.waifucamera.ImagePreviewUtil.previewImageWithSpecificApp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shiroha.waifucamera.CustomAdapter;
import com.shiroha.waifucamera.MainActivity;
import com.shiroha.waifucamera.R;
import com.shiroha.waifucamera.databinding.FragmentHomeBinding;

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
import java.util.List;
import java.util.Objects;

import xyz.xxin.fileselector.FileSelector;
import xyz.xxin.fileselector.beans.FileBean;
import xyz.xxin.fileselector.interfaces.OnResultCallbackListener;
import xyz.xxin.fileselector.style.FileSelectorPathBarStyle;
import xyz.xxin.fileselector.style.FileSelectorTitleBarStyle;

public class HomeFragment extends Fragment {
    HomeFragment context;

    String dataPath;
    JSONArray fgLibObj;
    String fgLibFileName;
    String favorFileName;
    JSONArray favorObj;

    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        binding.buttonGuide.setOnClickListener(v -> dialogWebview(v,"guide.html"));
        binding.buttonAbout.setOnClickListener(v -> dialogWebview(v,"about.html"));
        binding.addButton.setOnClickListener(v -> onAddButtonClick(v));
        binding.addButton2.setOnClickListener(v -> onAddFavorButtonClick(v));

        dataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();
        fgLibFileName = dataPath + "/fglib.json";
        favorFileName = dataPath + "/favor.json";

        context = this;

        /*CameraPerspectiveView cameraView = new CameraPerspectiveView(getContext());
        cameraView.setRotationX(20f);*/

        return root;
    }

    public void checkFile(){
        loadFavor();
        try {
            boolean isErr = false;
            for (int i = 0; i < favorObj.length(); i++) {
                String path = favorObj.getString(i).split("@")[1];
                File file = new File(path);
                if (!file.exists()){
                    favorObj.remove(i);
                    isErr = true;
                }
            }
            if (isErr){
                saveFavor();
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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
    public void saveToFavor(String path, String name){
        if (name == null){
            String[] result = path.split("/");
            name = result[result.length-1];
        }

        favorObj.put(name.concat("@").concat(path));

        saveFavor();
        display();
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

    public void display(){
        int type = 0;
        List<String> finalFileList = convertJsonArrayToList(favorObj);

        ArrayList<String> arrayList = new ArrayList<>(finalFileList);

        RecyclerView recyclerView = binding.recycler;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        CustomAdapter.OnItemClickListener itemClickListener = position -> {
            String selectedItem = finalFileList.get(position);

            onClickFg(selectedItem,position);
        };
        CustomAdapter.OnItemLongClickListener itemLongClickListener = position -> {
            String selectedItem = finalFileList.get(position);
            String imagePath = selectedItem.split("@")[1];

            previewImageWithSpecificApp(getContext(), imagePath, "com.miui.gallery");
        };

        CustomAdapter adapter = new CustomAdapter(arrayList, itemClickListener, itemLongClickListener, type);
        recyclerView.setAdapter(adapter);
    }

    public void onClickFg(String info, int position){
        String name = info.split("@")[0];
        String path = info.split("@")[1];

        // 创建自定义对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.edit_favor_dialog, null);

        // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        TextView textInfo = dialogView.findViewById(R.id.textInfo);
        Button editButton = dialogView.findViewById(R.id.editButton);
        Button deleteButton = dialogView.findViewById(R.id.deleteButton);
        Button toFirstButton = dialogView.findViewById(R.id.toFirstButton);
        Button toLastButton = dialogView.findViewById(R.id.toLastButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        textInfo.setText(context.getString(R.string.name) + ": " + name + "\n" + context.getString(R.string.path) + ": " + path);

        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
        });
        deleteButton.setOnClickListener(v -> {
            favorObj.remove(position);
            saveFavor();
            display();
            dialog.dismiss();
        });
        toLastButton.setOnClickListener(v -> {
            favorObj.remove(position);
            favorObj.put(name.concat("@").concat(path));
            saveFavor();
            display();
            dialog.dismiss();
        });
        toFirstButton.setOnClickListener(v -> {
            JSONArray newArray = new JSONArray();
            newArray.put(info);
            favorObj.remove(position);
            for (int i = 0; i < favorObj.length(); i++) {
                try {
                    newArray.put(favorObj.getString(i));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            favorObj = newArray;
            saveFavor();
            display();
            dialog.dismiss();
        });
        editButton.setOnClickListener(v -> {
            dialog.dismiss();
            // 创建自定义对话框布局
            View dialogViewInner = getLayoutInflater().inflate(R.layout.add_dialog, null);

            // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
            AlertDialog dialogInner = new AlertDialog.Builder(getContext())
                    .setView(dialogViewInner)
                    .create();

            EditText editName = dialogViewInner.findViewById(R.id.editName);
            EditText editPath = dialogViewInner.findViewById(R.id.editPath);
            editName.setText(name);
            editPath.setText(path);

            TextView textView = dialogViewInner.findViewById(R.id.textHeadline);
            String title = context.getString(R.string.edit);
            textView.setText(title);

            dialogViewInner.findViewById(R.id.cancelButton).setOnClickListener(view -> {
                dialogInner.dismiss();
            });

            dialogViewInner.findViewById(R.id.submitButton).setOnClickListener(view -> {
                String inputName = editName.getText().toString();
                String inputPath = editPath.getText().toString();
                try {
                    favorObj.put(position,inputName.concat("@").concat(inputPath));
                    saveFavor();
                    display();
                    dialogInner.dismiss();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });

            dialogViewInner.findViewById(R.id.selectButton).setOnClickListener(view -> {
                FileSelectorTitleBarStyle fileSelectorTitleBarStyle = new FileSelectorTitleBarStyle();
                fileSelectorTitleBarStyle.setBackgroundColorId(R.color.purple_500);
                fileSelectorTitleBarStyle.setTitleText(title);
                FileSelectorPathBarStyle fileSelectorPathBarStyle = new FileSelectorPathBarStyle();
                fileSelectorPathBarStyle.setHeadItemBackgroundColorId(R.color.purple_500);
                fileSelectorPathBarStyle.setItemBackgroundColorId(R.color.purple_200);
                FileSelector.create(this)
                        .isSingle(true)
                        .isOnlySelectFile(true)
                        .addDisplayType("jpg", "bmp", "png", "webp")

                        .setFileSelectorTitleBarStyle(fileSelectorTitleBarStyle)
                        .setFileSelectorPathBatStyle(fileSelectorPathBarStyle)
                        .forResult(new OnResultCallbackListener() {
                            @Override
                            public void onResult(List<FileBean> result) {
                                // 文件处理逻辑
                                editPath.setText(result.get(0).getFile().getPath());
                                editName.setText(result.get(0).getName());
                            }
                            @Override
                            public void onCancel() {
                                // 未选择处理逻辑
                            }
                        });
            });
            // 显示对话框
            dialogInner.show();
        });

        // 显示对话框
        dialog.show();
    }

    public void dialogWebview(View view, String path){
        // 创建自定义对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.webview, null);

        // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        WebView webview = dialogView.findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.loadUrl("file:///android_asset/".concat(path));
        WebSettings webSettings = webview.getSettings();
        //缩放操作
        webSettings.setSupportZoom(false); //支持缩放，默认为true。是下面那个的前提
        // 设置出现缩放工具
        webSettings.setBuiltInZoomControls(false); //设置内置的缩放控件。若为false，则该WebView不可缩放
        webSettings.setDisplayZoomControls(true); //隐藏原生的缩放控件
        webSettings.setBuiltInZoomControls(false);//设置显示缩放按钮;如果设置这个为false则就不能手势缩放了
        webSettings.setLoadWithOverviewMode(false);
        webSettings.setUseWideViewPort(false);
        //允许webview对文件的操作
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowFileAccessFromFileURLs(false);

        //禁止上下左右滚动(不显示滚动条)
        webview.setScrollContainer(false);
        webview.setVerticalScrollBarEnabled(false);
        webview.setHorizontalScrollBarEnabled(false);


        // 显示对话框
        dialog.show();
    }

    public void onAddButtonClick(View view){
        // 创建自定义对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.add_dialog, null);

        // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        EditText editName = dialogView.findViewById(R.id.editName);
        EditText editPath = dialogView.findViewById(R.id.editPath);

        TextView textView = dialogView.findViewById(R.id.textHeadline);
        String title = context.getString(R.string.add) + context.getString(R.string.fgimageLib);
        textView.setText(title);

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.submitButton).setOnClickListener(v -> {
            String inputName = editName.getText().toString();
            String inputPath = editPath.getText().toString();
            saveToFgLib(inputPath,inputName,fgLibObj.length());
            saveFgLib();
            renderFgLibToPage();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.selectButton).setOnClickListener(v -> {
            FileSelectorTitleBarStyle fileSelectorTitleBarStyle = new FileSelectorTitleBarStyle();
            fileSelectorTitleBarStyle.setBackgroundColorId(R.color.purple_500);
            fileSelectorTitleBarStyle.setTitleText(title);
            FileSelectorPathBarStyle fileSelectorPathBarStyle = new FileSelectorPathBarStyle();
            fileSelectorPathBarStyle.setHeadItemBackgroundColorId(R.color.purple_500);
            fileSelectorPathBarStyle.setItemBackgroundColorId(R.color.purple_200);
            FileSelector.create(this)
                    .isSingle(true)
                    .isOnlyDisplayFolder(true)  // 只显示文件夹
                    .setFileSelectorTitleBarStyle(fileSelectorTitleBarStyle)
                    .setFileSelectorPathBatStyle(fileSelectorPathBarStyle)
                    .forResult(new OnResultCallbackListener() {
                        @Override
                        public void onResult(List<FileBean> result) {
                            // 文件处理逻辑
                            editPath.setText(result.get(0).getFile().getPath());
                            if (editName.getText().toString().isEmpty()){
                                editName.setText(result.get(0).getName());
                            }
                        }
                        @Override
                        public void onCancel() {
                            // 未选择处理逻辑
                        }
                    });
        });
        // 显示对话框
        dialog.show();
    }

    public void onAddFavorButtonClick(View view){
        // 创建自定义对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.add_dialog, null);

        // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        EditText editName = dialogView.findViewById(R.id.editName);
        EditText editPath = dialogView.findViewById(R.id.editPath);

        TextView textView = dialogView.findViewById(R.id.textHeadline);
        String title = context.getString(R.string.add) + context.getString(R.string.favorItem);
        textView.setText(title);

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.submitButton).setOnClickListener(v -> {
            String inputName = editName.getText().toString();
            String inputPath = editPath.getText().toString();
            saveToFavor(inputPath,inputName);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.selectButton).setOnClickListener(v -> {
            FileSelectorTitleBarStyle fileSelectorTitleBarStyle = new FileSelectorTitleBarStyle();
            fileSelectorTitleBarStyle.setBackgroundColorId(R.color.purple_500);
            fileSelectorTitleBarStyle.setTitleText(title);
            FileSelectorPathBarStyle fileSelectorPathBarStyle = new FileSelectorPathBarStyle();
            fileSelectorPathBarStyle.setHeadItemBackgroundColorId(R.color.purple_500);
            fileSelectorPathBarStyle.setItemBackgroundColorId(R.color.purple_200);
            FileSelector.create(this)
                    .isSingle(true)
                    .isOnlySelectFile(true)
                    .addDisplayType("jpg", "bmp", "png","webp")
                    .setFileSelectorTitleBarStyle(fileSelectorTitleBarStyle)
                    .setFileSelectorPathBatStyle(fileSelectorPathBarStyle)
                    .forResult(new OnResultCallbackListener() {
                        @Override
                        public void onResult(List<FileBean> result) {
                            // 文件处理逻辑
                            editPath.setText(result.get(0).getFile().getPath());
                            if (editName.getText().toString().isEmpty()){
                                editName.setText(result.get(0).getName());
                            }
                        }
                        @Override
                        public void onCancel() {
                            // 未选择处理逻辑
                        }
                    });
        });
        // 显示对话框
        dialog.show();
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
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), context.getString(R.string.fileNotFound), Toast.LENGTH_SHORT).show());
            e.printStackTrace();
        }
        return null;
    }

    public void saveFgLib(){
        try {
            FileWriter writer = new FileWriter(fgLibFileName);
            writer.write(fgLibObj.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void saveToFgLib(String path, String name, int position){
        if (name == null){
            String[] result = path.split("/");
            name = result[result.length-2];
        }
        JSONObject temp = new JSONObject();
        try {
            temp.put("name",name);
            temp.put("path",path);
            fgLibObj.put(position,temp);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public void onClickLib(int position){
        try {
            String name = fgLibObj.getJSONObject(position).getString("name");
            String path = fgLibObj.getJSONObject(position).getString("path");

            View dialogView = getLayoutInflater().inflate(R.layout.edit_favor_dialog, null);
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setView(dialogView)
                    .create();

            TextView textInfo = dialogView.findViewById(R.id.textInfo);
            Button editButton = dialogView.findViewById(R.id.editButton);
            Button deleteButton = dialogView.findViewById(R.id.deleteButton);
            Button toFirstButton = dialogView.findViewById(R.id.toFirstButton);
            Button toLastButton = dialogView.findViewById(R.id.toLastButton);
            Button cancelButton = dialogView.findViewById(R.id.cancelButton);

            textInfo.setText(context.getString(R.string.name) + ": " + name + "\n" + context.getString(R.string.path) + ": " + path);

            cancelButton.setOnClickListener(v -> {
                dialog.dismiss();
            });
            deleteButton.setOnClickListener(v -> {
                //deleteFgLib(position);
                fgLibObj.remove(position);
                saveFgLib();
                renderFgLibToPage();
                dialog.dismiss();
            });
            toLastButton.setOnClickListener(v -> {
                //deleteFgLib(position);
                fgLibObj.remove(position);
                saveToFgLib(path, name, position);
                saveFgLib();
                renderFgLibToPage();
                dialog.dismiss();
            });
            toFirstButton.setOnClickListener(v -> {
                JSONArray newArray = new JSONArray();
                try {
                    newArray.put(fgLibObj.getJSONObject(position));
                    fgLibObj.remove(position);
                    for (int i = 0; i < fgLibObj.length(); i++) {
                        try {
                            newArray.put(fgLibObj.getJSONObject(i));
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    fgLibObj = newArray;
                    saveFgLib();
                    renderFgLibToPage();
                    dialog.dismiss();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
            editButton.setOnClickListener(v -> {
                dialog.dismiss();

                // 创建自定义对话框布局
                View dialogViewInner = getLayoutInflater().inflate(R.layout.add_dialog, null);

                // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
                AlertDialog dialogInner = new AlertDialog.Builder(getContext())
                        .setView(dialogViewInner)
                        .create();

                EditText editName = dialogViewInner.findViewById(R.id.editName);
                EditText editPath = dialogViewInner.findViewById(R.id.editPath);
                editName.setText(name);
                editPath.setText(path);

                TextView textView = dialogViewInner.findViewById(R.id.textHeadline);
                String title = context.getString(R.string.edit);
                textView.setText(title);

                dialogViewInner.findViewById(R.id.cancelButton).setOnClickListener(view -> {
                    dialogInner.dismiss();
                });

                dialogViewInner.findViewById(R.id.submitButton).setOnClickListener(view -> {
                    String inputName = editName.getText().toString();
                    String inputPath = editPath.getText().toString();
                    JSONObject temp = new JSONObject();

                    try {
                        temp.put("name",inputName);
                        temp.put("path",inputPath);

                        fgLibObj.put(position,temp);

                        saveFgLib();
                        renderFgLibToPage();
                        dialogInner.dismiss();

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });

                dialogViewInner.findViewById(R.id.selectButton).setOnClickListener(view -> {
                    FileSelectorTitleBarStyle fileSelectorTitleBarStyle = new FileSelectorTitleBarStyle();
                    fileSelectorTitleBarStyle.setBackgroundColorId(R.color.purple_500);
                    fileSelectorTitleBarStyle.setTitleText(title);
                    FileSelectorPathBarStyle fileSelectorPathBarStyle = new FileSelectorPathBarStyle();
                    fileSelectorPathBarStyle.setHeadItemBackgroundColorId(R.color.purple_500);
                    fileSelectorPathBarStyle.setItemBackgroundColorId(R.color.purple_200);
                    FileSelector.create(this)
                            .isSingle(true)
                            .isOnlyDisplayFolder(true)
                            .setFileSelectorTitleBarStyle(fileSelectorTitleBarStyle)
                            .setFileSelectorPathBatStyle(fileSelectorPathBarStyle)
                            .forResult(new OnResultCallbackListener() {
                                @Override
                                public void onResult(List<FileBean> result) {
                                    // 文件处理逻辑
                                    editPath.setText(result.get(0).getFile().getPath());
                                    editName.setText(result.get(0).getName());
                                }
                                @Override
                                public void onCancel() {
                                    // 未选择处理逻辑
                                }
                            });
                });
                // 显示对话框
                dialogInner.show();
            });

            // 显示对话框
            dialog.show();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void renderFgLibToPage() {
        try {
            ArrayList<String> objs = new ArrayList<>();

            /*if (fgLibObj.length() == 0){
                return;
            }*/

            for (int i = 0; i < fgLibObj.length(); i++) {
                JSONObject jsonObject = fgLibObj.getJSONObject(i);
                //String id = jsonObject.getString("id");
                String name = jsonObject.getString("name");
                String path = jsonObject.getString("path");

                objs.add(context.getString(R.string.name) + ": " + name + "\n" + context.getString(R.string.path) + ": " + path);
            }
            ListView listview = binding.listView;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, objs);
            listview.setAdapter(adapter);

            listview.setOnItemClickListener((parent, view, position, id) -> {
                // 在这里处理列表项的点击事件
                //String selectedItem = (String) parent.getItemAtPosition(position);

                onClickLib(position);
            });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public void loadFgLib(){
        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(readFile("[]",fgLibFileName));
            fgLibObj = jsonArray;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private ActionBar getSupportActionBar() {
        if (getActivity() instanceof AppCompatActivity) {
            return ((AppCompatActivity) getActivity()).getSupportActionBar();
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.app_name);

        checkFile();

        loadFgLib();
        renderFgLibToPage();
        loadFavor();
        display();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}