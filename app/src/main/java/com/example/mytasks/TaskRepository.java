package com.example.mytasks;

import android.content.Context;
import android.util.Log;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TaskRepository {
    private final TaskDao taskDao;
    private final ApiService apiService;
    private final Context context;

    public TaskRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.taskDao = db.taskDao();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }

    public void syncTasks(int projectId, DataSyncCallback<List<Task>> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            // Retrieve dynamic userId if needed (though getTasks currently only takes projectId)
            // But we ensure no static/hardcoded IDs are used elsewhere
            apiService.getTasks(projectId).enqueue(new Callback<List<Task>>() {
                @Override
                public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            taskDao.deleteTasksByProject(projectId);
                            for (Task task : response.body()) {
                                taskDao.insertTask(task);
                            }
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<List<Task>> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                List<Task> localTasks = taskDao.getTasksByProjectSorted(projectId);
                callback.onSuccess(localTasks);
            });
        }
    }

    public void saveTask(Task task, DataSyncCallback<Task> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.createTask(task).enqueue(new Callback<Task>() {
                @Override
                public void onResponse(Call<Task> call, Response<Task> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            taskDao.insertTask(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Task> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                taskDao.insertTask(task);
                callback.onSuccess(task);
            });
        }
    }

    public void updateTask(Task task, DataSyncCallback<Task> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.updateTask(task.id, task).enqueue(new Callback<Task>() {
                @Override
                public void onResponse(Call<Task> call, Response<Task> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            taskDao.updateTask(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Task> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                taskDao.updateTask(task);
                callback.onSuccess(task);
            });
        }
    }

    public interface DataSyncCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }
}
