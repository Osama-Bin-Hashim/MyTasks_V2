package com.example.mytasks;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mytasks.Activities.MainActivity;
import com.example.mytasks.databinding.ActivityLoginBinding;

import java.util.Objects;

import android.widget.EditText;
import android.content.SharedPreferences;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authRepository = new AuthRepository(this);

        // SESSION PERSISTENCE: Check for existing login
        android.content.SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        int userId = pref.getInt("LOGGED_IN_USER_ID", -1);
        String username = pref.getString("LOGGED_IN_USERNAME", "");

        // RELAXED CHECK: Redirect if we have a valid ID OR a non-empty username
        if (userId != -1 || !username.isEmpty()) {
            Intent intent = new Intent(this, MainActivity.class);
            if (userId != -1) intent.putExtra("USER_ID", userId);
            startActivity(intent);
            finish();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // INITIALIZE SERVER IP FIELD
        SharedPreferences netPref = getSharedPreferences("NetworkConfig", MODE_PRIVATE);
        String savedIp = netPref.getString("SERVER_IP", AppConfig.DEFAULT_IP);
        EditText etIp = findViewById(R.id.etServerIp);
        etIp.setText(savedIp);

        findViewById(R.id.btnSaveIp).setOnClickListener(v -> {
            String newIp = etIp.getText().toString().trim();
            if (!newIp.isEmpty()) {
                testConnectionAndSave(newIp, netPref);
            }
        });

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.loginBtn.setOnClickListener(v -> handleLogin());

        binding.registerOption.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void handleLogin() {
        String username = binding.loginEmail.getText().toString().trim();
        String password = binding.loginPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.loginBtn.setVisibility(View.GONE);
        binding.loginAcProgressBar.setVisibility(View.VISIBLE);

        authRepository.login(username, password, new AuthRepository.AuthCallback<User>() {
            @Override
            public void onSuccess(User user) {
                runOnUiThread(() -> {
                    binding.loginBtn.setVisibility(View.VISIBLE);
                    binding.loginAcProgressBar.setVisibility(View.GONE);
                    binding.tvConnWarningLogin.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "✅ Login Successful!", Toast.LENGTH_SHORT).show();
                    saveSessionAndFinish(user);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    binding.loginBtn.setVisibility(View.VISIBLE);
                    binding.loginAcProgressBar.setVisibility(View.GONE);
                    if (AppConfig.USE_SERVER_BACKEND) {
                        binding.tvConnWarningLogin.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveSessionAndFinish(User user) {
        getSharedPreferences("UserSession", MODE_PRIVATE)
                .edit()
                .putInt("LOGGED_IN_USER_ID", user.id)
                .putInt("userId", user.id)
                .putInt("logged_in_user_id", user.id)
                .putString("LOGGED_IN_USERNAME", user.username)
                .putString("LOGGED_IN_USER_ROLE", user.role)
                .apply();

        Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_ID", user.id);
        startActivity(intent);
        finish();
    }

    private void testConnectionAndSave(String ip, SharedPreferences pref) {
        String testUrl = "http://" + ip + ":5000/";
        Retrofit tempRetrofit = new Retrofit.Builder()
                .baseUrl(testUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService testService = tempRetrofit.create(ApiService.class);
        testService.testConnection().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // Any response (even error code) means the server is there!
                pref.edit().putString("SERVER_IP", ip).apply();
                RetrofitClient.resetClient();
                binding.tvConnWarningLogin.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "✅ Connected to server successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                binding.tvConnWarningLogin.setVisibility(View.VISIBLE);
                Toast.makeText(LoginActivity.this, "❌ Connection failed. Verify your server is running and the IP is correct.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
