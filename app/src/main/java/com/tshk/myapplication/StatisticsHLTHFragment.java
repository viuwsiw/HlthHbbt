package com.tshk.myapplication;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.tshk.myapplication.Data.MyDatabaseHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StatisticsHLTHFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StatisticsHLTHFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    MyDatabaseHelper dbHelper;

    public Bitmap bitmap;
    public TextView userNameStat;
    public ImageView imageViewStat;
    public TextView genderStat;
    public TextView waterStat;


    public StatisticsHLTHFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment StatisticsHLTHFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static StatisticsHLTHFragment newInstance(String param1, String param2) {
        StatisticsHLTHFragment fragment = new StatisticsHLTHFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        dbHelper = new MyDatabaseHelper(getActivity());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_statistics_h_l_t_h, container, false);

        userNameStat = view.findViewById(R.id.textView18);
        imageViewStat = view.findViewById(R.id.imageView4);
        genderStat = view.findViewById(R.id.textView22);
        waterStat = view.findViewById(R.id.textView27);

        readDatabase();
        readDatabase1();
        return view;
    }

    private void readDatabase()
    {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("user_table", null, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
            int gender = cursor.getInt(cursor.getColumnIndexOrThrow("gender"));
            int waterStatic = cursor.getInt(cursor.getColumnIndexOrThrow("water"));


            userNameStat.setText(username);

            String genderText;

            if (gender == 1) {
                genderText = "Мужской пол";
            } else {
                genderText = "Женский пол";
            }

            genderStat.setText(genderText);

            waterStat.setText("Выпило литров: " + waterStatic);
        }
        if (cursor != null) {
            cursor.close();
        }
        db.close();


        try {
            // Получаем файл из внутренней памяти
            File file = new File(requireActivity().getFilesDir(), "avatar.jpg");

            // Читаем Bitmap из файла
            FileInputStream fis = new FileInputStream(file);
            bitmap = BitmapFactory.decodeStream(fis);
            imageViewStat.setImageBitmap(bitmap);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readDatabase1(){




    }

}