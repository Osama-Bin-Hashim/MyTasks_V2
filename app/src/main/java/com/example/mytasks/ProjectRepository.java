package com.example.mytasks;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProjectRepository {
    private final ProjectDao projectDao;
    private final ApiService apiService;
    private final Context context;

    public ProjectRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.projectDao = db.projectDao();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }

    public void syncProjects(DataSyncCallback<List<Project>> callback) {
        syncProjects(-1, callback);
    }

    public void syncProjects(int explicitUserId, DataSyncCallback<List<Project>> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            int userId = explicitUserId;

            if (userId <= 0) {
                android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                
                // Try reading every common key variation used across the app
                userId = pref.getInt("LOGGED_IN_USER_ID", -1);
                if (userId == -1 || userId == 0) {
                    userId = pref.getInt("userId", -1);
                }
                if (userId == -1 || userId == 0) {
                    userId = pref.getInt("logged_in_user_id", -1);
                }
            }

            // CRITICAL: If the ID is still invalid or 0, do not fire the network request
            if (userId <= 0) {
                callback.onFailure("Local Session Error: Valid User ID not found in SharedPreferences.");
                return;
            }

            apiService.getProjects(userId).enqueue(new Callback<List<Project>>() {
                @Override
                public void onResponse(Call<List<Project>> call, Response<List<Project>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            // POINT 2: Pure Server-Mirror strategy (wipe then repopulate)
                            projectDao.deleteAllProjects();

                            for (Project project : response.body()) {
                                projectDao.insertProject(project);
                            }
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<List<Project>> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                List<Project> local = projectDao.getAllProjects();
                callback.onSuccess(local);
            });
        }
    }

    public void createProject(Project project, DataSyncCallback<Project> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.createProject(project).enqueue(new Callback<Project>() {
                @Override
                public void onResponse(Call<Project> call, Response<Project> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            projectDao.insertProject(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Project> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                projectDao.insertProject(project);
                callback.onSuccess(project);
            });
        }
    }

    public void updateProject(Project project, DataSyncCallback<Project> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.updateProject(project.id, project).enqueue(new Callback<Project>() {
                @Override
                public void onResponse(Call<Project> call, Response<Project> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            projectDao.updateProject(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Project> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                projectDao.updateProject(project);
                callback.onSuccess(project);
            });
        }
    }

    public void enrollUserInProject(int projId, String username, DataSyncCallback<Project> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            UsernameRequest body = new UsernameRequest(username);
            apiService.enrollUser(projId, body).enqueue(new Callback<Project>() {
                @Override
                public void onResponse(Call<Project> call, Response<Project> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            projectDao.updateProject(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Enrollment failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Project> call, Throwable t) {
                    callback.onFailure("Network error: " + t.getMessage());
                }
            });
        } else {
            // Local-only logic
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Project project = projectDao.getProjectById(projId);
                if (project != null) {
                    String currentRoster = project.projectRoster;
                    if (currentRoster == null) currentRoster = "";
                    if (!currentRoster.contains(username)) {
                        if (!currentRoster.isEmpty()) currentRoster += ", ";
                        currentRoster += username;
                        project.projectRoster = currentRoster;
                        projectDao.updateProject(project);
                    }
                    callback.onSuccess(project);
                } else {
                    callback.onFailure("Project not found locally");
                }
            });
        }
    }

    public void removeUserFromProject(int projId, String username, DataSyncCallback<Project> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            UsernameRequest body = new UsernameRequest(username);
            apiService.removeUser(projId, body).enqueue(new Callback<Project>() {
                @Override
                public void onResponse(Call<Project> call, Response<Project> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            projectDao.updateProject(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Removal failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Project> call, Throwable t) {
                    callback.onFailure("Network error: " + t.getMessage());
                }
            });
        } else {
            // Local-only logic
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Project project = projectDao.getProjectById(projId);
                if (project != null && project.projectRoster != null) {
                    List<String> rosterList = new ArrayList<>(Arrays.asList(project.projectRoster.split(", ")));
                    if (rosterList.remove(username)) {
                        project.projectRoster = String.join(", ", rosterList);
                        projectDao.updateProject(project);
                    }
                    callback.onSuccess(project);
                } else {
                    callback.onFailure("Project or roster not found locally");
                }
            });
        }
    }

    public interface DataSyncCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }
}
