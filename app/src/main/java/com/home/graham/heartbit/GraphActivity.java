package com.home.graham.heartbit;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import static com.home.graham.heartbit.BreathingCoach.REQUEST_ENABLE_BT;
import static com.home.graham.heartbit.BreathingCoach.TOGGLE_RECORDING;

public class GraphActivity extends AppCompatActivity implements UIMessageHandlerOwnerActivity {

    private static Handler handler;

    private static int avgWindowSize = 5;

    // UI elements
    private static Button toggleButton;
    private static GraphView graph;
    private static LineGraphSeries<DataPoint> breathSeries = new LineGraphSeries<>();
    private static LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<>();

    // Polar connection variables
    public static BluetoothAdapter bluetoothAdapter;
    private static BluetoothManager bluetoothManager;
    private static Polar polarService;
    private static RRReceiver receiverService;
    public static Handler uiMessageHandler;

    // Breath plot variables
    private static Runnable breathTimer;
    private static final int singleBreathTimeMS = 10000;
    private static final int updateRateMS = 50;
    private static final int frequency = singleBreathTimeMS/updateRateMS;

    // Session variables
    private static boolean connected = false;
    private static long recordingStartTime = 0;
    private boolean warning = false;
    private int currentXPos = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler = new Handler(Looper.getMainLooper());
        setUpGraph();
        (toggleButton = findViewById(R.id.toggle)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });
        setUpMessageHandler();
        connect();
    }

    private void setUpGraph() {
        graph = findViewById(R.id.graph);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(500);
        graph.getViewport().setMaxY(1000);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(frequency*5);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScalable(true);
    }

    private void toggleBreathTimer(boolean on) {
        if (on) {
            graph.removeSeries(breathSeries);
            breathSeries = new LineGraphSeries<>();
            graph.addSeries(breathSeries);
            (breathTimer = new Runnable() {

                @Override
                public void run() {
                    if (RRReceiver.recording) {
                        double yVal = ((Math.sin(((2 * Math.PI / (frequency)) * currentXPos) + (Math.PI / -2)) + 1) * 250) + 500;
                        breathSeries.appendData(new DataPoint(currentXPos, yVal), (currentXPos > frequency*5), 100000);
                        currentXPos++;
                        handler.postDelayed(this, updateRateMS);
                    }
                }
            }).run();
        }
    }

    private void toggleHRDisplay(boolean on) {
        if (on) {
            graph.removeSeries(hrSeries);
            recordingStartTime = 0;
            hrSeries = new LineGraphSeries<>();
            hrSeries.setColor(Color.RED);
            graph.addSeries(hrSeries);
        }
    }

    private void connect() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
        (receiverService = new RRReceiver(GraphActivity.this)).start();
        polarService = new Polar(bluetoothAdapter, getApplicationContext(), GraphActivity.this);
        polarService.start();
    }

    private void toggleRecording() {
        if (!RRReceiver.recording) {
            if (connected) {
                RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
                toggleButton.setText(getText(R.string.stop_btn_text));
                currentXPos = 0;
                toggleHRDisplay(true);
            }
        } else {
            RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
            toggleButton.setText(getText(R.string.start_btn_text));
            toggleBreathTimer(false);
            toggleHRDisplay(false);
        }
    }

    private void setUpMessageHandler() {
        uiMessageHandler = new Handler(Looper.getMainLooper()) {
            boolean flipped = false;
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case Polar.NEW_MEASUREMENT:
                        if (RRReceiver.recording) {
                            int numSamples = RRReceiver.timeSequentialRRValues.size();
                            if (numSamples >= avgWindowSize) {
                                float total = 0;
                                for (int i = numSamples; i > 0; i--) {
                                    total += RRReceiver.timeSequentialRRValues.get(i - 1);
                                }
                                float avg = total / avgWindowSize;
                                hrSeries.appendData(new DataPoint(currentXPos,
                                                ((float) 600000) / ((Polar.PolarTask) inputMessage.obj).rr),
                                        false, 1000000);
                                if (currentXPos == 0) {
                                    toggleBreathTimer(true);
                                }
                            }
                        }
                    case Polar.CONNECTED:
                        toggleButton.setBackground(getDrawable(R.drawable.button));
                        toggleButton.setEnabled(true);
                        connected = true;
                        break;
                    case Polar.COULD_NOT_CONNECT:
                        if (!warning) {
                            warning = true;
                            AlertDialog.Builder builder = new AlertDialog.Builder(GraphActivity.this);
                            builder.setTitle(R.string.monitor_not_found_title);
                            builder.setMessage(R.string.monitor_not_found_message);
                            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    warning = false;
                                }
                            });
                            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    warning = false;
                                    polarService.interrupt();
                                    (polarService = new Polar(bluetoothAdapter, getApplicationContext(), GraphActivity.this)).start();
                                }
                            });
                            builder.show();
                        }
                        break;
                    case Polar.TIMEOUT:
                        Toast.makeText(getApplicationContext(), "Make sure Polar is secure", Toast.LENGTH_SHORT).show();

                        break;
                    case RRReceiver.USER_MESSAGE:
                        Toast.makeText(getApplicationContext(), inputMessage.obj.toString(), Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
    }

    public Handler getUIMessageHandler() {
        return uiMessageHandler;
    }

    public Activity getActivity() {
        return GraphActivity.this;
    }
}