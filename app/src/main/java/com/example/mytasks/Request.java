package com.example.mytasks;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Data model for user requests and internal messages.
 */
@Entity(tableName = "requests")
public class Request {
    @PrimaryKey
    public int requestId;

    public int senderId;
    public String senderName;
    public int receiverId; // Can be Manager ID or Peer ID
    public int projectId;
    public String projectName;
    public String messageText;
    public String type; // "JOIN_PROJECT", "DIRECT_TO_MANAGER", "PEER_TO_PEER"
    public String status; // "PENDING", "APPROVED", "REJECTED", "MESSAGE"
    public long timestamp;
    public boolean isRead = false; // New field for notifications

    public Request() {}

    public Request(int senderId, String senderName, int receiverId, int projectId, String projectName, String messageText, String type, String status, long timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.receiverId = receiverId;
        this.projectId = projectId;
        this.projectName = projectName;
        this.messageText = messageText;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
    }
}
