package com.example.babybedapp.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Baby API 接口
 */
public interface BabyApi {

    @POST("analyze/baby")
    Call<AnalyzeResponse> analyze(@Body AnalyzeRequest request);
}
