package com.shiroha.waifucamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {

    private ArrayList<String> mData;
    private int mType;
    private OnItemClickListener mlistener1;
    private OnItemLongClickListener mlistener2;

    public CustomAdapter(ArrayList<String> data, OnItemClickListener listener1, OnItemLongClickListener listener2,int type) {
        mData = data;
        mlistener1 = listener1;
        mlistener2 = listener2;
        mType = type;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_layout, parent, false);
        return new ViewHolder(view, mlistener1, mlistener2);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String path;
        String name;
        String data = mData.get(position);
        if (mType == 1){
            path = data;
            String[] result = path.split("/");
            name = result[result.length-1];
        }
        else {
            String[] result = data.split("@");
            path = result[1];
            name = result[0];
        }

        holder.textView.setText(name);

        Bitmap bmp= BitmapFactory.decodeFile(path);
        Bitmap croppedBitmap = bmp;
        if (bmp.getHeight() > bmp.getWidth() * 1.4){
            croppedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), (int) (bmp.getWidth() * 1.2));
        }

        //holder.imageView.setImageBitmap(croppedBitmap);

        //File file = new File(path);
        Glide.with(holder.imageView.getContext())
                .load(croppedBitmap)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnLongClickListener { // 添加长按监听接口

        public TextView textView;
        public ImageView imageView;
        private OnItemClickListener clickListener;
        private OnItemLongClickListener longClickListener; // 长按监听器

        public ViewHolder(View itemView, OnItemClickListener clickListener,
                          OnItemLongClickListener longClickListener) { // 添加长按参数
            super(itemView);
            textView = itemView.findViewById(R.id.textView);
            imageView = itemView.findViewById(R.id.imageView);

            this.clickListener = clickListener;
            this.longClickListener = longClickListener; // 初始化长按监听

            // 设置两种监听器
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this); // 设置长按监听
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition(); // 推荐使用新方法
            if (position != RecyclerView.NO_POSITION && clickListener != null) {
                clickListener.onItemClick(position);
            }
        }

        // 实现长按监听方法
        @Override
        public boolean onLongClick(View v) {
            int position = getAdapterPosition(); // 推荐使用新方法
            if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                longClickListener.onItemLongClick(position);
                return true; // 表示事件已处理
            }
            return false;
        }
    }

    // 点击监听接口
    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    // 新增长按监听接口
    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }
}
