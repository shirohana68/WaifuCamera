package com.shiroha.waifucamera.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.shiroha.waifucamera.databinding.FragmentTableBinding;

public class TableFragmentTemplate extends Fragment {
    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;

    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;
    private View mControlsView;
    private boolean mVisible;
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

        /*TableViewModel TableViewModel =
                new ViewModelProvider(this).get(TableViewModel.class);

        final TextView textView = binding.fullscreenContent;
        TableViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);*/

        mVisible = true;
        /*mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        mContentView.setOnClickListener(v -> toggle());
        binding.dummyButton.setOnTouchListener(mDelayHideTouchListener);*/
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