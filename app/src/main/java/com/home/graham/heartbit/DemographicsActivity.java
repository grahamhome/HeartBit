package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Spinner;

public class DemographicsActivity extends AppCompatActivity {

    private EditText dobField;
    private EditText weightField;
    private Spinner genderField;
    private Spinner raceField;
    private Spinner smokerField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demographics);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        dobField = findViewById(R.id.dob);
        weightField = findViewById(R.id.weight);
        genderField = findViewById(R.id.gender);
        raceField = findViewById(R.id.race);
        smokerField = findViewById(R.id.smoker);

        findViewById(R.id.submit_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UserData.setDemographicData(DemographicsActivity.this, dobField.getText().toString(),
                        weightField.getText().toString(), genderField.getSelectedItem().toString(),
                        raceField.getSelectedItem().toString(), smokerField.getSelectedItem().toString());
                startActivity(new Intent(DemographicsActivity.this, PickerActivity.class));
                finish();
            }

        });
    }
}
