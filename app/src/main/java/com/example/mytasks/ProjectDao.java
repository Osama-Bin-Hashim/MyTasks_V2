package com.example.mytasks;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertProject(Project project);

    @androidx.room.Update
    void updateProject(Project project);

    @Query("SELECT * FROM projects")
    List<Project> getAllProjects();

    @Query("SELECT * FROM projects WHERE id = :projectId")
    Project getProjectById(int projectId);

    @Query("DELETE FROM projects")
    void deleteAllProjects();
}
