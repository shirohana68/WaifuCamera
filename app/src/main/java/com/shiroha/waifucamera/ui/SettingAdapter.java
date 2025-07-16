package com.shiroha.waifucamera.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.shiroha.waifucamera.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SettingAdapter extends RecyclerView.Adapter<SettingAdapter.ViewHolder> {

    private JSONArray templateArr;
    private OnItemClickListener mlistener;
    private List<SettingItem> settingItems; // 存储所有设置项的列表

    // 添加内部类表示设置项
    public static class SettingItem {
        public String name;
        public String valueType;
        public Object currentValue;
        public String description;

        public SettingItem(String name, String valueType, Object currentValue, String description) {
            this.name = name;
            this.valueType = valueType;
            this.currentValue = currentValue;
            this.description = description;
        }
    }

    public SettingAdapter(JSONObject setting, JSONArray template, OnItemClickListener listener) {
        templateArr = template;
        mlistener = listener;
        settingItems = new ArrayList<>();

        // 初始化设置项列表
        try {
            for (int i = 0; i < template.length(); i++) {
                JSONObject item = template.getJSONObject(i);
                String name = item.getString("name");
                String valueType = item.getString("valueType");
                String description = item.getString("description");

                // 从设置对象中获取当前值
                Object currentValue = null;
                switch (valueType) {
                    case "boolean":
                        currentValue = setting.getBoolean(name);
                        break;
                    case "double":
                        currentValue = setting.getDouble(name);
                        break;
                    case "string":
                        currentValue = setting.getString(name);
                        break;
                }

                settingItems.add(new SettingItem(name, valueType, currentValue, description));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.setting_layout, parent, false);
        return new ViewHolder(view, mlistener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        SettingItem item = settingItems.get(position);
        holder.textView.setText(item.description);

        switch (item.valueType) {
            case "boolean":
                holder.switchWid.setVisibility(View.VISIBLE);
                holder.editText.setVisibility(View.GONE);
                holder.switchWid.setChecked((Boolean) item.currentValue);

                // 添加开关状态变化监听
                holder.switchWid.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.currentValue = isChecked;
                });
                break;
            case "double":
            case "string":
                holder.editText.setVisibility(View.VISIBLE);
                holder.switchWid.setVisibility(View.GONE);
                holder.editText.setText(item.currentValue.toString());

                // 添加文本变化监听
                holder.editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (item.valueType.equals("double")) {
                            try {
                                item.currentValue = Double.parseDouble(s.toString());
                            } catch (NumberFormatException e) {
                                // 处理无效输入，可以设置默认值或忽略
                            }
                        } else {
                            item.currentValue = s.toString();
                        }
                    }
                });
                break;
        }
    }

    @Override
    public int getItemCount() {
        return settingItems.size();
    }

    // 添加获取设置项列表的方法
    public List<SettingItem> getSettingItems() {
        return settingItems;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView textView;
        public Switch switchWid;
        public EditText editText;
        private OnItemClickListener listener;

        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView1);
            switchWid = itemView.findViewById(R.id.switch1);
            editText = itemView.findViewById(R.id.editText);

            this.listener = listener;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && listener != null) {
                listener.onItemClick(position);
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }
}