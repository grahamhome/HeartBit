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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;


import java.util.Timer;
import java.util.TimerTask;

public class BreathingCoach extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, UIMessageHandlerOwnerActivity {

    // Thread messaging codes
    protected final static int REQUEST_ENABLE_BT = 1;
    protected final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    public final static int TOGGLE_RECORDING = 5;
    public final static int SESSION_TIMER_TICK = 10;
    public final static int CHRONOMETER_TICK = 11;

    // UI elements
    private static TextView connectionDisplay;
    private static Button toggleButton;
    private static ArcProgress sessionProgress;
    private static CircleProgress breathProgressIn;
    private static CircleProgress breathProgressOut;
    private static TextView sessionTimer;
    private static TextView helpLink;

    // Session variables
    private static boolean connected = false;
    private static boolean in = true;

    // Polar connection variables
    public static BluetoothAdapter bluetoothAdapter;
    private static BluetoothManager bluetoothManager;
    private static Polar polarService;
    private static RRReceiver receiverService;
    private static Handler uiMessageHandler;

    // Permission variables
    private static boolean location_permission_needed = false;

    // Timer variables TODO: Make a timer class
    private static int timerTickMS = 25;
    private static int secondsElapsed = 0;
    private static Timer animationTimer;

    public static int breathInTimeMS;
    public static int breathOutTimeMS;
    public static int sessionTimeMS;

    private boolean helping = false;
    private boolean warning = false;
    private boolean requesting = false;

    private Tone tone;

    private boolean participantSettings = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        participantSettings = UserData.getParticipantSettingsEnabled(BreathingCoach.this);
        // Get breathing rate & session length variables
        float breathInTimeSec = UserData.getBRIn(participantSettings, BreathingCoach.this);
        breathInTimeMS = (int)(breathInTimeSec *1000);
        float breathOutTimeSec = UserData.getBROut(participantSettings, BreathingCoach.this);
        breathOutTimeMS = (int)(breathOutTimeSec*1000);
        int sessionTimeMin = UserData.getSessionLength(participantSettings, BreathingCoach.this);
        sessionTimeMS = (sessionTimeMin*60000);
        // Set up UI
        setContentView(R.layout.activity_breathing_coach);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ((TextView)findViewById(R.id.title)).setText(sessionTimeMin + "-Minute " + getString(R.string.main_activity_title));
        connectionDisplay = findViewById(R.id.status_display);
        (breathProgressIn = findViewById(R.id.circle_progress_in)).setProgress(0);
        breathProgressIn.setMax(breathInTimeMS);
        (breathProgressOut = findViewById(R.id.circle_progress_out)).setMax(breathOutTimeMS);
        breathProgressOut.setProgress(breathOutTimeMS);
        (sessionProgress = findViewById(R.id.arc_progress)).setProgress(0);
        sessionProgress.setMax(sessionTimeMS);
        (sessionTimer = findViewById(R.id.session_timer)).setText("00:00");

        ((TextView)findViewById(R.id.current_rate)).setText(breathInTimeSec + "-second inhale, " + breathOutTimeSec + "-second exhale");

        tone = new Tone();

        (toggleButton = findViewById(R.id.toggle)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });
        (helpLink = findViewById(R.id.help_link)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!helping) {
                    helping = true;
                    final AlertDialog.Builder builder = new AlertDialog.Builder(BreathingCoach.this);
                    builder.setTitle(R.string.instructions_title);
                    builder.setMessage(R.string.instructions);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            helping = false;
                        }
                    });
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            helping = false;
                        }
                    });
                    builder.show();
                }
            }
        });

        // Check for permissions
        location_permission_needed = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED));

        // Request permissions
        if (location_permission_needed) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.permission_request_explanation_title);
            builder.setMessage(R.string.permission_request_explanation);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (location_permission_needed) {
                        requesting = true;
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                }
            });
            builder.show();
        } else {
            setUpMessageHandler();
            if (!connected) {
                connect();
            } else {
                showConnected();
            }
        }

        if (UserData.getNumberOfTrials(BreathingCoach.this) < PickerActivity.trialValues.size()) {
            findViewById(R.id.title).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder passwordPrompt = new AlertDialog.Builder(BreathingCoach.this);
                    passwordPrompt.setTitle(R.string.password_prompt_title);
                    passwordPrompt.setMessage(R.string.password_prompt_message_for_trials_skip);
                    final EditText passwordField = new EditText(BreathingCoach.this);
                    passwordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordPrompt.setView(passwordField);
                    passwordPrompt.setNegativeButton(R.string.cancel_btn_text, null);
                    passwordPrompt.setPositiveButton(R.string.enter_btn_text, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (passwordField.getText().toString().equals(getString(R.string.top_secret_password))) {
                                while (UserData.getNumberOfTrials(BreathingCoach.this) < PickerActivity.trialValues.size()+1) {
                                    UserData.incrementTrialCount(BreathingCoach.this);
                                }
                                Toast.makeText(getApplicationContext(), getString(R.string.trials_skipped_message), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.wrong_password_message), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    passwordPrompt.show();
                }
            });
        }
    }

    private void toggleRecording() {
        if (!RRReceiver.recording) {
            if (connected) {
                RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
                breathProgressIn.setText(getString(R.string.breathe_in));
                toggleButton.setText(getText(R.string.stop_btn_text));
                secondsElapsed = 0;
                (animationTimer = new Timer()).schedule(new TimerTask() {
                    long lastTickTime = 0;
                    long timeDifference = 0;

                    @Override
                    public void run() {
                        BreathingCoach.uiMessageHandler.obtainMessage(SESSION_TIMER_TICK).sendToTarget();
                        long now = SystemClock.elapsedRealtime();
                        if (lastTickTime == 0) {
                            lastTickTime = SystemClock.elapsedRealtime();
                        } else {
                            timeDifference = now - lastTickTime;
                        }
                        if (Math.round(timeDifference - 1000f) >= 1) {
                            BreathingCoach.uiMessageHandler.obtainMessage(CHRONOMETER_TICK).sendToTarget();
                            lastTickTime = now;
                        }
                    }
                }, 0, timerTickMS);
                tone.start();
            }
        } else {
            if (secondsElapsed < sessionTimeMS/1000){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.early_termination_warning_title);
                builder.setMessage(R.string.early_termination_warning);
                builder.setNegativeButton(android.R.string.no, null);
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        stopRecording();
                    }
                });
                builder.show();
            } else {
                stopRecording();
                if (UserData.getNumberOfTrials(BreathingCoach.this) < PickerActivity.trialValues.size()+1) {
                    UserData.incrementTrialCount(BreathingCoach.this);
                }
            }
        }
    }

    private void stopRecording() {
        tone.end();
        RRReceiver.rrHandler.obtainMessage(TOGGLE_RECORDING).sendToTarget();
        animationTimer.cancel();
        sessionTimer.setTextColor(Color.BLACK);
        sessionTimer.setText("00:00");
        breathProgressIn.setProgress(0);
        if (!in) {
            breathProgressOut.setVisibility(View.INVISIBLE);
            breathProgressIn.setVisibility(View.VISIBLE);
            in = true;
        }
        breathProgressOut.setProgress(breathOutTimeMS);
        sessionProgress.setProgress(0);
        breathProgressIn.setText(null);
        toggleButton.setText(getText(R.string.start_btn_text));
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
                        showConnected();
                        break;
                    case Polar.COULD_NOT_CONNECT:
                        if (!warning) {
                            warning = true;
                            AlertDialog.Builder builder = new AlertDialog.Builder(BreathingCoach.this);
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
                                    connectionDisplay.setText(R.string.connection_status_connecting);
                                    connectionDisplay.setTextColor(Color.BLACK);
                                    polarService.interrupt();
                                    (polarService = new Polar(bluetoothAdapter, getApplicationContext(), BreathingCoach.this)).start();
                                }
                            });
                            builder.show();
                        }
                        connectionDisplay.setText(R.string.connection_status_none);
                        connectionDisplay.setTextColor(Color.RED);
                        break;
                    case Polar.TIMEOUT:
                        Toast.makeText(getApplicationContext(), getString(R.string.timeout_warning), Toast.LENGTH_SHORT).show();
                        connectionDisplay.setText(R.string.connection_status_waiting);
                        connectionDisplay.setTextColor(Color.BLACK);
                        break;
                    case RRReceiver.USER_MESSAGE:
                        Toast.makeText(getApplicationContext(), inputMessage.obj.toString(), Toast.LENGTH_SHORT).show();
                        break;
                    case SESSION_TIMER_TICK:
                        float progress = in ? breathProgressIn.getProgress() : breathProgressOut.getProgress();
                        if ((in && progress == breathInTimeMS) || (!in && progress == 0)) {
                            in = !in;
                            if (in) {
                                breathProgressIn.setProgress(0);
                                breathProgressOut.setVisibility(View.INVISIBLE);
                                breathProgressIn.setVisibility(View.VISIBLE);
                            } else {
                                breathProgressOut.setProgress(breathOutTimeMS);
                                breathProgressIn.setVisibility(View.INVISIBLE);
                                breathProgressOut.setVisibility(View.VISIBLE);
                            }
                            tone.changeTone(in);
                        }
                        (in ? breathProgressIn : breathProgressOut).setProgress(
                                (in ? breathProgressIn : breathProgressOut).getProgress() +
                                        (timerTickMS * (in ? 1 : -1)));
                        sessionProgress.setProgress(sessionProgress.getProgress() + timerTickMS);
                        break;
                    case CHRONOMETER_TICK:
                        secondsElapsed++;
                        if (secondsElapsed <= sessionTimeMS/1000) {
                            int mins = secondsElapsed / 60;
                            int sec = secondsElapsed % 60;
                            String time = (mins > 9 ? mins : "0" + mins) + ":" + (sec > 9 ? sec : "0" + sec);
                            sessionTimer.setText(time);
                        } else {
                            sessionTimer.setTextColor(getColor(R.color.colorUnfinishedArc));
                        }
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
        (receiverService = new RRReceiver(BreathingCoach.this)).start();
        polarService = new Polar(bluetoothAdapter, getApplicationContext(), BreathingCoach.this);
        polarService.start();
    }

    private void showConnected() {
        connectionDisplay.setText(R.string.connection_status_ok);
        connectionDisplay.setTextColor(Color.GREEN);
        toggleButton.setBackground(getDrawable(R.drawable.button));
        toggleButton.setEnabled(true);
        connected = true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int grantResults[]) {
        requesting = false;
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        setUpMessageHandler();
                        if (!connected) {
                            connect();
                        } else {
                            showConnected();
                        }
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

    public Handler getUIMessageHandler() {
        return uiMessageHandler;
    }

    public Activity getActivity() {
        return BreathingCoach.this;
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
}
