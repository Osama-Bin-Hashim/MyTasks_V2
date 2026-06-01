package com.example.mytasks;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for Login Request payload
 */
public class LoginRequest {
    @SerializedName("username")
    private String username;

    @SerializedName("password")
    private String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
