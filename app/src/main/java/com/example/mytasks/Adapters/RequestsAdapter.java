package com.example.mytasks.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mytasks.R;
import com.example.mytasks.Request;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.RequestViewHolder> {

    private List<Request> requestList = new ArrayList<>();
    private final OnRequestActionListener listener;
    private final boolean isManager;
    private final int currentUserId;
    private final int managerId;

    public interface OnRequestActionListener {
        void onApprove(Request request);
        void onReject(Request request);
        void onDelete(Request request);
    }

    public RequestsAdapter(boolean isManager, int currentUserId, int managerId, OnRequestActionListener listener) {
        this.isManager = isManager;
        this.currentUserId = currentUserId;
        this.managerId = managerId;
        this.listener = listener;
    }

    public void setRequestList(List<Request> requestList) {
        this.requestList = requestList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.request_item, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        Request request = requestList.get(position);
        holder.bind(request, isManager, currentUserId, managerId, listener);
    }

    @Override
    public int getItemCount() {
        return requestList.size();
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvStatus, tvSender, tvProject, tvTime, tvMessage;
        View layoutActions, btnApprove, btnReject, btnDelete;
        com.google.android.material.card.MaterialCardView cardView;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardRequest);
            tvType = itemView.findViewById(R.id.tvRequestType);
            tvStatus = itemView.findViewById(R.id.tvRequestStatus);
            tvSender = itemView.findViewById(R.id.tvRequestSender);
            tvProject = itemView.findViewById(R.id.tvRequestProject);
            tvMessage = itemView.findViewById(R.id.tvRequestMessage);
            tvTime = itemView.findViewById(R.id.tvRequestTime);
            layoutActions = itemView.findViewById(R.id.layoutActionButtons);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnDelete = itemView.findViewById(R.id.btnDeleteRequest);
        }

        void bind(Request request, boolean isManager, int currentUserId, int managerId, OnRequestActionListener listener) {
            Log.d("DEBUG_REQ", "Binding Request ID: " + request.requestId + ", Sender: " + request.senderName + ", Type: " + request.type);

            // Null-safe defaults for fallback strings
            String sender = (request.senderName != null) ? request.senderName : "Unknown User";
            String message = (request.messageText != null) ? request.messageText : "No message content";
            
            tvType.setText(request.type != null ? request.type.replace("_", " ") : "Request");
            tvStatus.setText(request.status != null ? request.status : "PENDING");
            tvSender.setText("From: " + sender);
            tvProject.setText("Project: " + (request.projectName != null ? request.projectName : "General"));

            // POINT 2: Manager Message Styling
            if (request.senderId == managerId) {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD")); // Light Blue
            } else {
                cardView.setCardBackgroundColor(android.graphics.Color.WHITE);
            }

            // POINT 4: Solid-colored status badges
            if ("APPROVED".equals(request.status)) {
                tvStatus.setBackgroundResource(R.drawable.status_badge_approved);
            } else if ("REJECTED".equals(request.status)) {
                tvStatus.setBackgroundResource(R.drawable.status_badge_rejected);
            } else {
                tvStatus.setBackgroundResource(R.drawable.status_badge_pending);
            }

            if (request.messageText != null && !request.messageText.isEmpty()) {
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText(message);
            } else {
                tvMessage.setVisibility(View.GONE);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
            tvTime.setText(sdf.format(new Date(request.timestamp)));

            // POINT 3: Role & Status Based Visibility + Self-Approval Shield
            boolean isSentByMe = (request.senderId == currentUserId);
            if (isManager && "PENDING".equals(request.status) && !isSentByMe) {
                layoutActions.setVisibility(View.VISIBLE);
            } else {
                layoutActions.setVisibility(View.GONE);
            }

            // POINT 6: Delete visibility (Sender or Manager)
            if (isSentByMe || isManager) {
                btnDelete.setVisibility(View.VISIBLE);
            } else {
                btnDelete.setVisibility(View.GONE);
            }

            btnApprove.setOnClickListener(v -> listener.onApprove(request));
            btnReject.setOnClickListener(v -> listener.onReject(request));
            btnDelete.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                        .setTitle("Delete Message?")
                        .setMessage("Are you sure you want to permanently delete this message/request?")
                        .setPositiveButton("Delete", (dialog, which) -> listener.onDelete(request))
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }
}
