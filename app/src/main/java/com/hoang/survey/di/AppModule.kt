package com.hoang.survey.di

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.blankj.utilcode.util.DeviceUtils
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hoang.survey.BuildConfig
import com.hoang.survey.api.SurveyServiceApi
import com.hoang.survey.authentication.AccessTokenAuthenticator
import com.hoang.survey.authentication.AccessTokenProvider
import com.hoang.survey.listsurveys.MainActivityViewModel
import com.hoang.survey.repository.SurveyRepository
import com.hoang.survey.repository.SurveyRepositoryImpl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidApplication
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val DEFAULT_NETWORK_TIMEOUT = 10L
val API_END_POINT = "https://nimble-survey-api.herokuapp.com/surveys.json/"
val REFRESH_TOKEN_ENDPOINT =
    "https://nimble-survey-api.herokuapp.com/oauth/token?grant_type=password&username=carlos%40nimbl3.com&password=antikera"
val REFRESH_TOKEN_TEST_ENDPOINT = "http://localhost:8080/refresh/token"

val sharableModule = module {
    single { providesOkHttpClient(accessTokenProvider = get()) }
    single { provideGson() }
    single { AccessTokenProvider.getInstance(pref = get(), secretKey = DeviceUtils.getAndroidID()) }
    single { provideSurveyRepositoryImpl(retrofit = get(), refreshTokenEndpoint = REFRESH_TOKEN_ENDPOINT) }
    single { androidApplication().getSharedPreferences("com.hoang.survey.pref", MODE_PRIVATE) as SharedPreferences }
}

val productionModule = module {
    viewModel { MainActivityViewModel(surveyRepository = get(), initialLoadRequest = 2, perPageItems = 4) }
    single {
        providesRetrofitAdapter(
            httpClient = get(),
            gson = get(),
            endPoint = API_END_POINT
        )
    }
}

val testModule = module {
    viewModel { MainActivityViewModel(surveyRepository = get(), initialLoadRequest = 1, perPageItems = 4) }
    single {
        providesRetrofitAdapter(
            httpClient = get(),
            gson = get(),
            endPoint = "http://127.0.0.1:8080/"
        )
    }
}

fun provideGson(): Gson {
    return GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
}

fun providesRetrofitAdapter(
    gson: Gson,
    httpClient: OkHttpClient,
    endPoint: String
): Retrofit {
    val retrofit = Retrofit.Builder()
        .client(httpClient)
        .baseUrl(endPoint)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
        .build()
    return retrofit
}

fun providesOkHttpClient(accessTokenProvider: AccessTokenProvider): OkHttpClient {
    val logInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    val tokenInterceptor = Interceptor {
        var request = it.request()
        if (request.url.pathSegments.lastOrNull()?.toString() == "token") { // don't add token in case refresh token is called
            it.proceed(request)
        } else {
            val url = request.url.newBuilder().addQueryParameter("access_token", accessTokenProvider.getToken()).build()
            request = request.newBuilder().url(url).build()
            it.proceed(request)
        }
    }

    return OkHttpClient.Builder().apply {
        readTimeout(DEFAULT_NETWORK_TIMEOUT, TimeUnit.SECONDS)
        writeTimeout(DEFAULT_NETWORK_TIMEOUT, TimeUnit.SECONDS)
        addInterceptor(tokenInterceptor)
        authenticator(AccessTokenAuthenticator(accessTokenProvider))
        addInterceptor(logInterceptor)
    }.build()
}

fun provideSurveyRepositoryImpl(retrofit: Retrofit, refreshTokenEndpoint: String): SurveyRepository {
    return SurveyRepositoryImpl(
        retrofit.create(SurveyServiceApi::class.java),
        refreshTokenEndpoint
    )
}