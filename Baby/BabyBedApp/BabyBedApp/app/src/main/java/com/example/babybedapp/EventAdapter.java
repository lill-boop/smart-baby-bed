package com.example.babybedapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView Adapter for BabyCam Events
 * 优化版本 - 更现代的卡片设计
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<BabyCamEvent> events = new ArrayList<>();
    private OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(BabyCamEvent event);
    }

    public EventAdapter(OnEventClickListener listener) {
        this.listener = listener;
    }

    public void setEvents(List<BabyCamEvent> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        BabyCamEvent event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private View viewEventBg;
        private TextView tvEventIcon;
        private TextView tvEventType;
        private TextView tvEventTime;
        private TextView tvEventDuration;
        private ImageView btnPlay;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            viewEventBg = itemView.findViewById(R.id.viewEventBg);
            tvEventIcon = itemView.findViewById(R.id.tvEventIcon);
            tvEventType = itemView.findViewById(R.id.tvEventType);
            tvEventTime = itemView.findViewById(R.id.tvEventTime);
            tvEventDuration = itemView.findViewById(R.id.tvEventDuration);
            btnPlay = itemView.findViewById(R.id.btnPlay);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEventClick(events.get(pos));
                }
            });
        }

        void bind(BabyCamEvent event) {
            // 设置图标和颜色
            if (event.isCryEvent()) {
                tvEventIcon.setText("😢");
                viewEventBg.setBackgroundResource(R.drawable.bg_event_icon_cry);
                tvEventType.setText("哭声检测");
                tvEventType.setTextColor(Color.parseColor("#D32F2F"));
            } else {
                tvEventIcon.setText("📹");
                viewEventBg.setBackgroundResource(R.drawable.bg_event_icon);
                tvEventType.setText("录像事件");
                tvEventType.setTextColor(Color.parseColor("#1976D2"));
            }

            tvEventTime.setText(event.getFormattedTime());
            tvEventDuration.setText(event.getSeconds() + "秒");
        }
    }
}
