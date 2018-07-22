package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class IntroActivity extends AppCompatActivity {

    private EditText nameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        nameField = (EditText) findViewById(R.id.nameField);

        findViewById(R.id.startBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = nameField.getText().toString();
                if (name.length() == 0) {
                    nameField.setError(getString(R.string.name_field_error));
                } else {
                    UserData.setName(name, IntroActivity.this);
                    UserData.setIntroViewed(true, IntroActivity.this);
                    startActivity(new Intent(IntroActivity.this, BreathingCoach.class));
                }
            }
        });
    }


}
