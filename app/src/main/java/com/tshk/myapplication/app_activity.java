package com.tshk.myapplication;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.tshk.myapplication.Data.MyDatabaseHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
public class app_activity extends AppCompatActivity {

    private TextView textView5;
    private TextView textView10;
    private TextView AgeText;
    private ImageView imageView3;
    private MyDatabaseHelper dbHelper;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app);
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

        textView5 = findViewById(R.id.textView5);
        AgeText = findViewById(R.id.textView23);
        textView10 = findViewById(R.id.textView10);
        imageView3 = findViewById(R.id.imageView3); // Assuming you have an ImageView with this ID
        dbHelper = new MyDatabaseHelper(this);


        loadImageFromStorage();

        readFromDatabase();

        Button buttonEditUserData = findViewById(R.id.button7);
        buttonEditUserData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                db.delete("user_table", null, null);


                File file = new File(getFilesDir(), "avatar.jpg");
                if (file.exists() && !file.delete()) {
                    // Удаление файла не удалось
                }

                SharedPreferences preferences = getSharedPreferences("local_data", MODE_PRIVATE);
                preferences.edit().clear().apply();

                db.close();


                Intent intent1 = new Intent(app_activity.this, StartMenu.class);
                startActivity(intent1);
                finish();

            }
        });

        Button buttonComeback = findViewById(R.id.button4);
        Button buttonEditProfile = findViewById(R.id.button6);
        buttonComeback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent1 = new Intent(app_activity.this, SecondAppActivity.class);
                startActivity(intent1);

            }
        });
        buttonEditProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent1 = new Intent(app_activity.this, EditProfileMenu.class);
                startActivity(intent1);

            }
        });


    }

    private Bitmap loadImageFromStorage() {
        try {
            // Получаем файл из внутренней памяти
            File file = new File(getFilesDir(), "avatar.jpg");

            // Читаем Bitmap из файла
            FileInputStream fis = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            imageView3.setImageBitmap(bitmap);
            fis.close();

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void readFromDatabase() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("user_table", null, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
            int gender = cursor.getInt(cursor.getColumnIndexOrThrow("gender"));
            int ageText = cursor.getInt(cursor.getColumnIndexOrThrow("age"));


            textView5.setText("Имя: " + username);

            String genderText;

            if (gender == 1) {
                genderText = "Мужской";
            } else {
                genderText = "Женский";
            }

            textView10.setText("Пол: " + genderText);
            AgeText.setText(ageText + " лет");
        }
        if (cursor != null) {
            cursor.close();
        }




        db.close();

    }
}
