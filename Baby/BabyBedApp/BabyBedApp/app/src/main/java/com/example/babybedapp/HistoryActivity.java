package com.example.babybedapp;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.babybedapp.db.AppDatabase;
import com.example.babybedapp.db.EventDao;
import com.example.babybedapp.db.HourlyStat;
import com.example.babybedapp.db.StatusSampleDao;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 历史统计页面
 */
public class HistoryActivity extends AppCompatActivity {

    private LineChart chartTemp;
    private BarChart chartEvents;
    private TextView tvHourlyList;
    private Button btn3Hours, btn24Hours, btn3Days;

    private AppDatabase database;
    private ExecutorService executor;

    private int currentRangeHours = 24; // 默认24小时

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        database = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();

        initViews();
        setupListeners();
        loadData();
    }

    private void initViews() {
        chartTemp = findViewById(R.id.chartTemp);
        chartEvents = findViewById(R.id.chartEvents);
        tvHourlyList = findViewById(R.id.tvHourlyList);
        btn3Hours = findViewById(R.id.btn3Hours);
        btn24Hours = findViewById(R.id.btn24Hours);
        btn3Days = findViewById(R.id.btn3Days);

        // 配置折线图
        chartTemp.getDescription().setEnabled(false);
        chartTemp.setTouchEnabled(true);
        chartTemp.setDragEnabled(true);
        chartTemp.setScaleEnabled(true);
        chartTemp.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        // 配置柱状图
        chartEvents.getDescription().setEnabled(false);
        chartEvents.setTouchEnabled(true);
        chartEvents.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        updateButtonStates();
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btn3Hours.setOnClickListener(v -> {
            currentRangeHours = 3;
            updateButtonStates();
            loadData();
        });

        btn24Hours.setOnClickListener(v -> {
            currentRangeHours = 24;
            updateButtonStates();
            loadData();
        });

        btn3Days.setOnClickListener(v -> {
            currentRangeHours = 72;
            updateButtonStates();
            loadData();
        });
    }

    private void updateButtonStates() {
        btn3Hours.setAlpha(currentRangeHours == 3 ? 1.0f : 0.5f);
        btn24Hours.setAlpha(currentRangeHours == 24 ? 1.0f : 0.5f);
        btn3Days.setAlpha(currentRangeHours == 72 ? 1.0f : 0.5f);
    }

    private void loadData() {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            long startTs = now - (currentRangeHours * 60 * 60 * 1000L);

            // 获取温度数据
            List<StatusSampleDao.HourlyAvgTemp> tempData = database.statusSampleDao().getHourlyAvgTemp(startTs);

            // 获取事件数据
            List<EventDao.HourlyEventCount> wetData = database.eventDao().getHourlyCount("WET", startTs);
            List<EventDao.HourlyEventCount> cryData = database.eventDao().getHourlyCount("CRY", startTs);

            // 获取小时统计列表
            List<HourlyStat> hourlyStats = database.hourlyStatDao().getRecent72Hours(now - 72 * 60 * 60 * 1000L);

            runOnUiThread(() -> {
                updateTempChart(tempData);
                updateEventsChart(wetData, cryData);
                updateHourlyList(hourlyStats);
            });
        });
    }

    private void updateTempChart(List<StatusSampleDao.HourlyAvgTemp> data) {
        if (data == null || data.isEmpty()) {
            chartTemp.clear();
            chartTemp.invalidate();
            return;
        }

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (int i = 0; i < data.size(); i++) {
            StatusSampleDao.HourlyAvgTemp item = data.get(i);
            entries.add(new Entry(i, (float) item.avg_temp));
            labels.add(sdf.format(new Date(item.hour_ts)));
        }

        LineDataSet dataSet = new LineDataSet(entries, "平均体温 (℃)");
        dataSet.setColor(Color.parseColor("#1565C0"));
        dataSet.setCircleColor(Color.parseColor("#1565C0"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        chartTemp.setData(lineData);
        chartTemp.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chartTemp.getXAxis().setGranularity(1f);
        chartTemp.invalidate();
    }

    private void updateEventsChart(List<EventDao.HourlyEventCount> wetData,
            List<EventDao.HourlyEventCount> cryData) {
        // 合并数据
        ArrayList<BarEntry> wetEntries = new ArrayList<>();
        ArrayList<BarEntry> cryEntries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // 简化处理：使用 wetData 的时间轴
        for (int i = 0; i < Math.max(wetData != null ? wetData.size() : 0,
                cryData != null ? cryData.size() : 0); i++) {
            float wetCount = 0;
            float cryCount = 0;
            String label = "";

            if (wetData != null && i < wetData.size()) {
                wetCount = wetData.get(i).count;
                label = sdf.format(new Date(wetData.get(i).hour_ts));
            }
            if (cryData != null && i < cryData.size()) {
                cryCount = cryData.get(i).count;
                if (label.isEmpty()) {
                    label = sdf.format(new Date(cryData.get(i).hour_ts));
                }
            }

            wetEntries.add(new BarEntry(i, wetCount));
            cryEntries.add(new BarEntry(i, cryCount));
            labels.add(label);
        }

        if (wetEntries.isEmpty() && cryEntries.isEmpty()) {
            chartEvents.clear();
            chartEvents.invalidate();
            return;
        }

        BarDataSet wetDataSet = new BarDataSet(wetEntries, "尿床");
        wetDataSet.setColor(Color.parseColor("#E65100"));

        BarDataSet cryDataSet = new BarDataSet(cryEntries, "哭泣");
        cryDataSet.setColor(Color.parseColor("#C2185B"));

        BarData barData = new BarData(wetDataSet, cryDataSet);
        barData.setBarWidth(0.35f);

        chartEvents.setData(barData);
        chartEvents.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chartEvents.groupBars(0f, 0.1f, 0.05f);
        chartEvents.invalidate();
    }

    private void updateHourlyList(List<HourlyStat> stats) {
        if (stats == null || stats.isEmpty()) {
            tvHourlyList.setText("暂无数据");
            return;
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:00", Locale.getDefault());

        sb.append("时间         | 平均温度 | 尿床 | 哭泣\n");
        sb.append("-------------|----------|------|------\n");

        for (HourlyStat stat : stats) {
            sb.append(String.format(Locale.getDefault(),
                    "%s | %.1f ℃  | %d   | %d\n",
                    sdf.format(new Date(stat.hour_start_ts)),
                    stat.avg_temp,
                    stat.wet_count,
                    stat.cry_count));
        }

        tvHourlyList.setText(sb.toString());
    }
}
