package com.tshk.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.tshk.myapplication.Data.HealthData;
import com.tshk.myapplication.Data.HealthDataAdapter;
import com.tshk.myapplication.Data.HealthDataDao;

import java.util.List;


public class HealthFragment extends Fragment {
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    public static String dateTime;

    private String mParam1;
    private String mParam2;
    private HealthDataDao healthDataDao;
    public static float progress_water_data;
    public static int id;
    public String currentDateAndTime;
    private RecyclerView recyclerView;


    private HealthDataAdapter adapter;
    private List<HealthData> healthDataList;;


    public HealthFragment() {
        // Конструктор по умолчанию
    }



    public static HealthFragment newInstance(String param1, String param2) {
        HealthFragment fragment = new HealthFragment();
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
        if (getActivity() != null) {

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health, container, false);

        TextView textView = view.findViewById(R.id.textView17);
        Button addButton = view.findViewById(R.id.button5);

        //new AsyncTask<Void, Void, List<HealthData>>() {
          //  @Override
           // protected List<HealthData> doInBackground(Void... voids) {

            //    Log.e("AsyncTask", "doInBackground started");
            //    if (healthDataDao != null) {
            //        healthDataList = healthDataDao.getAll();
            //    }
           //     Log.e("AsyncTask", "doInBackground finished");
           //     return healthDataList;
         //   }

          //  @Override
         //   protected void onPostExecute(List<HealthData> healthDataList) {
             //   Log.e("AsyncTask", "onPostExecute started");
             //   if (adapter != null && healthDataList != null) {
             //       adapter.setHealthData(healthDataList);
            //    }
           //     Log.e("AsyncTask", "onPostExecute finished");
          //  }
      //  }.execute();



        addButton.setOnClickListener(v -> {
            Toast.makeText(getActivity(), "В разработке!", Toast.LENGTH_SHORT).show();
        });

        return view;
    }




}