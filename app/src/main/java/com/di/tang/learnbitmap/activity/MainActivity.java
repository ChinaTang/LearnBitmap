package com.di.tang.learnbitmap.activity;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.di.tang.learnbitmap.R;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static int i = 0;

    Handler handler = new Handler(){

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        i++;
        Log.e(TAG, "onCreate: " + i );
        Intent intent = new Intent(MainActivity.this, MainActivity2.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.e(TAG, "onDestroy: " + i);
    }
}
