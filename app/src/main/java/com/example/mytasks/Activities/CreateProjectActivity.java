package com.example.mytasks.Activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mytasks.AppDatabase;
import com.example.mytasks.Project;
import com.example.mytasks.ProjectRepository;
import com.example.mytasks.User;
import com.example.mytasks.databinding.ActivityCreateProjectBinding;

import java.util.List;

public class CreateProjectActivity extends AppCompatActivity {

    private ActivityCreateProjectBinding binding;
    private ProjectRepository projectRepository;
    private int currentUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectRepository = new ProjectRepository(this);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Get current user ID from intent or fetch first available user
        currentUserId = getIntent().getIntExtra("USER_ID", -1);
        if (currentUserId == -1) {
            fetchFirstUser();
        }

        binding.btnSubmitProject.setOnClickListener(v -> {
            String projectName = binding.inputProjectName.getText().toString().trim();
            if (projectName.isEmpty()) {
                binding.inputProjectName.setError("Project name required");
                return;
            }

            if (currentUserId != -1) {
                saveProject(projectName);
            } else {
                Toast.makeText(this, "User session not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchFirstUser() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<User> users = db.userDao().getAllUsers();
            if (!users.isEmpty()) {
                currentUserId = users.get(0).id;
            }
        });
    }

    private void saveProject(String name) {
        Project newProject = new Project(name, currentUserId);
        projectRepository.createProject(newProject, new ProjectRepository.DataSyncCallback<Project>() {
            @Override
            public void onSuccess(Project data) {
                runOnUiThread(() -> {
                    Toast.makeText(CreateProjectActivity.this, "Project Created successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(CreateProjectActivity.this, "Failed to sync project: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
