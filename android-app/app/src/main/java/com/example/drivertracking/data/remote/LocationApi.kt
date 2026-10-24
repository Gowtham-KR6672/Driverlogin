package com.example.drivertracking.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Headers

interface LocationApi {
    @Headers("${RetryInterceptor.HEADER}: ${RetryInterceptor.SAFE_TO_REPEAT}")
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @Headers("${RetryInterceptor.HEADER}: ${RetryInterceptor.NO_RETRY}")
    @POST("/api/driver/location")
    suspend fun sendLocation(@Body payload: LocationPayload): Response<LocationResponse>

    @GET("/api/home")
    suspend fun getHomeStats(
        @retrofit2.http.Query("driver_id") driverId: Int,
        @retrofit2.http.Query("year") year: Int?,
        @retrofit2.http.Query("month") month: Int?
    ): Response<HomeResponse>

    @GET("/api/work-entry/active")
    suspend fun getActiveWork(@retrofit2.http.Query("driver_id") driverId: Int): Response<ActiveWorkResponse>

    @POST("/api/work-entry/start")
    suspend fun startWork(@Body request: StartWorkRequest): Response<GenericResponse>

    @POST("/api/work-entry/end")
    suspend fun endWork(@Body request: EndWorkRequest): Response<GenericResponse>

    @POST("/api/work-entry/continue")
    suspend fun continueWork(@Body request: ContinueWorkRequest): Response<GenericResponse>

    @GET("/api/history")
    suspend fun getHistory(@retrofit2.http.Query("driver_id") driverId: Int): Response<HistoryResponse>

    // --- Admin APIs ---

    @GET("/api/admin/dashboard")
    suspend fun getAdminDashboard(): Response<AdminDashboardResponse>

    @GET("/api/admin/users")
    suspend fun getAdminUsers(): Response<AdminUsersResponse>

    @POST("/api/admin/users")
    suspend fun createAdminUser(@Body payload: Map<String, String>): Response<GenericResponse>

    @POST("/api/admin/users/{userId}/reset-password")
    suspend fun resetUserPassword(
        @retrofit2.http.Path("userId") userId: Int,
        @Body payload: Map<String, String>
    ): Response<GenericResponse>

    @POST("/api/admin/users/{userId}/delete")
    suspend fun deleteUser(
        @retrofit2.http.Path("userId") userId: Int,
        @Body payload: Map<String, String>
    ): Response<GenericResponse>

    @GET("/api/admin/work")
    suspend fun getAdminWork(
        @retrofit2.http.Query("user_id") userId: Int
    ): Response<AdminWorkResponse>
}
