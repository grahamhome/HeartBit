package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private RelativeLayout mainLayout;
    private EditText inSec;
    private EditText outSec;
    private EditText sessionMin;
    private CheckBox inOutPaired;
    private CheckBox participantSettingsEnabled;
    private Button copyBtn;
    private Button saveBtn;
    private TextView inhaleTitle;
    private LinearLayout exhaleLayout;

    boolean participantMode;
    boolean paired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        mainLayout = (RelativeLayout)findViewById(R.id.mainView);
        if (participantMode = getIntent().getExtras().getBoolean("participantMode", false)) {
            mainLayout.removeView(findViewById(R.id.participant_settings_checkbox));
            ((LinearLayout)findViewById(R.id.button_layout)).removeView(findViewById(R.id.copy_btn));
        } else {
            participantSettingsEnabled = findViewById(R.id.participant_settings_checkbox);
            participantSettingsEnabled.setChecked(UserData.getParticipantSettingsEnabled(SettingsActivity.this));
            copyBtn = findViewById(R.id.copy_btn);
            copyBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveSettings(true);
                    Toast.makeText(getApplicationContext(), R.string.copy_success_msg, Toast.LENGTH_SHORT).show();
                }
            });
        }
        inSec = findViewById(R.id.breath_in_sec);
        outSec = findViewById(R.id.breath_out_sec);
        sessionMin = findViewById(R.id.session_length_min);
        inOutPaired = findViewById(R.id.pair_checkbox);
        saveBtn = findViewById(R.id.save_btn);
        inhaleTitle = findViewById(R.id.inhale_title);
        exhaleLayout = findViewById(R.id.exhale_layout);
        if (paired = UserData.getInOutPaired(participantMode, SettingsActivity.this)) {
            inOutPaired.setChecked(true);
            toggleExhaleSettings(false);
        }
        inSec.setText(String.valueOf(UserData.getBRIn(participantMode, SettingsActivity.this)));
        outSec.setText(String.valueOf(UserData.getBROut(participantMode, SettingsActivity.this)));
        sessionMin.setText(String.valueOf(UserData.getSessionLength(participantMode, SettingsActivity.this)));
        inOutPaired.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                toggleExhaleSettings(!inOutPaired.isChecked());
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings(participantMode);
                startActivity(new Intent(SettingsActivity.this, PickerActivity.class));
                finish();
            }
        });
    }

    private void toggleExhaleSettings(boolean enable) {
        if (enable) {
            mainLayout.addView(exhaleLayout);
            inhaleTitle.setText(R.string.in_title);
        } else {
            mainLayout.removeView(exhaleLayout);
            inhaleTitle.setText(R.string.both_title);
        }
    }

    private void saveSettings(boolean forParticipant) {
        Float breathIn = Float.parseFloat(inSec.getText().toString());
        Float breathOut = inOutPaired.isChecked() ? breathIn : Float.parseFloat(outSec.getText().toString());
        int sessionLength = Integer.parseInt(sessionMin.getText().toString());
        UserData.setBreathingConfig(forParticipant, breathIn, breathOut, sessionLength, inOutPaired.isChecked(), forParticipant ? true : participantSettingsEnabled.isChecked(), SettingsActivity.this);
    }
}
