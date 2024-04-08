package com.tshk.myapplication;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tshk.myapplication.Data.MyDatabaseHelper;

public class StartMenu extends AppCompatActivity {
    private EditText editTextText;
    private Button button3;
    MyDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_menu);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        final ConstraintLayout constraintLayout = findViewById(R.id.textCheckerDatabase);
        constraintLayout.post(new Runnable() {
            @Override
            public void run() {
                final ConstraintLayout constraintLayout = findViewById(R.id.textCheckerDatabase);
                int[] colors = new int[]{Color.parseColor("#A8E4A0"), Color.parseColor("#BDECB6"),
                        Color.parseColor("#C5F0B3"), Color.parseColor("#D0F5C0"),
                        Color.parseColor("#DAFACD"), Color.parseColor("#E4FFDA")};
                ObjectAnimator colorAnimation = ObjectAnimator.ofInt(constraintLayout, "backgroundColor", colors);
                colorAnimation.setEvaluator(new ArgbEvaluator());
                colorAnimation.setDuration(3000); // продолжительность анимации в миллисекундах
                colorAnimation.setRepeatCount(ValueAnimator.INFINITE);
                colorAnimation.setRepeatMode(ValueAnimator.REVERSE);
                colorAnimation.setInterpolator(new TimeInterpolator() {
                    @Override
                    public float getInterpolation(float input) {
                        // Эффект "волны" достигается за счет модификации входного значения
                        return (float) Math.sin(input * 2); // Можно изменить частоту и амплитуду волн
                    }
                });
                colorAnimation.start();
            }
        });

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.animations_startlayouts);
        Button buttonAnim1 = findViewById(R.id.button3);
        TextView textViewAnim1 = findViewById(R.id.textView);
        TextView textViewAnim2 = findViewById(R.id.textView7);
        EditText editTextAnim = findViewById(R.id.editTextText);
        // Применяем анимацию к кнопке
        buttonAnim1.startAnimation(animation);
        textViewAnim1.startAnimation(animation);
        textViewAnim2.startAnimation(animation);
        editTextAnim.startAnimation(animation);

        editTextText = findViewById(R.id.editTextText);
        button3 = findViewById(R.id.button3);
        dbHelper = new MyDatabaseHelper(this);

        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToDatabase(editTextText.getText().toString());
                Intent intent = new Intent(StartMenu.this, UserMenu.class);
                startActivity(intent);
                finish();

            }
        });
    }

    private void saveToDatabase(String username) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        db.insert("user_table", null, values);
    }
}