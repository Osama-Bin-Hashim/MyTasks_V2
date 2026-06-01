package com.example.mytasks;

import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @GET("/")
    Call<ResponseBody> testConnection();

    // AUTH
    @POST("/api/auth/register")
    Call<User> register(@Body User user);

    @POST("/api/auth/login")
    Call<User> loginUser(@Body LoginRequest loginRequest);

    // PROJECTS
    @GET("/api/projects")
    Call<List<Project>> getProjects(@Query("userId") int userId);

    @POST("/api/projects")
    Call<Project> createProject(@Body Project project);

    @POST("/api/projects/{proj_id}/enroll")
    Call<Project> enrollUser(@Path("proj_id") int projId, @Body UsernameRequest body);

    @POST("/api/projects/{proj_id}/remove")
    Call<Project> removeUser(@Path("proj_id") int projId, @Body UsernameRequest body);

    @PUT("/api/projects/{projectId}")
    Call<Project> updateProject(@Path("projectId") int projectId, @Body Project project);

    // TASKS
    @GET("/api/tasks/{projectId}")
    Call<List<Task>> getTasks(@Path("projectId") int projectId);

    @POST("/api/tasks")
    Call<Task> createTask(@Body Task task);

    @PUT("/api/tasks/{taskId}")
    Call<Task> updateTask(@Path("taskId") int taskId, @Body Task task);

    // NOTICES
    @GET("/api/notices/{projectId}")
    Call<List<Notice>> getNotices(@Path("projectId") int projectId);

    @POST("/api/notices")
    Call<Notice> createNotice(@Body Notice notice);

    // REQUESTS / MESSAGES
    @GET("/api/requests/{projectId}")
    Call<List<Request>> getRequests(@Path("projectId") int projectId, @Query("userId") int userId);

    @POST("/api/requests")
    Call<Request> sendRequest(@Body Request request);

    @PUT("/api/requests/{requestId}")
    Call<Request> updateRequest(@Path("requestId") int requestId, @Body Request request);
}
