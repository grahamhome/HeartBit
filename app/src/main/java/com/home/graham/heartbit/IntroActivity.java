package com.home.graham.heartbit;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

public class IntroActivity extends AppCompatActivity {

    private EditText nameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        nameField = (EditText) findViewById(R.id.name_field);

        findViewById(R.id.start_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = nameField.getText().toString();
                if (name.length() == 0) {
                    nameField.setError(getString(R.string.name_field_error));
                } else {
                    UserData.setName(name, IntroActivity.this);
                    UserData.setIntroViewed(true, IntroActivity.this);
                    if (name.equals(getString(R.string.researcher_name))) {
                        Toast.makeText(getApplicationContext(), getString(R.string.researcher_mode_unlock_message), Toast.LENGTH_SHORT).show();
                    }
                    startActivity(new Intent(IntroActivity.this, PickerActivity.class));
                }
            }
        });
    }


}
