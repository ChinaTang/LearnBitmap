package com.di.tang.learnbitmap.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.di.tang.learnbitmap.bithmap.BitmapOptimised;
import com.di.tang.learnbitmap.utils.MyUtils;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by tangdi on 2016/9/5.
 */
public class BitmapDiskCache {

    private static final String TAG = "BitmapDiskCache";

    private static final int DISK_CACHE_INDEX = 0;

    private static BitmapDiskCache instance = null;

    private DiskLruCache mDiskLruCache;

    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    private BitmapCache bitmapCache;


    private Context context;

    private BitmapDiskCache(Context context)throws IOException{
        this.context = context;
        File disCacheDir = getDiskCacheDir(context, "bitmap");
        mDiskLruCache = DiskLruCache.open(disCacheDir, 1, 1, DISK_CACHE_SIZE);
        bitmapCache = BitmapCache.getInstance(context);
    }

    public static BitmapDiskCache getInstacne(Context context) throws IOException{
        if(instance == null){
            instance = new BitmapDiskCache(context);
            return instance;
        }
        return instance;
    }

    private static File getDiskCacheDir(Context context , String path){
        File cacheFile = context.getExternalFilesDir(path);
        return cacheFile;
    }

    private File diskCacheDir = getDiskCacheDir(context, "bitmap");

    private String hashKeyFormUrl(String url){
        String cacheKey;
        try{
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(url.getBytes());
            cacheKey = bytesToHexString(messageDigest.digest());
        }catch (NoSuchAlgorithmException e){
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes){
        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0; i < bytes.length; i++){
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if(hex.length() == 1){
                stringBuilder.append('0');
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }

    public void cachBitmap(URL url)throws IOException{
        String key = hashKeyFormUrl(url.toString());
        DiskLruCache.Editor editor = mDiskLruCache.edit(key);
        if(editor != null){
            OutputStream outputStream = editor.newOutputStream(DISK_CACHE_INDEX);
            if(downloadUrlToStream(url.toString(), outputStream)){
                editor.commit();
            }else{
                editor.abort();
            }
        }
        mDiskLruCache.flush();
    }

    private boolean downloadUrlToStream(String urlString, OutputStream outputStream){
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try{
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream());
            out = new BufferedOutputStream(outputStream);
            int b;
            while((b = in.read()) != -1){
                out.write(b);
            }
            return true;
        }catch (IOException e){
            Log.e(TAG, "downloadUrlToStream: " + e.toString());
        }finally {
            if(urlConnection != null){
                urlConnection.disconnect();
            }
            MyUtils.close(in);
            MyUtils.close(out);
        }
        return false;
    }

    public Bitmap loadCache(URL url, int reqWidth, int reqHeight )throws IOException{
        String key = hashKeyFormUrl(url.toString());
        DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
        Bitmap bitmap = null;
        if(snapshot != null){
            FileInputStream in = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
            FileDescriptor descriptor = in.getFD();
            bitmap = BitmapOptimised.decodeSampledBitmapFromFileDescriptor(descriptor,
                    reqWidth, reqHeight);
            return bitmap;
        }
        if(bitmap != null){
            bitmapCache.addBitmapToMemoryCache(key, bitmap);
        }

        return null;
    }
}
