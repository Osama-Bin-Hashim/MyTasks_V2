package com.example.mytasks;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface RequestDao {
    @Insert
    void insertRequest(Request request);

    @Update
    void updateRequest(Request request);

    @Query("SELECT * FROM requests WHERE receiverId = :userId OR senderId = :userId ORDER BY timestamp DESC")
    List<Request> getRequestsForUser(int userId);

    @Query("SELECT * FROM requests WHERE projectId = :projectId ORDER BY timestamp DESC")
    List<Request> getRequestsByProject(int projectId);

    @Query("SELECT * FROM requests WHERE requestId = :requestId")
    Request getRequestById(int requestId);

    @Query("SELECT COUNT(*) FROM requests WHERE projectId = :projectId AND receiverId = :userId AND isRead = 0")
    int getUnreadCountForEmployee(int projectId, int userId);

    @Query("SELECT COUNT(*) FROM requests WHERE projectId = :projectId AND senderId != :userId AND isRead = 0")
    int getUnreadCountForManager(int projectId, int userId);

    @androidx.room.Delete
    void deleteRequest(Request request);

    @Query("DELETE FROM requests WHERE projectId = :projectId")
    void deleteRequestsByProject(int projectId);
}
