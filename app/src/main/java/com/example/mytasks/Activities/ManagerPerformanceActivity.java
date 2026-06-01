package com.example.mytasks.Activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mytasks.Adapters.RosterAdapter;
import com.example.mytasks.AppDatabase;
import com.example.mytasks.Models.RosterStats;
import com.example.mytasks.Project;
import com.example.mytasks.ProjectRepository;
import com.example.mytasks.R;
import com.example.mytasks.Task;
import com.example.mytasks.TaskRepository;
import com.example.mytasks.TaskRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ManagerPerformanceActivity extends AppCompatActivity implements RosterAdapter.OnRemoveMemberListener {
    
    private int activeProjectId;
    private Project activeProject;
    private RosterAdapter rosterAdapter;
    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private boolean isManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_performance);

        // SECURITY SHIELD
        isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        if (!isManager) {
            Toast.makeText(this, "Access Denied: Managers Only.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        activeProjectId = getIntent().getIntExtra("PROJECT_ID", -1);
        projectRepository = new ProjectRepository(this);
        taskRepository = new TaskRepository(this);
        
        // PERSISTENT USER GREETING
        SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        String currentSessionUsername = pref.getString("LOGGED_IN_USERNAME", "User");
        int currentUserId = pref.getInt("LOGGED_IN_USER_ID", -1);
        ((TextView)findViewById(R.id.tvDashboardTitle)).setText("Dashboard: " + currentSessionUsername);

        if (currentUserId != -1) {
            pref.edit().putLong("LAST_PERFORMANCE_VIEW_" + currentUserId + "_" + activeProjectId, System.currentTimeMillis()).apply();
        }

        setupRecyclerView();
        loadProjectAnalytics(activeProjectId);
        setupRosterEnrollment();
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.rvRosterPerformance);
        rosterAdapter = new RosterAdapter(isManager, this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(rosterAdapter);
    }

    @Override
    public void onRemoveMember(String username) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + username + " from this project? This will also unassign them from all tasks.")
                .setPositiveButton("Remove", (dialog, which) -> executeMemberRemoval(username))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeMemberRemoval(String username) {
        if (activeProject != null && activeProject.projectRoster != null) {
            // SECURITY_AUDIT
            SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
            String managerName = pref.getString("LOGGED_IN_USERNAME", "Unknown");
            Log.d("SECURITY_AUDIT", "Manager " + managerName + " removing user " + username + " from Project ID: " + activeProjectId);

            // 1. Remove from Roster
            List<String> rosterList = new ArrayList<>(Arrays.asList(activeProject.projectRoster.split(", ")));
            rosterList.remove(username);
            activeProject.projectRoster = String.join(", ", rosterList);
            
            projectRepository.updateProject(activeProject, new ProjectRepository.DataSyncCallback<Project>() {
                @Override
                public void onSuccess(Project data) {
                    // 2. Unassign from Tasks (Note: we should ideally sync this too, but for now local)
                    AppDatabase db = AppDatabase.getInstance(ManagerPerformanceActivity.this);
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        List<Task> tasks = db.taskDao().getTasksByProject(activeProjectId);
                        for (Task task : tasks) {
                            if (task.assigneeId != null && task.assigneeId.contains(username)) {
                                List<String> assignees = new ArrayList<>(Arrays.asList(task.assigneeId.split(", ")));
                                assignees.remove(username);
                                task.assigneeId = String.join(", ", assignees);
                                db.taskDao().updateTask(task);

                                // REPLICATION GATE: Sync assignee changes to server
                                taskRepository.updateTask(task, new TaskRepository.DataSyncCallback<Task>() {
                                    @Override
                                    public void onSuccess(Task data) {
                                        Log.d("SYNC", "Task assignee updated on server for task ID: " + task.id);
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Log.e("SYNC", "Failed to sync task update: " + error);
                                    }
                                });
                            }
                        }
                        runOnUiThread(() -> {
                            Toast.makeText(ManagerPerformanceActivity.this, "Employee removed successfully", Toast.LENGTH_SHORT).show();
                            loadProjectAnalytics(activeProjectId);
                        });
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> Toast.makeText(ManagerPerformanceActivity.this, "Removal failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void loadProjectAnalytics(int projectId) {
        taskRepository.syncTasks(projectId, new TaskRepository.DataSyncCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> data) {
                calculateAndDisplayAnalytics(projectId);
            }

            @Override
            public void onFailure(String error) {
                // Fallback to local data on sync failure
                calculateAndDisplayAnalytics(projectId);
            }
        });
    }

    private void calculateAndDisplayAnalytics(int projectId) {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 0. Fetch Project for Roster
            activeProject = db.projectDao().getProjectById(projectId);
            
            // 1. Global Metrics
            int totalTasksCount = db.taskDao().getTaskCountByProject(projectId);
            int completedTasksCount = db.taskDao().getCompletedTaskCountByProject(projectId);
            int activeTasks = totalTasksCount - completedTasksCount;
            
            int globalRate = 0;
            if (totalTasksCount > 0) {
                globalRate = (completedTasksCount * 100) / totalTasksCount;
            }

            // 2. Roster Performance Aggregation
            List<RosterStats> performanceList = new ArrayList<>();
            if (activeProject != null && activeProject.projectRoster != null && !activeProject.projectRoster.isEmpty()) {
                String[] memberNames = activeProject.projectRoster.split(", ");
                List<Task> allProjectTasks = db.taskDao().getTasksByProject(projectId);
                
                for (String username : memberNames) {
                    int userTotal = 0;
                    int userCompleted = 0;
                    
                    for (Task task : allProjectTasks) {
                        if (task.assigneeId != null && task.assigneeId.contains(username)) {
                            userTotal++;
                            if ("DONE".equals(task.status)) {
                                userCompleted++;
                            }
                        }
                    }
                    performanceList.add(new RosterStats(username, userTotal, userCompleted));
                }
            }

            final int finalRate = globalRate;
            final int finalCompleted = completedTasksCount;
            final int finalActive = activeTasks;
            final String rosterText = (activeProject != null) ? activeProject.projectRoster : "";

            runOnUiThread(() -> {
                TextView tvRate = findViewById(R.id.tvGlobalProgress);
                ProgressBar pb = findViewById(R.id.pbGlobalProgress);
                TextView tvSummary = findViewById(R.id.tvTaskSummary);
                TextView tvRoster = findViewById(R.id.tvRosterList);

                tvRate.setText("Global Progress: " + finalRate + "%");
                pb.setProgress(finalRate);
                tvSummary.setText("Total Active: " + finalActive + " | Total Closed: " + finalCompleted);
                tvRoster.setText("Team: " + (rosterText == null || rosterText.isEmpty() ? "No members enrolled" : rosterText));
                
                rosterAdapter.setRosterStatsList(performanceList);
            });
        });
    }

    private void setupRosterEnrollment() {
        EditText inputUsername = findViewById(R.id.inputNewMemberUsername);
        findViewById(R.id.btnAddMemberToProject).setOnClickListener(v -> {
            String username = inputUsername.getText().toString().trim();
            if (username.isEmpty()) {
                inputUsername.setError("Username required");
                return;
            }

            verifyAndAddMember(username);
        });
    }

    private void verifyAndAddMember(String username) {
        // 1. Bypass local Room database User table lookup checks entirely
        com.example.mytasks.ApiService apiService = com.example.mytasks.RetrofitClient.getClient(this).create(com.example.mytasks.ApiService.class);
        com.example.mytasks.UsernameRequest enrollmentBody = new com.example.mytasks.UsernameRequest(username);

        // 2. Query the dedicated server endpoint using the explicit enrollment API signature
        apiService.enrollUser(activeProjectId, enrollmentBody).enqueue(new retrofit2.Callback<com.example.mytasks.Project>() {
            @Override
            public void onResponse(retrofit2.Call<com.example.mytasks.Project> call, retrofit2.Response<com.example.mytasks.Project> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 3. Re-inject the updated Project payload returned from the server back into Room to refresh statistics instantly
                    AppDatabase db = AppDatabase.getInstance(ManagerPerformanceActivity.this);
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        db.projectDao().updateProject(response.body());
                        runOnUiThread(() -> {
                            Toast.makeText(ManagerPerformanceActivity.this, username + " enrolled successfully!", Toast.LENGTH_SHORT).show();
                            ((EditText)findViewById(R.id.inputNewMemberUsername)).setText("");
                            loadProjectAnalytics(activeProjectId);
                        });
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(ManagerPerformanceActivity.this, "Enrollment failed: User not found on server database", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<com.example.mytasks.Project> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(ManagerPerformanceActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
