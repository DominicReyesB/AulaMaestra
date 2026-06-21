package com.aulamaestra;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.aulamaestra.api.ApiClient;
import com.aulamaestra.db.SessionManager;
import com.aulamaestra.ui.LoginActivity;

public class AulaMaestraApp extends Application implements DefaultLifecycleObserver {
    private static final long SESSION_CLOSE_DELAY_MS = 15_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sessionClosedInBackground;
    private final Runnable closeBackgroundSession = () -> {
        new SessionManager(this).clearAll();
        ApiClient.cancelPendingRequests();
        sessionClosedInBackground = true;
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        mainHandler.removeCallbacks(closeBackgroundSession);
        if (!sessionClosedInBackground) {
            return;
        }
        sessionClosedInBackground = false;
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(login);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        mainHandler.removeCallbacks(closeBackgroundSession);
        mainHandler.postDelayed(closeBackgroundSession, SESSION_CLOSE_DELAY_MS);
    }
}
