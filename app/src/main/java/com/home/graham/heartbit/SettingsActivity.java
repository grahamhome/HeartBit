package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
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
    private EditText inMs;
    private EditText outSec;
    private EditText outMs;
    private EditText sessionMin;
    private EditText sessionSec;
    private CheckBox inOutPaired;
    private CheckBox participantSettingsEnabled;
    private Button copyBtn;
    private Button saveBtn;
    private TextView inhaleTitle;
    private TextView exhaleTitle;
    private LinearLayout exhaleLayout;

    boolean participantMode;
    boolean paired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
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
        inMs = findViewById(R.id.breath_in_ms);
        outSec = findViewById(R.id.breath_out_sec);
        outMs = findViewById(R.id.breath_out_ms);
        sessionMin = findViewById(R.id.session_length_min);
        sessionSec = findViewById(R.id.session_length_sec);
        inOutPaired = findViewById(R.id.pair_checkbox);
        saveBtn = findViewById(R.id.save_btn);
        inhaleTitle = findViewById(R.id.inhale_title);
        exhaleTitle = findViewById(R.id.exhale_title);
        exhaleLayout = findViewById(R.id.exhale_layout);
        if (paired = UserData.getInOutPaired(participantMode, SettingsActivity.this)) {
            inOutPaired.setChecked(true);
            toggleExhaleSettings(false);
        }
        inSec.setText(String.valueOf(UserData.getBRIn(participantMode, SettingsActivity.this)/1000));
        inMs.setText(String.valueOf(UserData.getBRIn(participantMode, SettingsActivity.this)%1000));
        outSec.setText(String.valueOf(UserData.getBROut(participantMode, SettingsActivity.this)/1000));
        outMs.setText(String.valueOf(UserData.getBROut(participantMode, SettingsActivity.this)%1000));
        sessionMin.setText(String.valueOf(UserData.getSessionLength(participantMode, SettingsActivity.this)/60000));
        sessionSec.setText(String.valueOf((UserData.getSessionLength(participantMode, SettingsActivity.this)%60000)/1000));
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
                Intent nextActivityIntent = new Intent(SettingsActivity.this, BreathingCoach.class);
                nextActivityIntent.putExtra("fromSettings", true);
                startActivity(nextActivityIntent);
                finish();
            }
        });
    }

    private void toggleExhaleSettings(boolean enable) {
        if (enable) {
            mainLayout.addView(exhaleTitle);
            mainLayout.addView(exhaleLayout);
            inhaleTitle.setText(R.string.in_title);
        } else {
            mainLayout.removeView(exhaleTitle);
            mainLayout.removeView(exhaleLayout);
            inhaleTitle.setText(R.string.both_title);
        }
    }

    private void saveSettings(boolean forParticipant) {
        int breathIn = Integer.parseInt(inSec.getText().toString())*1000 + Integer.parseInt(inMs.getText().toString());
        int breathOut = inOutPaired.isChecked() ? breathIn : Integer.parseInt(outSec.getText().toString())*1000 + Integer.parseInt(outMs.getText().toString());
        int sessionLength = Integer.parseInt(sessionMin.getText().toString())*60000 + Integer.parseInt(sessionSec.getText().toString())*1000;
        UserData.setBreathingConfig(forParticipant, breathIn, breathOut, sessionLength, inOutPaired.isChecked(), forParticipant ? true : participantSettingsEnabled.isChecked(), SettingsActivity.this);
    }
}
