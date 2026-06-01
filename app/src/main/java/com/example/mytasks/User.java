package com.example.mytasks;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

/**
 * User entity focused on identity.
 * Roles are determined dynamically by Project/Task relationships.
 */
@Entity(tableName = "users",
        indices = {@Index(value = {"username"}, unique = true)})
public class User {
    @PrimaryKey
    @SerializedName("id")
    public int id;

    public String username;
    public String email;
    public String password;
    public String role;

    public User() {}

    public User(String username) {
        this.username = username;
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User(String username, String role) {
        this.username = username;
        this.role = role;
    }
}
