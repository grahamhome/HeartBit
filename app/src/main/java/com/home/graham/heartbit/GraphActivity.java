package com.home.graham.heartbit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import static com.home.graham.heartbit.BreathingCoach.PERMISSION_REQUEST_COARSE_LOCATION;
import static com.home.graham.heartbit.BreathingCoach.REQUEST_ENABLE_BT;
import static com.home.graham.heartbit.BreathingCoach.TOGGLE_RECORDING;

public class GraphActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, UIMessageHandlerOwnerActivity {

    private static Handler handler;

    private static int avgWindowSize = 2;

    // UI elements
    private static Button toggleButton;
    private static GraphView graph;
    private static LineGraphSeries<DataPoint> breathSeries = new LineGraphSeries<>();
    private static LineGraphSeries<DataPoint> hrSeries = new LineGraphSeries<>();

    // Permission variables
    private static boolean location_permission_needed = false;

    // Polar connection variables
    public static BluetoothAdapter bluetoothAdapter;
    private static BluetoothManager bluetoothManager;
    private static Polar polarService;
    private static RRReceiver receiverService;
    private static Handler uiMessageHandler;

    // Breath plot variables
    private static Runnable breathTimer;
    private static final int singleBreathTimeMS = 10000;
    private static final int updateRateMS = 50;
    private static final int frequency = singleBreathTimeMS/updateRateMS;

    // Session variables
    private static boolean connected = false;
    private boolean warning = false;
    private int currentXPos = 0;
    private boolean requesting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler = new Handler(Looper.getMainLooper());
        graph = findViewById(R.id.graph);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Session Duration (Seconds)");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Heart Rate (BPM)");
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        graph.getGridLabelRenderer().setLabelFormatter( new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    if (value % 1000 == 0) {
                        return String.valueOf((int)(value/1000));
                    } else {
                        return "";
                    }
                } else {
                    return super.formatLabel(value, isValueX);
                }
            }
        });

        setUpGraph();
        (toggleButton = findViewById(R.id.toggle)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });

        // Check for permissions
        location_permission_needed = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                (this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED));

        // Request permissions
        if (location_permission_needed) {
            requesting = true;
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.permission_request_explanation_title);
            builder.setMessage(R.string.permission_request_explanation);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (location_permission_needed) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                    else {
                        requesting = false;
                    }
                }
            });
            builder.show();
        } else {
            setUpMessageHandler();
            connect();
        }

    }

    private void setUpGraph() {
        graph.removeSeries(breathSeries);
        breathSeries = new LineGraphSeries<>();
        breathSeries.setTitle("Breathing Rate");
        graph.addSeries(breathSeries);
        graph.removeSeries(hrSeries);
        hrSeries = new LineGraphSeries<>();
        hrSeries.setTitle("Heart Rate");
        hrSeries.setColor(Color.RED);
        graph.addSeries(hrSeries);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(50);
        graph.getViewport().setMaxY(100);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(singleBreathTimeMS*10);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(false);
        graph.getViewport().setScrollableY(true);
    }

    private void enableBreathTimer() {
        (breathTimer = new Runnable() {

            @Override
            public void run() {
                if (RRReceiver.recording) {
                    double yVal = ((Math.sin(((2 * Math.PI / (frequency)) * currentXPos) + (Math.PI / -2)) + 1) * 20) + 60;
                    breathSeries.appendData(new DataPoint(currentXPos*updateRateMS, yVal), (currentXPos*updateRateMS > singleBreathTimeMS*10), 1000000);
                    currentXPos++;
                    handler.postDelayed(this, updateRateMS);
                }
            }
        }).run();
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
                setUpGraph();
            }
        } else {
            RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
            toggleButton.setText(getText(R.string.start_btn_text));
        }
    }

    private void setUpMessageHandler() {
        uiMessageHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case Polar.NEW_MEASUREMENT:
                        if (RRReceiver.recording) {
                            int numSamples = RRReceiver.timeSequentialRRValues.size();
                            if (numSamples >= avgWindowSize) {
                                float total = 0;
                                for (int i = 1; i <= avgWindowSize; i++) {
                                    total += RRReceiver.timeSequentialRRValues.get(numSamples-i);
                                }
                                float avg = total / avgWindowSize;
                                hrSeries.appendData(new DataPoint(currentXPos*updateRateMS,
                                                ((float) 60000) / avg),
                                        false, 1000000);
                                if (currentXPos == 0) {
                                    enableBreathTimer();
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

    @Override
    public void onPause() {
        super.onPause();
        if (!requesting) {
            if (RRReceiver.recording) {
                toggleRecording();
            }
            uiMessageHandler = new Handler(Looper.getMainLooper());
            if (polarService != null) {
                polarService.interrupt();
            }
            if (receiverService != null) {
                receiverService.interrupt();
            }
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]) {
        requesting = false;
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        setUpMessageHandler();
                        connect();
                    } else {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle(R.string.permission_denied_title);
                        builder.setMessage(R.string.permission_denied_message);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.addCategory(Intent.CATEGORY_HOME);
                                startActivity(intent);
                                finish();
                            }
                        });
                        builder.show();
                    }
                }
                break;
        }
    }
}