package com.shiroha.waifucamera;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;

public class ImagePreviewUtil {

    // 通过系统应用预览图片（支持文件路径）
    public static void previewImageWithSystemViewer(Context context, String imagePath) {
        File imageFile = new File(imagePath);
        Uri imageUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider", // 与AndroidManifest中一致
                imageFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(imageUri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 临时授权
        context.startActivity(intent);
    }

    public static void previewImageWithSpecificApp(Context context, String imagePath, String packageName) {
        File imageFile = new File(imagePath);
        Uri imageUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                imageFile
        );

        // 创建图片查看 Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(imageUri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // 设置特定应用包名
        intent.setPackage(packageName);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // 如果指定应用不存在，使用默认方式
            intent.setPackage(null);
            context.startActivity(Intent.createChooser(intent, "选择图片查看器"));
        }
    }

    public static void previewImageWithComponent(Context context, String imagePath,
                                                 String packageName, String className) {
        File imageFile = new File(imagePath);
        Uri imageUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                imageFile
        );

        // 创建组件 Intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(imageUri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // 设置特定组件
        ComponentName component = new ComponentName(packageName, className);
        intent.setComponent(component);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // 回退到默认方式
            intent.setComponent(null);
            context.startActivity(Intent.createChooser(intent, "选择图片查看器"));
        }
    }


}