package com.example.mytasks;

import android.content.Context;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NoticeRepository {
    private final NoticeDao noticeDao;
    private final ApiService apiService;

    public NoticeRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.noticeDao = db.noticeDao();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }

    public void syncNotices(int projectId, DataSyncCallback<List<Notice>> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.getNotices(projectId).enqueue(new Callback<List<Notice>>() {
                @Override
                public void onResponse(Call<List<Notice>> call, Response<List<Notice>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            noticeDao.deleteNoticesByProject(projectId);
                            for (Notice notice : response.body()) {
                                noticeDao.insertNotice(notice);
                            }
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<List<Notice>> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                List<Notice> local = noticeDao.getNoticesByProject(projectId);
                callback.onSuccess(local);
            });
        }
    }

    public void createNotice(Notice notice, DataSyncCallback<Notice> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.createNotice(notice).enqueue(new Callback<Notice>() {
                @Override
                public void onResponse(Call<Notice> call, Response<Notice> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            noticeDao.insertNotice(response.body());
                            callback.onSuccess(response.body());
                        });
                    } else {
                        callback.onFailure("Server Error: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<Notice> call, Throwable t) {
                    callback.onFailure("Network Failure: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                noticeDao.insertNotice(notice);
                callback.onSuccess(notice);
            });
        }
    }

    public interface DataSyncCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }
}
