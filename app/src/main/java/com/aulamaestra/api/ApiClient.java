package com.aulamaestra.api;

import com.aulamaestra.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static AulaApiService service;

    private ApiClient() {
    }

    public static AulaApiService api() {
        if (service == null) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(log)
                    .build();
            String base = BuildConfig.API_BASE_URL.trim();
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                base = "https://" + base;
            }
            if (!base.endsWith("/")) {
                base = base + "/";
            }
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(base)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            service = retrofit.create(AulaApiService.class);
        }
        return service;
    }
}
