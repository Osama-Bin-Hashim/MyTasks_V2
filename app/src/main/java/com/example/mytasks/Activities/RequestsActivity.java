package com.example.mytasks.Activities;

import android.content.SharedPreferences;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.mytasks.Adapters.RequestsAdapter;
import com.example.mytasks.ApiService;
import com.example.mytasks.AppDatabase;
import com.example.mytasks.NotificationHelper;
import com.example.mytasks.Project;
import com.example.mytasks.Request;
import com.example.mytasks.RequestRepository;
import com.example.mytasks.RetrofitClient;
import com.example.mytasks.User;
import com.example.mytasks.databinding.ActivityRequestsBinding;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

public class RequestsActivity extends AppCompatActivity implements RequestsAdapter.OnRequestActionListener {

    private ActivityRequestsBinding binding;
    private RequestsAdapter adapter;
    private RequestRepository requestRepository;
    private int currentUserId;
    private String currentUsername;
    private boolean isManager;
    private int projectId;
    private Project currentProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // IDENTITY PERSISTENCE: Read session ID with defensive fallbacks
        SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        currentUserId = pref.getInt("LOGGED_IN_USER_ID", -1);
        if (currentUserId == -1 || currentUserId == 0) currentUserId = pref.getInt("userId", -1);
        if (currentUserId == -1 || currentUserId == 0) currentUserId = pref.getInt("logged_in_user_id", -1);
        
        currentUsername = pref.getString("LOGGED_IN_USERNAME", "Unknown");
        
        isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        projectId = getIntent().getIntExtra("PROJECT_ID", -1);

        requestRepository = new RequestRepository(this);

        loadProjectAndRequests();

        // UI VISIBILITY FIX: FAB must be visible for everyone, including Managers
        binding.fabSendRequest.setVisibility(android.view.View.VISIBLE);
        binding.fabSendRequest.setOnClickListener(v -> showSendMessageDialog());
    }

    private void setupRecyclerView() {
        // SAFE INITIALIZATION: Use fallback managerId if currentProject is null
        int targetManagerId = (currentProject != null) ? currentProject.managerId : -1;
        
        adapter = new RequestsAdapter(isManager, currentUserId, targetManagerId, this);
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);

        if (currentProject == null) {
            Log.e("REQ_ERROR", "setupRecyclerView: currentProject is null! Using fallback empty states.");
            binding.tvEmptyRequests.setVisibility(View.VISIBLE);
            binding.rvRequests.setVisibility(View.GONE);
        }
    }

    private void loadProjectAndRequests() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currentProject = db.projectDao().getProjectById(projectId);
            runOnUiThread(() -> {
                if (currentProject != null) {
                    setupRecyclerView();
                    loadRequests();
                } else {
                    Log.e("REQ_ERROR", "Project data is null! Workspace ID: " + projectId);
                    Toast.makeText(this, "Failed to load project context", Toast.LENGTH_SHORT).show();
                    binding.tvEmptyRequests.setVisibility(View.VISIBLE);
                    binding.tvEmptyRequests.setText("Error: Project not found");
                }
            });
        });
    }

    private void loadRequests() {
        if (currentProject == null) return;
        
        ApiService apiService = RetrofitClient.getClient(this).create(ApiService.class);
        SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        
        int userId = pref.getInt("LOGGED_IN_USER_ID", -1);
        if (userId == -1 || userId == 0) userId = pref.getInt("userId", -1);
        if (userId == -1 || userId == 0) userId = pref.getInt("logged_in_user_id", -1);

        if (userId <= 0) {
            fetchLocalRequests();
            return;
        }

        apiService.getRequests(projectId, userId).enqueue(new Callback<List<Request>>() {
            @Override
            public void onResponse(Call<List<Request>> call, Response<List<Request>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    binding.tvConnWarning.setVisibility(View.GONE);
                    
                    // UI UPDATE FIRST: Ensure items are visible before sync
                    displayFilteredRequests(response.body());
                    
                    // Atomic Background Sync to Room
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase db = AppDatabase.getInstance(RequestsActivity.this);
                        db.requestDao().syncProjectRequests(projectId, response.body());
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.tvConnWarning.setVisibility(View.VISIBLE);
                        fetchLocalRequests();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<Request>> call, Throwable t) {
                runOnUiThread(() -> {
                    binding.tvConnWarning.setVisibility(View.VISIBLE);
                    fetchLocalRequests();
                });
            }
        });
    }

    private void fetchLocalRequests() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Request> local = db.requestDao().getRequestsByProject(projectId);
            runOnUiThread(() -> displayFilteredRequests(local));
        });
    }

    private void displayFilteredRequests(List<Request> list) {
        if (list == null) return;
        
        List<Request> filteredRequests = new ArrayList<>();
        
        // Fallback safely to our intent value or SharedPreferences role if currentProject is loading slow
        boolean checkIsManager = isManager; 

        for (Request req : list) {
            if (req == null) continue;
            if (req.type == null) req.type = "MESSAGE";

            // REWRITTEN FILTERING LOGIC FOR ROBUSTNESS
            if ("DIRECT_TO_MANAGER".equals(req.type) || "JOIN_PROJECT".equals(req.type)) {
                // If I am the sender, or if I am the manager of this workspace view, or if I am the receiver, show it
                if (currentUserId == req.senderId || checkIsManager || currentUserId == req.receiverId) {
                    filteredRequests.add(req);
                }
            } else if ("PEER_TO_PEER".equals(req.type) || "MANAGER_TO_EMPLOYEE".equals(req.type)) {
                // Show if I'm involved in the communication chain or if I'm the manager
                if (currentUserId == req.senderId || currentUserId == req.receiverId || checkIsManager) {
                    filteredRequests.add(req);
                }
            } else {
                // Default visibility fallback for other message types (e.g. system messages)
                filteredRequests.add(req);
            }
        }

        runOnUiThread(() -> {
            if (adapter != null) {
                if (filteredRequests.isEmpty()) {
                    binding.tvEmptyRequests.setVisibility(View.VISIBLE);
                    binding.rvRequests.setVisibility(View.GONE);
                } else {
                    binding.tvEmptyRequests.setVisibility(View.GONE);
                    binding.rvRequests.setVisibility(View.VISIBLE);
                    adapter.setRequestList(filteredRequests);
                }
            }
        });
    }

    private void showSendMessageDialog() {
        if (currentProject == null) {
            Toast.makeText(this, "Project data not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Request / Message");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Recipient Spinner - Corrected for Manager access
        Spinner spinner = new Spinner(this);
        List<String> roster = new ArrayList<>();
        
        if (!isManager) {
            roster.add("Project Manager");
        }
        
        if (currentProject.projectRoster != null && !currentProject.projectRoster.isEmpty()) {
            String[] members = currentProject.projectRoster.split(", ");
            for (String member : members) {
                roster.add(member); 
            }
        }
        
        Log.d("REQ_DEBUG", "Number of employees loaded for spinner: " + roster.size());

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roster);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        layout.addView(spinner);

        // Message EditText
        EditText input = new EditText(this);
        input.setHint("Type your message...");
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String message = input.getText().toString().trim();
            String recipient = spinner.getSelectedItem().toString();
            if (message.isEmpty()) {
                Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            sendRequest(recipient, message);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendRequest(String recipient, String message) {
        Log.d("REQ_DEBUG", "Send clicked. Recipient: " + recipient + ", Manager Mode: " + isManager);
        
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int receiverId;
            String type;
            
            if ("Project Manager".equals(recipient)) {
                receiverId = currentProject.managerId;
                type = "DIRECT_TO_MANAGER";
            } else {
                User user = db.userDao().getUserByUsername(recipient);
                receiverId = (user != null) ? user.id : -1;
                type = isManager ? "MANAGER_TO_EMPLOYEE" : "PEER_TO_PEER";
            }

            Request newRequest = new Request(
                currentUserId,
                currentUsername,
                receiverId,
                projectId,
                currentProject.name,
                message,
                type,
                "PENDING",
                System.currentTimeMillis()
            );

            requestRepository.sendRequest(newRequest, new RequestRepository.DataSyncCallback<Request>() {
                @Override
                public void onSuccess(Request data) {
                    runOnUiThread(() -> {
                        Toast.makeText(RequestsActivity.this, "Message Sent Successfully", Toast.LENGTH_SHORT).show();
                        NotificationHelper.showNotification(RequestsActivity.this, "Message Sent!", "To: " + recipient);
                        loadRequests();
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(RequestsActivity.this, "Sync Failed: " + error + ". Message saved locally.", Toast.LENGTH_LONG).show();
                        loadRequests();
                    });
                }
            });
        });
    }

    @Override
    public void onApprove(Request request) {
        // SECURITY_AUDIT
        Log.d("SECURITY_AUDIT", "Request APPROVED. ID: " + request.requestId + " by User ID: " + currentUserId);

        request.status = "APPROVED";
        requestRepository.updateRequest(request, new RequestRepository.DataSyncCallback<Request>() {
            @Override
            public void onSuccess(Request updatedRequest) {
                // Trigger Backend Action (e.g. Join Project)
                if ("JOIN_PROJECT".equals(updatedRequest.type)) {
                    processJoinProject(updatedRequest);
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(RequestsActivity.this, "Request Approved", Toast.LENGTH_SHORT).show();
                        loadRequests();
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(RequestsActivity.this, "Failed to sync approval: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void processJoinProject(Request request) {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Project project = db.projectDao().getProjectById(request.projectId);
            if (project != null) {
                String roster = project.projectRoster;
                if (roster == null) roster = "";
                if (!roster.contains(request.senderName)) {
                    if (!roster.isEmpty()) roster += ", ";
                    roster += request.senderName;
                    project.projectRoster = roster;
                    // Ideally you'd have a ProjectRepository.updateProject too
                    db.projectDao().updateProject(project);
                }
            }
            runOnUiThread(() -> {
                Toast.makeText(RequestsActivity.this, "Request Approved & User added to Roster", Toast.LENGTH_SHORT).show();
                loadRequests();
            });
        });
    }

    @Override
    public void onReject(Request request) {
        // SECURITY_AUDIT
        Log.d("SECURITY_AUDIT", "Request REJECTED. ID: " + request.requestId + " by User ID: " + currentUserId);

        request.status = "REJECTED";
        requestRepository.updateRequest(request, new RequestRepository.DataSyncCallback<Request>() {
            @Override
            public void onSuccess(Request data) {
                runOnUiThread(() -> {
                    Toast.makeText(RequestsActivity.this, "Request Rejected", Toast.LENGTH_SHORT).show();
                    loadRequests();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(RequestsActivity.this, "Failed to sync rejection: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onDelete(Request request) {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.requestDao().deleteRequest(request);
            runOnUiThread(() -> {
                Toast.makeText(this, "Message Deleted", Toast.LENGTH_SHORT).show();
                loadRequests();
            });
        });
    }
}
