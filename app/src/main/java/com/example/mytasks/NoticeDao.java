package com.example.mytasks;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NoticeDao {
    @Insert
    void insertNotice(Notice notice);

    @androidx.room.Delete
    void deleteNotice(Notice notice);

    @Query("SELECT * FROM notices WHERE projectId = :projId ORDER BY timestamp DESC")
    List<Notice> getNoticesByProject(int projId);

    @Query("SELECT COUNT(*) FROM notices WHERE projectId = :projectId AND timestamp > :lastViewTimestamp")
    int getNewNoticeCount(int projectId, long lastViewTimestamp);

    @Query("DELETE FROM notices WHERE projectId = :projectId")
    void deleteNoticesByProject(int projectId);
}
