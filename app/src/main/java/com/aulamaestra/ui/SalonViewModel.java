package com.aulamaestra.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SalonViewModel extends ViewModel {
    public final MutableLiveData<Long> contentVersion = new MutableLiveData<>(0L);

    public void bump() {
        Long v = contentVersion.getValue();
        contentVersion.setValue(v == null ? 1L : v + 1);
    }
}
