package com.tshk.myapplication;

import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.tabs.TabLayout;
import com.tshk.myapplication.databinding.ActivitySecondAppBinding;

public class SecondAppActivity extends AppCompatActivity {
    private ActivitySecondAppBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        super.getOnBackPressedDispatcher();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Здесь можно добавить логику для блокирования кнопки "Назад"
                        // Например, ничего не делаем, чтобы заблокировать кнопку "Назад"
                    }
                });

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        binding = ActivitySecondAppBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentContainerView2);
        NavController navController = navHostFragment.getNavController();

        if (savedInstanceState == null) {
            navController.navigate(R.id.HeadFragment);
        }


        // Настройка TabLayout с NavController
        TabLayout appbar = findViewById(R.id.tabs);
        appbar.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        navController.navigate(R.id.HeadFragment);
                        break;
                    case 1:
                        navController.navigate(R.id.HealthFragment);
                        break;
                    case 2:
                        navController.navigate(R.id.StatisticsHLTHFragment);
                        break;
                }
            }


            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Обработка, если нужно
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Обработка, если нужно
            }
        });
    }



}

