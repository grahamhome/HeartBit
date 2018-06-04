package com.home.graham.heartbit;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BreathingCoach extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private static TextView rrDisplay;
    private static TextView bpmDisplay;

    public static BluetoothAdapter bluetoothAdapter;
    private static BluetoothManager bluetoothManager;

    private static Polar polarService;

    public static Handler uiMessageHandler;
    private static ArrayList<Integer> rrValues = new ArrayList<>();
    private static int[] recentValues = new int[5];
    private static int rvIndex = 0;

    private static Context context;

    // Indicates whether or not we are considering the most recently received value as a possible error.
    private static boolean possibleError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breathing_coach);
        rrDisplay = findViewById(R.id.rrDisplay);
        bpmDisplay = findViewById(R.id.bpmDisplay);
        bpmDisplay.setText("Waiting...");
        context = this;
        setUpMessageHandler();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            } else {
                connect();
            }
        } else {
            connect();
        }
    }

    private void setUpMessageHandler() {
        uiMessageHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                Polar.PolarTask polarTask = (Polar.PolarTask)inputMessage.obj;
                switch (inputMessage.what) {
                    case Polar.NEW_MEASUREMENT:
                        // Check to see if the current beat is a possible error
                        if (rrValues.size() > 0 && Math.abs(polarTask.rr - rrValues.get(rrValues.size() - 1)) > 0.25 * rrValues.get(rrValues.size() - 1)) {

                            if (possibleError) {
                                if (Math.abs(polarTask.rr - rrValues.get(rrValues.size() - 2)) <= 0.25 * rrValues.get(rrValues.size() - 2)){
                                    // The error value was a one-off event, and should be removed
                                    rrValues.remove(rrValues.size() - 1);
                                    rrDisplay.setText("Most recent value removed" + System.lineSeparator() + rrDisplay.getText());
                                    possibleError = false;
                                } else {
                                    // We have seen a second value that is an aberrant increase or decrease, so the values
                                    // are simply rising or falling faster than we expect.
                                    possibleError = false;
                                }
                            } else {
                                possibleError = true;
                            }
                        }
                        // Add the beat to the list and recalculate the bpm
                        rrValues.add(polarTask.rr);
                        rrDisplay.setText(String.valueOf(polarTask.rr) + System.lineSeparator() + rrDisplay.getText());
                        if (rrValues.size() > 5) {
                            float tot = 0;
                            for (int i : rrValues.subList(rrValues.size()-6, rrValues.size()-1)) {
                                tot += i;
                            }
                            bpmDisplay.setText(String.valueOf(Math.round(61440.0 / (tot / 5))));
                        }
                        break;
                    case Polar.COULD_NOT_CONNECT:
                        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Unable To Connect"); // TODO: make these strings into variables
                        builder.setMessage("The Polar HR monitor could not be found. Please ensure it is attached to the chest strap and is being worn correctly before continuing.");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                polarService.start();
                            }
                        });
                        builder.show();
                        break;
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
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
        polarService = new Polar(bluetoothAdapter, getApplicationContext());
        polarService.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connect();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Unable To Continue"); // TODO: make these strings into variables
                    builder.setMessage("Since location access has not been granted, this app will not be able to scan for the Polar heart rate sensor.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            System.exit(0);
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }
}
