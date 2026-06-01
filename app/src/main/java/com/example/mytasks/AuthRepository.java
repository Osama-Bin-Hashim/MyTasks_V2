package com.example.mytasks;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private final UserDao userDao;
    private final ApiService apiService;
    private final Context context;

    public AuthRepository(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.userDao = db.userDao();
        this.apiService = RetrofitClient.getClient(context).create(ApiService.class);
    }

    public void login(String username, String password, AuthCallback<User> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            LoginRequest loginReq = new LoginRequest(username, password);

            apiService.loginUser(loginReq).enqueue(new Callback<User>() {
                @Override
                public void onResponse(Call<User> call, Response<User> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        User user = response.body();

                        // POINT 2: Extract and commit ID to SharedPreferences (Defensive multiple keys)
                        SharedPreferences prefs = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                        prefs.edit()
                                .putInt("LOGGED_IN_USER_ID", user.id)
                                .putInt("userId", user.id)
                                .putInt("logged_in_user_id", user.id)
                                .apply();

                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            // POINT 4: Sync with local DB using the server's ID
                            userDao.insertUser(user);
                            callback.onSuccess(user);
                        });
                    } else {
                        callback.onFailure("Server Auth Failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<User> call, Throwable t) {
                    callback.onFailure("Network Error: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                User user = userDao.getUserByUsername(username);
                if (user != null && Objects.equals(user.password, password)) {
                    callback.onSuccess(user);
                } else {
                    callback.onFailure("Invalid credentials or user not found");
                }
            });
        }
    }

    public void register(User newUser, AuthCallback<User> callback) {
        if (AppConfig.USE_SERVER_BACKEND) {
            apiService.register(newUser).enqueue(new Callback<User>() {
                @Override
                public void onResponse(Call<User> call, Response<User> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        User user = response.body();

                        // POINT 2: Extract and commit ID to SharedPreferences (Defensive multiple keys)
                        SharedPreferences prefs = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                        prefs.edit()
                                .putInt("LOGGED_IN_USER_ID", user.id)
                                .putInt("userId", user.id)
                                .putInt("logged_in_user_id", user.id)
                                .apply();

                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            userDao.insertUser(user);
                            callback.onSuccess(user);
                        });
                    } else {
                        callback.onFailure("Server Registration Failed: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<User> call, Throwable t) {
                    callback.onFailure("Network Error: " + t.getMessage());
                }
            });
        } else {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                User existingUser = userDao.getUserByUsername(newUser.username);
                User existingMail = userDao.getUserByEmail(newUser.email);
                if (existingUser != null) {
                    callback.onFailure("Username already taken.");
                } else if (existingMail != null) {
                    callback.onFailure("Email already exists.");
                } else {
                    userDao.insertUser(newUser);
                    callback.onSuccess(newUser);
                }
            });
        }
    }

    public interface AuthCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }
}
