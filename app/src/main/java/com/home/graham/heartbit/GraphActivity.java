package com.home.graham.heartbit;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class GraphActivity extends AppCompatActivity {

    private static Handler handler;

    private static GraphView graph;
    private static LineGraphSeries<DataPoint> breathSeries = new LineGraphSeries<>();

    private static final int singleBreathTimeMS = 10000;
    private static final int updateRateMS = 50;
    private static final int frequency = singleBreathTimeMS/updateRateMS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler = new Handler(Looper.getMainLooper());
        (graph = findViewById(R.id.graph)).addSeries(breathSeries);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(2);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(frequency);
        final Runnable breathTimer = new Runnable() {
            int x = 0;
            @Override
            public void run() {
                double yVal = Math.sin(((2*Math.PI/(frequency))*x) + (Math.PI/-2)) + 1;
                breathSeries.appendData(new DataPoint(x, yVal), (x > frequency), frequency);
                x++;
                handler.postDelayed(this, updateRateMS);
            }
        };
        breathTimer.run();
    }
}

