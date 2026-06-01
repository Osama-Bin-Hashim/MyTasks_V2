package com.example.mytasks.Activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.mytasks.Adapters.TaskAdapter;
import com.example.mytasks.AppDatabase;
import com.example.mytasks.NotificationHelper;
import com.example.mytasks.Project;
import com.example.mytasks.Task;
import com.example.mytasks.TaskRepository;
import com.example.mytasks.databinding.ActivityTodoWorkspaceBinding;

import java.util.List;

public class TodoWorkspaceActivity extends AppCompatActivity implements TaskAdapter.OnTaskActionListener {

    private ActivityTodoWorkspaceBinding binding;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;
    private int projectId;
    private boolean isManager;
    private String currentUsername = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTodoWorkspaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        projectId = getIntent().getIntExtra("PROJECT_ID", -1);
        isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        currentUsername = getIntent().getStringExtra("LOGGED_IN_USERNAME");

        taskRepository = new TaskRepository(this);
        
        if (!isManager) {
            markTasksAsRead();
        }
        
        loadInitialData();
    }

    private void markTasksAsRead() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.taskDao().markTasksAsRead(projectId, currentUsername);
        });
    }

    private void loadInitialData() {
        taskRepository.syncTasks(projectId, new TaskRepository.DataSyncCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                runOnUiThread(() -> setupRecyclerView(tasks));
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(TodoWorkspaceActivity.this, "Sync failed: " + error + ". Showing cached data.", Toast.LENGTH_LONG).show();
                    // Local fallback already handled by Repository logic or we can trigger it here
                    fetchLocalData();
                });
            }
        });
    }

    private void fetchLocalData() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Task> localTasks = db.taskDao().getTasksByProjectSorted(projectId);
            runOnUiThread(() -> setupRecyclerView(localTasks));
        });
    }

    private void setupRecyclerView(List<Task> tasks) {
        adapter = new TaskAdapter(isManager, currentUsername, this);
        adapter.setTasks(tasks);
        binding.todoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.todoRecyclerView.setAdapter(adapter);
    }

    @Override
    public void onMarkDone(Task task) {
        task.status = "DONE";
        // Mocking execution metrics: 80% of limit
        task.timeTakenMillis = (long) (task.timeLimitMillis * 0.8);

        taskRepository.updateTask(task, new TaskRepository.DataSyncCallback<Task>() {
            @Override
            public void onSuccess(Task updatedTask) {
                loadInitialData();
                runOnUiThread(() -> {
                    Toast.makeText(TodoWorkspaceActivity.this, "Task marked as DONE", Toast.LENGTH_SHORT).show();
                    NotificationHelper.showNotification(TodoWorkspaceActivity.this, "Task Completed!", 
                        currentUsername + " finished: " + updatedTask.title);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(TodoWorkspaceActivity.this, "Failed to sync update: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
