package com.example.mytasks;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {

    /**
     * Requirement: Use REPLACE to prevent synchronization collisions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTask(Task task);

    @Update
    void updateTask(Task task);

    @androidx.room.Delete
    void deleteTask(Task task);

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    Task getTaskById(int taskId);

    @Query("SELECT * FROM tasks WHERE projectId = :projectId")
    List<Task> getTasksByProject(int projectId);

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY priority ASC")
    List<Task> getTasksByProjectSorted(int projectId);

    @Query("SELECT * FROM tasks WHERE assigneeId LIKE '%' || :employeeName || '%'")
    List<Task> getTasksByAssignee(String employeeName);

    @Query("SELECT * FROM tasks WHERE status = :status")
    List<Task> getTasksByStatus(String status);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projId")
    int getTaskCountByProject(int projId);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projId AND status = 'DONE'")
    int getCompletedTaskCountByProject(int projId);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projId AND assigneeId LIKE '%' || :username || '%'")
    int getEmployeeTaskCount(int projId, String username);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projId AND assigneeId LIKE '%' || :username || '%' AND status = 'DONE'")
    int getEmployeeCompletedTaskCount(int projId, String username);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND assigneeId LIKE '%' || :username || '%' AND isRead = 0")
    int getUnreadTaskCount(int projectId, String username);

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND status = 'DONE' AND timeTakenMillis > :lastViewTimestamp")
    int getRecentlyCompletedCount(int projectId, long lastViewTimestamp);

    @Query("UPDATE tasks SET isRead = 1 WHERE projectId = :projectId AND assigneeId LIKE '%' || :username || '%'")
    void markTasksAsRead(int projectId, String username);

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    void deleteTasksByProject(int projectId);

    @Query("DELETE FROM tasks")
    void deleteAllTasks();
}
