package com.example.mytasks;

import com.google.gson.annotations.SerializedName;

public class UsernameRequest {
    @SerializedName("username")
    private String username;

    public UsernameRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
