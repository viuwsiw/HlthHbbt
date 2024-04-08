package com.tshk.myapplication.Data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HealthDataDao {
    @Insert
    void insert(HealthData healthData);

    @Query("SELECT * FROM health_data")
    List<HealthData> getAll();

    // Добавлен метод getHealthDataByDateTime
    @Query("SELECT * FROM health_data WHERE dateTime = :dateTime")
    List<HealthData> getHealthDataByDateTime(String dateTime);
}
