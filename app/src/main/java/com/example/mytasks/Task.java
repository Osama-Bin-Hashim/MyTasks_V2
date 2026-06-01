package com.example.mytasks;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Detailed Task entity including metric tracking and project mapping.
 */
@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey
    public int id;

    public int projectId;   // References Project.id
    public String assigneeId;  // Stores comma-separated Team Member names

    public String title;
    public String description;

    // 1 = Critical, 2 = High, 3 = Medium, 4 = Low
    public int priority;
    public String status;   // "PENDING", "IN_PROGRESS", "DONE"

    public long timeLimitMillis;
    public long timeTakenMillis;
    public boolean canShareNotes;
    public boolean isRead = true; // New field for notifications

    public Task() {}
}
