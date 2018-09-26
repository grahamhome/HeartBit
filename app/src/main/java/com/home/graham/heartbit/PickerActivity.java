package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class PickerActivity extends AppCompatActivity {

    private static final boolean RESEARCHER_MODE_ENABLED = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!RESEARCHER_MODE_ENABLED) {
            startActivity(new Intent(PickerActivity.this, BreathingCoach.class));
            finish();
        } else {
            setContentView(R.layout.activity_picker);
            findViewById(R.id.participant_activity_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(PickerActivity.this, BreathingCoach.class));
                    finish();
                }
            });

            findViewById(R.id.researcher_activity_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(PickerActivity.this, GraphActivity.class));
                    finish();
                }
            });
        }
    }
}
