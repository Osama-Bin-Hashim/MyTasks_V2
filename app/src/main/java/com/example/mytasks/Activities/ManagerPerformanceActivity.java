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
import com.example.mytasks.Models.AnalyticsResponse;
import com.example.mytasks.Models.RosterStats;
import com.example.mytasks.Project;
import com.example.mytasks.ProjectRepository;
import com.example.mytasks.R;
import com.example.mytasks.RetrofitClient;
import com.example.mytasks.Task;
import com.example.mytasks.TaskRepository;
import com.example.mytasks.UsernameRequest;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ManagerPerformanceActivity extends AppCompatActivity implements RosterAdapter.OnRemoveMemberListener {
    
    private int activeProjectId;
    private Project activeProject;
    private RosterAdapter rosterAdapter;
    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private boolean isManager;
    private PieChart pieChartStatus, priorityChart;
    private BarChart barChartWorkload;

    private final int[] lightBluePalette = new int[]{
            android.graphics.Color.parseColor("#81D4FA"), // Light Blue 200
            android.graphics.Color.parseColor("#29B6F6"), // Light Blue 400
            android.graphics.Color.parseColor("#03A9F4"), // Light Blue 500
            android.graphics.Color.parseColor("#0288D1"), // Light Blue 700
            android.graphics.Color.parseColor("#01579B")  // Light Blue 900
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
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

        pieChartStatus = findViewById(R.id.pieChartStatus);
        priorityChart = findViewById(R.id.priorityChart);
        barChartWorkload = findViewById(R.id.barChartWorkload);

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
        com.example.mytasks.ApiService apiService = RetrofitClient.getClient(this).create(com.example.mytasks.ApiService.class);
        apiService.getProjectAnalytics(projectId).enqueue(new Callback<AnalyticsResponse>() {
            @Override
            public void onResponse(Call<AnalyticsResponse> call, Response<AnalyticsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AnalyticsResponse analytics = response.body();
                    renderCharts(analytics);
                    updateGlobalProgressUI(analytics);
                }
                
                // ALWAYS fetch local Project data to update Roster List (Sync check)
                AppDatabase db = AppDatabase.getInstance(ManagerPerformanceActivity.this);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    activeProject = db.projectDao().getProjectById(projectId);
                    calculateAndDisplayAnalytics(projectId); // Still runs to populate Roster List
                });
            }

            @Override
            public void onFailure(Call<AnalyticsResponse> call, Throwable t) {
                calculateAndDisplayAnalytics(projectId);
            }
        });
    }

    private void updateGlobalProgressUI(AnalyticsResponse analytics) {
        runOnUiThread(() -> {
            int total = analytics.totalTasks;
            int done = 0;
            if (analytics.statusDistribution != null && analytics.statusDistribution.get("DONE") != null) {
                done = analytics.statusDistribution.get("DONE");
            }
            
            int progress = (total > 0) ? (done * 100) / total : 0;
            
            TextView tvRate = findViewById(R.id.tvGlobalProgress);
            ProgressBar pb = findViewById(R.id.pbGlobalProgress);
            
            tvRate.setText(progress + "%");
            pb.setProgress(progress);
        });
    }

    private void renderCharts(AnalyticsResponse analytics) {
        runOnUiThread(() -> {
            // 1. PieChart: Status Distribution
            List<PieEntry> statusEntries = new ArrayList<>();
            if (analytics.statusDistribution != null) {
                for (Map.Entry<String, Integer> entry : analytics.statusDistribution.entrySet()) {
                    statusEntries.add(new PieEntry(entry.getValue(), entry.getKey()));
                }
            }

            PieDataSet statusDataSet = new PieDataSet(statusEntries, "");
            statusDataSet.setColors(lightBluePalette);
            statusDataSet.setValueTextSize(12f);
            statusDataSet.setValueTextColor(android.graphics.Color.WHITE);

            PieData statusData = new PieData(statusDataSet);
            pieChartStatus.setData(statusData);
            pieChartStatus.getDescription().setEnabled(false);
            pieChartStatus.getLegend().setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE);
            pieChartStatus.setHoleColor(android.graphics.Color.TRANSPARENT);
            pieChartStatus.setCenterText("Status");
            pieChartStatus.animateY(1000, com.github.mikephil.charting.animation.Easing.EaseInOutCubic);
            pieChartStatus.invalidate();

            // 2. PieChart: Priority Breakdown
            List<PieEntry> priorityEntries = new ArrayList<>();
            if (analytics.priorityBreakdown != null) {
                for (Map.Entry<String, Integer> entry : analytics.priorityBreakdown.entrySet()) {
                    String label = "P" + entry.getKey();
                    priorityEntries.add(new PieEntry(entry.getValue(), label));
                }
            }

            PieDataSet priorityDataSet = new PieDataSet(priorityEntries, "");
            priorityDataSet.setColors(lightBluePalette);
            priorityDataSet.setValueTextSize(12f);
            priorityDataSet.setValueTextColor(android.graphics.Color.WHITE);

            PieData priorityData = new PieData(priorityDataSet);
            priorityChart.setData(priorityData);
            priorityChart.getDescription().setEnabled(false);
            priorityChart.getLegend().setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE);
            priorityChart.setHoleColor(android.graphics.Color.TRANSPARENT);
            priorityChart.setCenterText("Priority");
            priorityChart.animateY(1000, com.github.mikephil.charting.animation.Easing.EaseInOutCubic);
            priorityChart.invalidate();

            // 3. BarChart: Employee Workload
            List<BarEntry> workloadEntries = new ArrayList<>();
            final List<String> labels = new ArrayList<>();
            int index = 0;

            if (analytics.employeeWorkload != null) {
                for (Map.Entry<String, AnalyticsResponse.EmployeeStats> entry : analytics.employeeWorkload.entrySet()) {
                    workloadEntries.add(new BarEntry(index, entry.getValue().totalTasks));
                    labels.add(entry.getKey());
                    index++;
                }
            }

            BarDataSet workloadDataSet = new BarDataSet(workloadEntries, "Tasks Assigned");
            workloadDataSet.setColors(lightBluePalette);
            workloadDataSet.setValueTextSize(10f);
            workloadDataSet.setValueTextColor(android.graphics.Color.DKGRAY);

            BarData workloadData = new BarData(workloadDataSet);
            barChartWorkload.setData(workloadData);
            barChartWorkload.getDescription().setEnabled(false);
            barChartWorkload.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels));
            barChartWorkload.getXAxis().setGranularity(1f);
            barChartWorkload.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            barChartWorkload.animateY(1000, com.github.mikephil.charting.animation.Easing.EaseInOutCubic);
            barChartWorkload.invalidate();
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
