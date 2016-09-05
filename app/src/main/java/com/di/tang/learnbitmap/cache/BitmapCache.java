package com.di.tang.learnbitmap.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;

/**
 * Created by tangdi on 2016/9/5.
 */
public class BitmapCache {

    private static BitmapCache instance = null;

    private Context context;

    private BitmapCache(Context context){
        this.context = context;
    }

    public static BitmapCache getInstance(Context context){
        if(instance == null){
            instance = new BitmapCache(context);
            return instance;
        }
        return instance;
    }

    private int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);

    private int cacheSize = maxMemory / 8;

    private LruCache mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
        @Override
        protected int sizeOf(String key, Bitmap bitmap){
            return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {

        }
    };

    public void addBitmapToMemoryCache(String key, Bitmap bitmap){
        mMemoryCache.put(key, bitmap);
    }

}
