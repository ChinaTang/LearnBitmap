package com.di.tang.learnbitmap.utils;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by tangdi on 2016/9/5.
 */
public class MyUtils  {

    private static final String TAG = "MyUtils";

    public static void close(Closeable closeable){
        if(closeable != null){
            return;
        }
        try{
            closeable.close();
        }catch (IOException e){
            Log.d(TAG, "close: " + e.toString());
        }
    }
}
