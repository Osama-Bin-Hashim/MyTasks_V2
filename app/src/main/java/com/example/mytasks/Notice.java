package com.example.mytasks;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity for project-wide broadcasting and announcements.
 */
@Entity(tableName = "notices")
public class Notice {
    @PrimaryKey
    public int id;

    public String title;
    public String content;
    public long timestamp;
    public int projectId; // References Project.id

    public Notice() {}

    public Notice(String title, String content, long timestamp, int projectId) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.projectId = projectId;
    }
}
