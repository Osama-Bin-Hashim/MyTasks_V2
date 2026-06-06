package com.example.mytasks.Models;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class AnalyticsResponse {
    @SerializedName("projectId")
    public int projectId;

    @SerializedName("statusDistribution")
    public Map<String, Integer> statusDistribution;

    @SerializedName("priorityBreakdown")
    public Map<String, Integer> priorityBreakdown;

    @SerializedName("employeeWorkload")
    public Map<String, EmployeeStats> employeeWorkload;

    @SerializedName("totalTasks")
    public int totalTasks;

    public static class EmployeeStats {
        @SerializedName("totalTasks")
        public int totalTasks;

        @SerializedName("completedTasks")
        public int completedTasks;
    }
}
