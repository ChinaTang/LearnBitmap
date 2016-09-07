package com.di.tang.learnbitmap.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.di.tang.learnbitmap.R;

/**
 * Created by tangdi on 2016/9/7.
 */
public class MainActivity2 extends AppCompatActivity {

    private static final String TAG = "MainActivity2";

    private static int i = 0;

    Handler handler = new Handler(){

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        i++;
        Log.e(TAG, "onCreate: " + i );
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.e(TAG, "onDestroy: " + i);
    }

}
