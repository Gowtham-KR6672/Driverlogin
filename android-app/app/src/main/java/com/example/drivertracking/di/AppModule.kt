package com.example.drivertracking.di

import android.content.Context
import androidx.room.Room
import com.example.drivertracking.data.local.AppDatabase
import com.example.drivertracking.data.local.LocationDao
import com.example.drivertracking.data.remote.LocationApi
import com.example.drivertracking.data.remote.RetryInterceptor
import com.example.drivertracking.util.Constants
import com.example.drivertracking.util.ServerStatus
import com.example.drivertracking.util.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(sessionManager: SessionManager, serverStatus: ServerStatus): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            val token = sessionManager.getAuthToken()
            token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
            val response = chain.proceed(requestBuilder.build())
            if (response.code == 401 && token != null) {
                sessionManager.expireSession(token)
            }
            response
        }

        // A sleeping server takes about a minute to start (see RetryInterceptor), so wait for it
        // instead of failing at OkHttp's 10 second default. The call timeout caps all retries.
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(serverStatus))
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLocationApi(retrofit: Retrofit): LocationApi {
        return retrofit.create(LocationApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "driver_tracking.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: AppDatabase): LocationDao {
        return database.locationDao()
    }
}
