package com.home.graham.heartbit;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class InfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        findViewById(R.id.ok_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UserData.setIntructionsViewed(true, InfoActivity.this);
                startActivity(new Intent(InfoActivity.this, PickerActivity.class));
            }
        });
    }


}
