package com.aulamaestra.api;

import com.aulamaestra.BuildConfig;

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
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(45, TimeUnit.SECONDS);
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor log = new HttpLoggingInterceptor();
                log.setLevel(HttpLoggingInterceptor.Level.BASIC);
                clientBuilder.addInterceptor(log);
            }
            OkHttpClient client = clientBuilder.build();
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
