package com.example.mytasks;

import android.content.Context;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RequestRepository {
    private final RequestDao requestDao;
    private final ApiService apiService;
    private final Context context;

    public RequestRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.requestDao = db.requestDao();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }

    public void syncRequests(int projectId, DataSyncCallback<List<Request>> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
            
            // Try reading every common key variation used across the app
            int userId = pref.getInt("LOGGED_IN_USER_ID", -1);
            if (userId == -1 || userId == 0) {
                userId = pref.getInt("userId", -1);
            }
            if (userId == -1 || userId == 0) {
                userId = pref.getInt("logged_in_user_id", -1);
            }

            // CRITICAL: If the ID is still invalid or 0, do not fire the network request
            if (userId <= 0) {
                callback.onFailure("Local Session Error: Valid User ID not found in SharedPreferences.");
                return;
            }

            apiService.getRequests(projectId, userId).enqueue(new Callback<List<Request>>() {
                @Override
                public void onResponse(Call<List<Request>> call, Response<List<Request>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            requestDao.deleteRequestsByProject(projectId);
                            for (Request req : response.body()) {
                                requestDao.insertRequest(req);
                            }
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<List<Request>> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                List<Request> local = requestDao.getRequestsByProject(projectId);
                callback.onSuccess(local);
            });
        }
    }

    public void sendRequest(Request request, DataSyncCallback<Request> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.sendRequest(request).enqueue(new Callback<Request>() {
                @Override
                public void onResponse(Call<Request> call, Response<Request> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            requestDao.insertRequest(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Request> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                requestDao.insertRequest(request);
                callback.onSuccess(request);
            });
        }
    }

    public void updateRequest(Request request, DataSyncCallback<Request> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.updateRequest(request.requestId, request).enqueue(new Callback<Request>() {
                @Override
                public void onResponse(Call<Request> call, Response<Request> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            requestDao.updateRequest(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Request> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                requestDao.updateRequest(request);
                callback.onSuccess(request);
            });
        }
    }

    public interface DataSyncCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }
}
