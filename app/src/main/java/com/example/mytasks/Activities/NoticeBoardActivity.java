package com.example.mytasks.Activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mytasks.Adapters.NoticeAdapter;
import com.example.mytasks.AppDatabase;
import com.example.mytasks.Notice;
import com.example.mytasks.NoticeRepository;
import com.example.mytasks.NotificationHelper;
import com.example.mytasks.R;

import java.util.List;

public class NoticeBoardActivity extends AppCompatActivity {

    private int projectId;
    private boolean isManager;
    private NoticeAdapter noticeAdapter;
    private NoticeRepository noticeRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice_board);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rvNotices).getRootView(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        projectId = getIntent().getIntExtra("PROJECT_ID", -1);
        isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        noticeRepository = new NoticeRepository(this);

        updateNoticeViewTimestamp();
        setupUI();
        setupRecyclerView();
        loadNotices();
    }

    private void updateNoticeViewTimestamp() {
        android.content.SharedPreferences pref = getSharedPreferences("UserSession", MODE_PRIVATE);
        // I need the currentUserId here. It should be in UserSession.
        int userId = pref.getInt("LOGGED_IN_USER_ID", -1);
        if (userId != -1) {
            pref.edit().putLong("LAST_NOTICE_VIEW_" + userId + "_" + projectId, System.currentTimeMillis()).apply();
        }
    }

    private void setupUI() {
        View managerCard = findViewById(R.id.managerNoticeCard);
        if (isManager) {
            managerCard.setVisibility(View.VISIBLE);
            findViewById(R.id.btnPostNotice).setOnClickListener(v -> postNotice());
        } else {
            managerCard.setVisibility(View.GONE);
        }
    }

    private void setupRecyclerView() {
        RecyclerView rv = findViewById(R.id.rvNotices);
        noticeAdapter = new NoticeAdapter(isManager);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(noticeAdapter);
    }

    private void loadNotices() {
        noticeRepository.syncNotices(projectId, new NoticeRepository.DataSyncCallback<List<Notice>>() {
            @Override
            public void onSuccess(List<Notice> notices) {
                runOnUiThread(() -> noticeAdapter.setNoticeList(notices));
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(NoticeBoardActivity.this, "Sync Error: " + error + ". Showing cached notices.", Toast.LENGTH_SHORT).show();
                    fetchLocalNotices();
                });
            }
        });
    }

    private void fetchLocalNotices() {
        AppDatabase db = AppDatabase.getInstance(this);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Notice> local = db.noticeDao().getNoticesByProject(projectId);
            runOnUiThread(() -> noticeAdapter.setNoticeList(local));
        });
    }

    private void postNotice() {
        EditText etTitle = findViewById(R.id.etNoticeTitle);
        EditText etContent = findViewById(R.id.etNoticeContent);

        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Title and Content are required", Toast.LENGTH_SHORT).show();
            return;
        }

        Notice notice = new Notice(title, content, System.currentTimeMillis(), projectId);
        noticeRepository.createNotice(notice, new NoticeRepository.DataSyncCallback<Notice>() {
            @Override
            public void onSuccess(Notice data) {
                runOnUiThread(() -> {
                    Toast.makeText(NoticeBoardActivity.this, "Notice broadcasted successfully!", Toast.LENGTH_SHORT).show();
                    NotificationHelper.showNotification(NoticeBoardActivity.this, "New Notice Broadcasted!", title);
                    etTitle.setText("");
                    etContent.setText("");
                    loadNotices();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(NoticeBoardActivity.this, "Failed to sync notice: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
