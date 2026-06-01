package com.example.mytasks.Activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mytasks.AppDatabase;
import com.example.mytasks.R;
import com.example.mytasks.Task;
import com.example.mytasks.TaskRepository;

import java.util.List;

public class EmployeePerformanceActivity extends AppCompatActivity {
    private TaskRepository taskRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_performance);

        taskRepository = new TaskRepository(this);

        int activeProjectId = getIntent().getIntExtra("PROJECT_ID", -1);
        SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        String currentUsername = pref.getString("LOGGED_IN_USERNAME", "");

        loadPerformanceData(activeProjectId, currentUsername);
    }

    private void loadPerformanceData(int projectId, String username) {
        taskRepository.syncTasks(projectId, new TaskRepository.DataSyncCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> data) {
                AppDatabase db = AppDatabase.getInstance(EmployeePerformanceActivity.this);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    int total = db.taskDao().getEmployeeTaskCount(projectId, username);
                    int completed = db.taskDao().getEmployeeCompletedTaskCount(projectId, username);
                    
                    int rate = 0;
                    if (total > 0) {
                        rate = (completed * 100) / total;
                    }

                    final int finalRate = rate;
                    final int finalTotal = total;
                    final int finalCompleted = completed;
                    
                    runOnUiThread(() -> {
                        TextView tvTotal = findViewById(R.id.tvTotalTasks);
                        TextView tvCompleted = findViewById(R.id.tvCompletedTasks);
                        TextView tvRate = findViewById(R.id.tvCompletionRate);
                        ProgressBar pb = findViewById(R.id.pbCompletion);

                        tvTotal.setText("Your Total Assigned Tasks: " + finalTotal);
                        tvCompleted.setText("Completed Tasks: " + finalCompleted);
                        tvRate.setText("Your Task Completion Rate: " + finalRate + "%");
                        pb.setProgress(finalRate);
                    });
                });
            }

            @Override
            public void onFailure(String error) {
                // Fallback to local data if sync fails
                AppDatabase db = AppDatabase.getInstance(EmployeePerformanceActivity.this);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    int total = db.taskDao().getEmployeeTaskCount(projectId, username);
                    int completed = db.taskDao().getEmployeeCompletedTaskCount(projectId, username);
                    
                    int rate = (total > 0) ? (completed * 100) / total : 0;
                    final int finalRate = rate;
                    final int finalTotal = total;
                    final int finalCompleted = completed;

                    runOnUiThread(() -> {
                        TextView tvTotal = findViewById(R.id.tvTotalTasks);
                        TextView tvCompleted = findViewById(R.id.tvCompletedTasks);
                        TextView tvRate = findViewById(R.id.tvCompletionRate);
                        ProgressBar pb = findViewById(R.id.pbCompletion);

                        tvTotal.setText("Your Total Assigned Tasks: " + finalTotal);
                        tvCompleted.setText("Completed Tasks: " + finalCompleted);
                        tvRate.setText("Your Task Completion Rate: " + finalRate + "% (Offline)");
                        pb.setProgress(finalRate);
                    });
                });
            }
        });
    }
}
