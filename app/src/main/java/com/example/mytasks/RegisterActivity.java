package com.example.mytasks;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mytasks.Activities.MainActivity;

public class RegisterActivity extends AppCompatActivity {

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        authRepository = new AuthRepository(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        AppCompatButton continueBtn = findViewById(R.id.continueBtn);
        TextView loginOption = findViewById(R.id.loginOption);

//        EditText passwordEditText = findViewById(R.id.password);
//
//        if (passwordEditText.getTransformationMethod() instanceof PasswordTransformationMethod) {
//            // Show password
//            passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
//        } else {
//            // Hide password
//            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
//        }
//        passwordEditText.setSelection(passwordEditText.getText().length());
//--------------------------- Register
        continueBtn.setOnClickListener(v -> {
            EditText nameInput = findViewById(R.id.regiName);
            EditText emailInput = findViewById(R.id.regiEmail);
            EditText password = findViewById(R.id.password);
            EditText reTypedPassword = findViewById(R.id.reTypedPassword);

            String username = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String pwd = password.getText().toString().trim();
            String rePwd = reTypedPassword.getText().toString().trim();

            if (username.isEmpty()) {
                nameInput.setError("Please enter the username");
                Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (email.isEmpty()){
                emailInput.setError("Please enter the email address");
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pwd.isEmpty()){
                password.setError("Please enter the password");
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (rePwd.isEmpty()){
                reTypedPassword.setError("Please re-type the password");
                Toast.makeText(this, "Re-Type the password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!pwd.equals(rePwd)) {
                reTypedPassword.setError("Passwords do not match");
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            User newUser = new User(username, email, pwd);
            authRepository.register(newUser, new AuthRepository.AuthCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        loginOption.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });

    }
}
