package com.example.mytasks.Adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mytasks.AppDatabase;
import com.example.mytasks.CreateTaskActivity;
import com.example.mytasks.Project;
import com.example.mytasks.R;
import com.example.mytasks.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private boolean isManager;
    private String currentUsername;
    private OnTaskActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault());

    public interface OnTaskActionListener {
        void onStartTask(Task task);
        void onMarkDone(Task task);
    }

    public TaskAdapter(boolean isManager, String currentUsername, OnTaskActionListener listener) {
        this.isManager = isManager;
        this.currentUsername = currentUsername;
        this.listener = listener;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_card, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        
        // TASK FILTERING: Employees see their tasks only
        if (!isManager) {
            if (task.assigneeId == null || !task.assigneeId.contains(currentUsername)) {
                holder.itemView.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                return;
            } else {
                holder.itemView.setVisibility(View.VISIBLE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        holder.bind(task, isManager, listener, dateFormat, position, this);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView title, priority, status, deadline, description, team;
        Button btnMarkDone, btnStartTask;
        ImageButton btnEditTask, btnDeleteTask;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.taskTitle);
            priority = itemView.findViewById(R.id.priorityTag);
            status = itemView.findViewById(R.id.taskStatus);
            deadline = itemView.findViewById(R.id.tvDeadlineDisplay);
            description = itemView.findViewById(R.id.tvTaskDescriptionDisplay);
            team = itemView.findViewById(R.id.tvAssignedTeamDisplay);
            btnMarkDone = itemView.findViewById(R.id.btnMarkDone);
            btnStartTask = itemView.findViewById(R.id.btnStartTask);
            btnEditTask = itemView.findViewById(R.id.btnEditTask);
            btnDeleteTask = itemView.findViewById(R.id.btnDeleteTask);
        }

        public void bind(Task task, boolean isManager, OnTaskActionListener listener,
                        SimpleDateFormat dateFormat, int position, TaskAdapter adapter) {
            Context context = itemView.getContext();
            title.setText(task.title);
            status.setText("Status: " + task.status);
            
            // Description Display
            if (task.description == null || task.description.trim().isEmpty()) {
                description.setVisibility(View.GONE);
            } else {
                description.setVisibility(View.VISIBLE);
                description.setText(task.description);
            }

            // Team Display
            if (task.assigneeId == null || task.assigneeId.isEmpty()) {
                team.setText("Team: N/A");
            } else {
                team.setText("Team: " + task.assigneeId);
            }
            
            String formattedDeadline = dateFormat.format(new Date(task.timeLimitMillis));
            deadline.setText("Deadline: " + formattedDeadline);
            
            String priorityText;
            int priorityColor;
            switch (task.priority) {
                case 1: priorityText = "CRITICAL"; priorityColor = 0xFFFF4444; break;
                case 2: priorityText = "HIGH"; priorityColor = 0xFFFFBB33; break;
                case 3: priorityText = "MEDIUM"; priorityColor = 0xFF0099CC; break;
                default: priorityText = "LOW"; priorityColor = 0xFF99CC00; break;
            }
            priority.setText(priorityText);
            priority.setBackgroundColor(priorityColor);

            // Visibility for Action Buttons (Employee Workflow)
            if (isManager || "DONE".equals(task.status)) {
                btnMarkDone.setVisibility(View.GONE);
                btnStartTask.setVisibility(View.GONE);
            } else {
                // Step 1 & 3: UI Addition/Shift
                if ("PENDING".equals(task.status)) {
                    btnStartTask.setVisibility(View.VISIBLE);
                    btnMarkDone.setVisibility(View.GONE);
                } else if ("IN_PROGRESS".equals(task.status)) {
                    btnStartTask.setVisibility(View.GONE);
                    btnMarkDone.setVisibility(View.VISIBLE);
                } else {
                    btnStartTask.setVisibility(View.GONE);
                    btnMarkDone.setVisibility(View.VISIBLE);
                }

                btnStartTask.setOnClickListener(v -> {
                    // CLICK-TIME VERIFICATION GATE
                    android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                    String sessionUser = pref.getString("LOGGED_IN_USERNAME", "");
                    
                    if (task.assigneeId == null || !task.assigneeId.contains(sessionUser)) {
                        android.widget.Toast.makeText(context, "Security Alert: You are not assigned to this task!", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    listener.onStartTask(task);
                });

                btnMarkDone.setOnClickListener(v -> {
                    // CLICK-TIME VERIFICATION GATE
                    android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                    String sessionUser = pref.getString("LOGGED_IN_USERNAME", "");
                    
                    if (task.assigneeId == null || !task.assigneeId.contains(sessionUser)) {
                        android.widget.Toast.makeText(context, "Security Alert: You are not assigned to this task!", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    listener.onMarkDone(task);
                });
            }

            // Management Controls
            if (isManager) {
                btnEditTask.setVisibility(View.VISIBLE);
                btnDeleteTask.setVisibility(View.VISIBLE);

                btnDeleteTask.setOnClickListener(v -> {
                    // CLICK-TIME VERIFICATION GATE
                    android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                    int sessionUserId = pref.getInt("LOGGED_IN_USER_ID", -1);
                    
                    AppDatabase dbCheck = AppDatabase.getInstance(context);
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        Project project = dbCheck.projectDao().getProjectById(task.projectId);
                        if (project == null || project.managerId != sessionUserId) {
                            ((android.app.Activity)context).runOnUiThread(() -> 
                                android.widget.Toast.makeText(context, "Security Alert: Unauthorized Deletion Attempt!", android.widget.Toast.LENGTH_SHORT).show());
                            return;
                        }

                        ((android.app.Activity)context).runOnUiThread(() -> {
                            new AlertDialog.Builder(context)
                                .setTitle("Delete Task")
                                .setMessage("Are you sure you want to permanently delete this task?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    AppDatabase db = AppDatabase.getInstance(context);
                                    AppDatabase.databaseWriteExecutor.execute(() -> {
                                        db.taskDao().deleteTask(task);
                                        ((android.app.Activity)context).runOnUiThread(() -> {
                                            adapter.tasks.remove(position);
                                            adapter.notifyItemRemoved(position);
                                            adapter.notifyItemRangeChanged(position, adapter.tasks.size());
                                        });
                                    });
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    });
                });

                btnEditTask.setOnClickListener(v -> {
                    // CLICK-TIME VERIFICATION GATE
                    android.content.SharedPreferences pref = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                    int sessionUserId = pref.getInt("LOGGED_IN_USER_ID", -1);

                    AppDatabase dbCheck = AppDatabase.getInstance(context);
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        Project project = dbCheck.projectDao().getProjectById(task.projectId);
                        if (project == null || project.managerId != sessionUserId) {
                            ((android.app.Activity)context).runOnUiThread(() -> 
                                android.widget.Toast.makeText(context, "Security Alert: Unauthorized Edit Attempt!", android.widget.Toast.LENGTH_SHORT).show());
                            return;
                        }

                        ((android.app.Activity)context).runOnUiThread(() -> {
                            Intent intent = new Intent(context, CreateTaskActivity.class);
                            intent.putExtra("IS_EDIT_MODE", true);
                            intent.putExtra("TASK_ID", task.id);
                            intent.putExtra("PROJECT_ID", task.projectId);
                            intent.putExtra("IS_MANAGER", true); 
                            context.startActivity(intent);
                        });
                    });
                });
            } else {
                btnEditTask.setVisibility(View.GONE);
                btnDeleteTask.setVisibility(View.GONE);
            }
        }
    }
}
