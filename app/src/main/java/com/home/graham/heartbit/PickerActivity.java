package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class PickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check if intro/info activities need to be viewed
        if (!UserData.getIntroViewed(PickerActivity.this)) {
            startActivity(new Intent(PickerActivity.this, IntroActivity.class));
            finish();
        } else if (!UserData.getInstructionsViewed(PickerActivity.this)) {
            startActivity(new Intent(PickerActivity.this, InfoActivity.class));
            finish();
        } else {
            if (!UserData.getName(PickerActivity.this).equals(getString(R.string.researcher_name))) {
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
}
