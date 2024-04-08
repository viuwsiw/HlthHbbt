package com.tshk.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.tshk.myapplication.Data.MyDatabaseHelper;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HeadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HeadFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    MyDatabaseHelper dbHelper;
    public ImageButton imageButton;
    Random random = new Random();
    int randomIndex = random.nextInt(24) + 1;


    public HeadFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HeadFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HeadFragment newInstance(String param1, String param2) {
        HeadFragment fragment = new HeadFragment();
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
        return inflater.inflate(R.layout.fragment_head, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView textView13 = view.findViewById(R.id.textView13);
        String randomTip = getString(getResources().getIdentifier("tips" + randomIndex, "string", getActivity().getPackageName()));
        textView13.setText(randomTip);

        imageButton = view.findViewById(R.id.imageButton);
        ImageButton spotify1 = view.findViewById(R.id.imageButton3);
        ImageButton spotify2 = view.findViewById(R.id.imageButton5);
        ImageButton yandexmusic1 = view.findViewById(R.id.imageButton6);
        ImageButton yandexmusic2 = view.findViewById(R.id.imageButton7); //vetka
        loadImageFromStorage();
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent11 = new Intent(getActivity(), app_activity.class);
                startActivity(intent11);
            }
        });
        spotify1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://open.spotify.com/playlist/37i9dQZF1DX889U0CL85jj?si=4675dec61cc44afc";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        spotify2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://open.spotify.com/playlist/37i9dQZF1DXbXD9pMSZomS?si=5ac6b21554fa442c";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        yandexmusic1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://music.yandex.ru/users/music-blog/playlists/2467";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        yandexmusic2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = "https://music.yandex.ru/users/music-blog/playlists/2561";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

    }

    private Bitmap loadImageFromStorage() {
        try {
            // Получаем файл из внутренней памяти
            File file = new File(requireActivity().getFilesDir(), "avatar.jpg");

            // Читаем Bitmap из файла
            FileInputStream fis = new FileInputStream(file);
            Bitmap bitmap = BitmapFactory.decodeStream(fis);
            imageButton = requireView().findViewById(R.id.imageButton);
            imageButton.setImageBitmap(bitmap);
            fis.close();

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


}