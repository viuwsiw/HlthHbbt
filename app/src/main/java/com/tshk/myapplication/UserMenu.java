
package com.tshk.myapplication;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.tshk.myapplication.Data.MyDatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class UserMenu extends AppCompatActivity {

    private static final int RESULT_LOAD_IMAGE = 1;

    TextView textView6;
    ImageView imageView;
    public ToggleButton toggleButton; // Add this line
    MyDatabaseHelper dbHelper;
    public int gender;
    public int appstarts = 0;
    public int Age;
    public Bitmap avatarBitmap;
    public File file;
    AutoCompleteTextView autoCompleteTextView;
    public FileOutputStream fos;
    public Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_menu);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        dbHelper = new MyDatabaseHelper(this);
        readFromDatabase();

        super.getOnBackPressedDispatcher();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                db.delete("user_table", null, null);
                File file = new File(getFilesDir(), "avatar.jpg");
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        // Файл успешно удален
                    } else {
                        // Удаление файла не удалось
                    }
                } else {
                    // Файл не существует
                }
                SharedPreferences preferences = getSharedPreferences("local_data", MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.clear();
                editor.apply();
                Intent intent1 = new Intent(UserMenu.this, MainActivity.class);
                startActivity(intent1);
                finish();
            }
        });

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
        Button buttonAnim = findViewById(R.id.button2);
        Button buttonAnim1 = findViewById(R.id.button);
        TextView textViewAnim1 = findViewById(R.id.textView4);
        TextView textViewAnim2 = findViewById(R.id.textView6);
        ToggleButton toggleButtonAnim = findViewById(R.id.toggleButton);
        ImageView imageViewAnim = findViewById(R.id.imageView);
        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        TextView textViewAnim3 = findViewById(R.id.textView19);
        // Применяем анимацию к кнопке
        buttonAnim.startAnimation(animation);
        buttonAnim1.startAnimation(animation);
        textViewAnim1.startAnimation(animation);
        textViewAnim2.startAnimation(animation);
        toggleButtonAnim.startAnimation(animation);
        imageViewAnim.startAnimation(animation);
        autoCompleteTextView.startAnimation(animation);
        textViewAnim3.startAnimation(animation);

        imageView = findViewById(R.id.imageView); // Assuming you have an ImageView with this ID
        toggleButton = findViewById(R.id.toggleButton); // Assuming you have a ToggleButton with this ID



        Button button22 = findViewById(R.id.button2);
        button22.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGallery();
            }
        });

        Button button11 = findViewById(R.id.button);
        button11.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();

                int tglbttn = 0;
                if (toggleButton.isChecked()) {
                    tglbttn = 1;
                } else {
                    tglbttn = 2;
                }

                String text = autoCompleteTextView.getText().toString();
                Age = Integer.parseInt(text);

                values.put("gender", tglbttn);
                values.put("age", Age);

                db.update("user_table", values, "_id = ?", new String[] {String.valueOf(tglbttn)});
                db.close();
                appstarts = 1;


                if (imageView.getDrawable() != null) {
                    SharedPreferences sharedPreferences = getSharedPreferences("local_data", MODE_PRIVATE);
                    SharedPreferences.Editor editing = sharedPreferences.edit();
                    editing.putInt("appstarts", appstarts);
                    editing.apply(); // Примените изменения
                    saveImageToStorage(imageView);
                    Intent intent11 = new Intent(UserMenu.this, SecondAppActivity.class);
                    startActivity(intent11);
                    finish();
                } else {
                    Toast.makeText(UserMenu.this, "Выберите изображение! Это необходимо!", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    private void saveImageToStorage(ImageView imageView) {
        try {
            // Создаем файл во внутренней памяти
            file = new File(getFilesDir(), "avatar.jpg");
            // Сохраняем Bitmap в файл

            bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
            fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();

            // Теперь файл содержит сохраненное изображение
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RESULT_LOAD_IMAGE);
        Button buttonsss = findViewById(R.id.button);
        buttonsss.setEnabled(true);
        buttonsss.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK) {
            if (data != null) {
                // Get the selected image URI
                Uri imageUri = data.getData();

                // Load the image into the ImageView using Glide
                Glide.with(this)
                        .load(imageUri)
                        .centerCrop()
                        .into(imageView);
            }
        }
    }

    private void readFromDatabase() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("user_table", null, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
            textView6 = findViewById(R.id.textView6);
            textView6.setText(username);
        }
        if (cursor != null) {
            cursor.close();
        }


    }
}
