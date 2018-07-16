package com.home.graham.heartbit;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BreathingCoach extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static int PERMISSION_REQUEST_WRITE_EXTERNAL = 2;

    public final static int TOGGLE_RECORDING = 5;

    private static TextView connectionDisplay;
    private static TextView bpmDisplay;
    private static TextView rrDisplay;
    private static Button toggleButton;

    public static BluetoothAdapter bluetoothAdapter;
    private static BluetoothManager bluetoothManager;
    private static Polar polarService;
    private static RRReceiver receiverService = new RRReceiver();
    public static Handler uiMessageHandler;
    private static Context context;

    private ArrayList<Float> rrValues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_breathing_coach);
        (connectionDisplay = findViewById(R.id.statusDisplay)).setText("Connecting");
        bpmDisplay = findViewById(R.id.bpmDisplay);
        rrDisplay = findViewById(R.id.rrDisplay);
        context = this;
        setUpMessageHandler();
        (toggleButton = findViewById(R.id.toggle)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
            }
        });

        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL);
        }

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
                switch (inputMessage.what) {
                    case Polar.NEW_MEASUREMENT:
                        connectionDisplay.setText(R.string.connection_status_ok);
                        connectionDisplay.setTextColor(Color.GREEN);
                    case Polar.CONNECTED:
                        connectionDisplay.setText(R.string.connection_status_ok);
                        connectionDisplay.setTextColor(Color.GREEN);
                        break;
                    case Polar.COULD_NOT_CONNECT:
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.monitor_not_found_title);
                        builder.setMessage(R.string.monitor_not_found_message);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                connectionDisplay.setText(R.string.connection_status_connecting);
                                connectionDisplay.setTextColor(Color.BLACK);
                                (polarService = new Polar(bluetoothAdapter, getApplicationContext())).start();
                            }
                        });
                        builder.show();
                        connectionDisplay.setText(R.string.connection_status_none);
                        connectionDisplay.setTextColor(Color.RED);
                        break;
                    case Polar.TIMEOUT:
                        Toast.makeText(getApplicationContext(), "Make sure Polar is secure", Toast.LENGTH_SHORT).show();
                        connectionDisplay.setText("Waiting for data");
                        connectionDisplay.setTextColor(Color.BLACK);
                        break;
                    case RRReceiver.NEW_VALUE:
                        rrValues.add((float)inputMessage.obj);
                        rrDisplay.setText(inputMessage.obj.toString() + System.lineSeparator() + rrDisplay.getText());
                        if (rrValues.size() > 5) {
                            float tot = 0;
                            for (float f : rrValues.subList(rrValues.size() - 6, rrValues.size() - 1)) {
                                tot += f;
                            }
                            bpmDisplay.setText(String.valueOf(60000/Math.round((tot / 5))));
                        }
                        break;
                    case RRReceiver.RECORDING_STARTED:
                        toggleButton.setText(R.string.stop_btn_text);
                        break;
                    case RRReceiver.RECORDING_STOPPED:
                        toggleButton.setText(R.string.start_btn_text);
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
        receiverService.start();
        polarService = new Polar(bluetoothAdapter, getApplicationContext());
        polarService.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        connect();
                    } else {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle(R.string.permission_denied_title);
                        builder.setMessage(R.string.permission_denied_message);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                System.exit(0);
                            }
                        });
                        builder.show();
                    }
                }
                break;
            case PERMISSION_REQUEST_WRITE_EXTERNAL:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Unable To Save"); // TODO: make these strings into variables
                    builder.setMessage("Since write permission has not been granted, this app will not be able to export the RR data it collects.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
                break;
        }
    }
}
