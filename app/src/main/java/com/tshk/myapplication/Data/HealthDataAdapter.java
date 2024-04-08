package com.tshk.myapplication.Data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.tshk.myapplication.R;

import java.util.List;

public class HealthDataAdapter extends RecyclerView.Adapter<HealthDataAdapter.ViewHolder> {
    private List<HealthData> healthDataList;

    // ViewHolder class
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Define view items here
        TextView dateView;
        TextView waterIntakeView;

        public ViewHolder(View itemView) {
            super(itemView);
            // Initialize view items here
            dateView = itemView.findViewById(R.id.textViewDateTime);
            waterIntakeView = itemView.findViewById(R.id.textViewWater);
        }
    }

    public HealthDataAdapter(List<HealthData> healthDataList) {
        this.healthDataList = healthDataList;
    }


    // onCreateViewHolder method
    @Override
    public HealthDataAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_health, parent, false);
        return new ViewHolder(view);
    }

    // onBindViewHolder method
    @Override
    public void onBindViewHolder(HealthDataAdapter.ViewHolder holder, int position) {
        HealthData healthData = healthDataList.get(position);
        // Bind healthData to the view items here
        holder.dateView.setText(healthData.getDateTime());
        holder.waterIntakeView.setText(String.valueOf(healthData.getWaterIntake()));
    }


    // getItemCount method
    @Override
    public int getItemCount() {
        return healthDataList.size();
    }

    public void setHealthData(List<HealthData> healthDataList) {
        this.healthDataList = healthDataList;
        notifyDataSetChanged();
    }


}

