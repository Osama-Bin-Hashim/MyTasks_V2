package com.example.mytasks;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Defines the Project scope.
 * managerId maps to the User.id of the creator/supervisor.
 */
@Entity(tableName = "projects")
public class Project {
    @PrimaryKey
    public int id;

    public String name;
    public int managerId; // References User.id
    public String projectRoster = ""; // Comma-separated usernames

    public Project() {}

    public Project(String name, int managerId) {
        this.name = name;
        this.managerId = managerId;
    }
}
