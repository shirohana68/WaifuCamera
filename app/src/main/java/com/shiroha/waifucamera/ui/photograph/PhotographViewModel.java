package com.shiroha.waifucamera.ui.photograph;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PhotographViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public PhotographViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is photograph fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}