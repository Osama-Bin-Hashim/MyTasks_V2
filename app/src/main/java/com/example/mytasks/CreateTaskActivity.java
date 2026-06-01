package com.example.mytasks;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mytasks.databinding.ActivityCreateTaskBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreateTaskActivity extends AppCompatActivity {
    private ActivityCreateTaskBinding binding;
    private TaskRepository taskRepository;
    private List<User> userList = new ArrayList<>();
    private final String[] priorityOptions = {"1 - Critical", "2 - High", "3 - Medium", "4 - Low"};
    
    private String serializedWorkers = "";
    private boolean[] checkedUsers;
    private long selectedDeadlineMillis = 0L;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault());
    
    private boolean isEditMode = false;
    private int editingTaskId = -1;
    private Task existingTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int projectId = getIntent().getIntExtra("PROJECT_ID", -1);
        isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
        editingTaskId = getIntent().getIntExtra("TASK_ID", -1);

        taskRepository = new TaskRepository(this);

        // SECURITY SHIELD: Enforce Manager-only access
        boolean isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        if (!isManager) {
            Toast.makeText(this, "Access Denied: Managers Only.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupPrioritySpinner();
        loadRosterUsers(projectId);
        setupDeadlinePicker();

        if (isEditMode) {
            binding.tvTitle.setText("Update Task");
            binding.btnSaveTask.setText("Update Task");
            loadExistingTask();
        }

        binding.tvSelectAssignees.setOnClickListener(v -> showUserSelectionDialog());

        binding.btnSaveTask.setOnClickListener(v -> {
            saveTaskToDatabase(projectId);
        });
    }

    private void setupPrioritySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, priorityOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPriority.setAdapter(adapter);
    }

    private void loadRosterUsers(int projectId) {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Project project = db.projectDao().getProjectById(projectId);
            if (project == null) return;

            String roster = project.projectRoster;
            if (roster == null || roster.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "No team members enrolled in this project", Toast.LENGTH_LONG).show());
                return;
            }

            String[] memberNames = roster.split(", ");
            userList = new ArrayList<>();
            for (String name : memberNames) {
                userList.add(new User(name)); // Minimal User object for UI mapping
            }

            checkedUsers = new boolean[userList.size()];
            
            if (isEditMode && existingTask != null) {
                runOnUiThread(this::preCheckUsers);
            }
        });
    }

    private void loadExistingTask() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            existingTask = db.taskDao().getTaskById(editingTaskId);
            if (existingTask != null) {
                runOnUiThread(() -> {
                    binding.etTaskTitle.setText(existingTask.title);
                    binding.etTaskDesc.setText(existingTask.description);
                    binding.spinnerPriority.setSelection(existingTask.priority - 1);
                    
                    selectedDeadlineMillis = existingTask.timeLimitMillis;
                    binding.tvTaskDeadline.setText(dateFormat.format(existingTask.timeLimitMillis));
                    
                    serializedWorkers = existingTask.assigneeId;
                    binding.tvSelectAssignees.setText(serializedWorkers.isEmpty() ? "Select Team Members" : serializedWorkers);
                    
                    if (!userList.isEmpty()) {
                        preCheckUsers();
                    }
                });
            }
        });
    }

    private void preCheckUsers() {
        if (serializedWorkers == null || serializedWorkers.isEmpty()) return;
        String[] assigned = serializedWorkers.split(", ");
        for (int i = 0; i < userList.size(); i++) {
            for (String name : assigned) {
                if (userList.get(i).username.equals(name)) {
                    checkedUsers[i] = true;
                    break;
                }
            }
        }
    }

    private void showUserSelectionDialog() {
        if (userList.isEmpty()) {
            Toast.makeText(this, "No team members to select", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] usernames = new String[userList.size()];
        for (int i = 0; i < userList.size(); i++) {
            usernames[i] = userList.get(i).username;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Team Members");
        builder.setMultiChoiceItems(usernames, checkedUsers, (dialog, which, isChecked) -> {
            checkedUsers[which] = isChecked;
        });

        builder.setPositiveButton("OK", (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < checkedUsers.length; i++) {
                if (checkedUsers[i]) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(usernames[i]);
                }
            }
            serializedWorkers = sb.toString();
            binding.tvSelectAssignees.setText(serializedWorkers.isEmpty() ? "Select Team Members" : serializedWorkers);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setupDeadlinePicker() {
        if (!isEditMode) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            selectedDeadlineMillis = calendar.getTimeInMillis();
            binding.tvTaskDeadline.setText(dateFormat.format(calendar.getTime()));
        }

        binding.tvTaskDeadline.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDeadlineMillis);

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            showTimePicker(calendar);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar calendar) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            selectedDeadlineMillis = calendar.getTimeInMillis();
            binding.tvTaskDeadline.setText(dateFormat.format(calendar.getTime()));
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
    }

    private void saveTaskToDatabase(int projectId) {
        String title = binding.etTaskTitle.getText().toString().trim();
        String desc = binding.etTaskDesc.getText().toString().trim();

        if (title.isEmpty()) {
            binding.etTaskTitle.setError("Title is required");
            return;
        }

        if (projectId == -1 && !isEditMode) {
            Toast.makeText(this, "Invalid Project Context", Toast.LENGTH_SHORT).show();
            return;
        }

        if (serializedWorkers.isEmpty()) {
            Toast.makeText(this, "Please select at least one team member", Toast.LENGTH_SHORT).show();
            return;
        }

        int priority = binding.spinnerPriority.getSelectedItemPosition() + 1;

        Task taskToSave = isEditMode ? existingTask : new Task();
        taskToSave.projectId = isEditMode ? existingTask.projectId : projectId;
        taskToSave.assigneeId = serializedWorkers;
        taskToSave.title = title;
        taskToSave.description = desc;
        taskToSave.status = isEditMode ? existingTask.status : "PENDING";
        taskToSave.priority = priority;
        taskToSave.timeLimitMillis = selectedDeadlineMillis;

        if (isEditMode) {
            taskRepository.updateTask(taskToSave, new TaskRepository.DataSyncCallback<Task>() {
                @Override
                public void onSuccess(Task data) {
                    runOnUiThread(() -> {
                        Toast.makeText(CreateTaskActivity.this, "Task Updated Successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, "Update failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            taskToSave.isRead = false;
            taskRepository.saveTask(taskToSave, new TaskRepository.DataSyncCallback<Task>() {
                @Override
                public void onSuccess(Task data) {
                    runOnUiThread(() -> {
                        NotificationHelper.showNotification(CreateTaskActivity.this, "New Task Assigned!", "You have been assigned: " + data.title);
                        Toast.makeText(CreateTaskActivity.this, "Task Created Successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> Toast.makeText(CreateTaskActivity.this, "Creation failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }
}
