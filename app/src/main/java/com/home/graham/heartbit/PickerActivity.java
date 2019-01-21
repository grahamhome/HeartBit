package com.home.graham.heartbit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PickerActivity extends AppCompatActivity {

    private Button settingsBtn;

    // Inhale & exhale durations for initial trial runs
    static final List<Float> trialValues = Arrays.asList(6.5f, 6f, 5.5f, 5f, 4.5f);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check if intro/demographics/info activities need to be viewed
        if (!UserData.getDemographicsEntered(PickerActivity.this)) {
            startActivity(new Intent(PickerActivity.this, IntroActivity.class));
            finish();
        } else if (!UserData.getInstructionsViewed(PickerActivity.this)) {
            startActivity(new Intent(PickerActivity.this, InfoActivity.class));
            finish();
        } else {
            setContentView(R.layout.activity_picker);

            int numTrials = UserData.getNumberOfTrials(PickerActivity.this);
            if (numTrials < trialValues.size()) {
                // Use researcher settings to configure breathing rate, then go straight to Breathing Coach
                UserData.setBreathingConfig(false, trialValues.get(numTrials),
                        trialValues.get(numTrials),
                        UserData.getSessionLength(false, PickerActivity.this),
                        true, false, PickerActivity.this);
                startActivity(new Intent(PickerActivity.this, BreathingCoach.class));
                finish();
            } else if (numTrials == trialValues.size()){
                UserData.setBreathingConfig(false,
                        UserData.getBRIn(false, PickerActivity.this),
                        UserData.getBROut(false, PickerActivity.this),
                        UserData.getSessionLength(false, PickerActivity.this),
                        true, true, PickerActivity.this);
            }

            findViewById(R.id.participant_activity_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(PickerActivity.this, BreathingCoach.class));
                    finish();
                }
            });

            (settingsBtn = findViewById(R.id.settings_button)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent settingsActivityIntent = new Intent(PickerActivity.this, SettingsActivity.class);
                    settingsActivityIntent.putExtra("participantMode", true);
                    startActivity(settingsActivityIntent);
                    finish();
                }
            });
            if (!UserData.getParticipantSettingsEnabled(PickerActivity.this)) {
                settingsBtn.setEnabled(false);
                settingsBtn.setBackground(getDrawable(R.drawable.button_inactive));
            }

            ((TextView) findViewById(R.id.title)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder passwordPrompt = new AlertDialog.Builder(PickerActivity.this);
                    passwordPrompt.setTitle(R.string.password_prompt_title);
                    passwordPrompt.setMessage(R.string.password_prompt_message);
                    final EditText passwordField = new EditText(PickerActivity.this);
                    passwordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordPrompt.setView(passwordField);
                    passwordPrompt.setNegativeButton(R.string.cancel_btn_text, null);
                    passwordPrompt.setPositiveButton(R.string.enter_btn_text, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (passwordField.getText().toString().equals(getString(R.string.top_secret_password))) {
                                Intent settingsIntent = new Intent(PickerActivity.this, SettingsActivity.class);
                                settingsIntent.putExtra("participantMode", false);
                                startActivity(settingsIntent);
                                finish();
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
}
