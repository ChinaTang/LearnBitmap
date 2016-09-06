package com.di.tang.learnbitmap.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import com.di.tang.learnbitmap.R;
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
import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;

/**
 * Created by tangdi on 2016/9/5.
 */
public class ImageLoad {

    private static final String TAG = "ImageLoad";

    private static ImageLoad instance = null;

    /**
     * About Thread
     */
    public static final int MESSAGE_POST_RESULT = 1;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;

    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;

    private static final long KEEP_ALIVE = 10L;

    private static final int TAG_KEY_URI = R.id.imageload_uri;

    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    private static final int IO_BUFF_SIZE = 8 * 1024;

    private static final int DISK_CACHE_INDEX = 0;

    private boolean mIsDiskLruCacheCreated = false;
    /**
     * About cache
     */
    private Context context;

    private LruCache<String, Bitmap> mMemoryCache;

    private DiskLruCache mDiskLruCache;

    /**
     * About Thread
     * ThreadPoolExecutor
     */
    private static final ThreadFactory mThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor
            (CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS,
                    new LinkedBlockingDeque<Runnable>(), mThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg){
            LoaderResult loaderResult = (LoaderResult) msg.obj;
            ImageView imageView = loaderResult.imageView;
            String uri = (String)imageView.getTag(TAG_KEY_URI);
            if(uri.equals(loaderResult.url)){
                imageView.setImageBitmap(loaderResult.bitmap);
            }else{
                Log.w(TAG, "handleMessage: " +
                        "set image bitmap, but, url has change, ignored");
            }
        }
    };

    /**
     * About Cache
     * MemoryCache
     * DiskCache
     */
    private ImageLoad(Context context){
        this.context = context;
        int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap bitmap){
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(context, "bitmap");
        if(!diskCacheDir.exists()){
            diskCacheDir.mkdirs();
        }
        if(getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE){
            try{
                mDiskLruCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            }catch (IOException e){
                Log.e(TAG, "ImageLoad: " + e.toString());
            }
        }
    }

    public static ImageLoad getInstnace(Context context){
        if(instance == null){
            instance = new ImageLoad(context);
        }
        return instance;
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap){
        if(getBitmapFromMemCache(key) == null){
            mMemoryCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key){
        return mMemoryCache.get(key);
    }

    private static File getDiskCacheDir(Context context , String path){
        /*boolean externalStorageAvailable = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED);
        String cachePath;
        if(externalStorageAvailable){
            cachePath = context.getExternalCacheDir().getPath();
        }else{
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + path);*/
        File cacheFile = context.getExternalFilesDir(path);
        return cacheFile;
    }

    private static long getUsableSpace(File path){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD){
            return path.getUsableSpace();
        }
        final StatFs stats = new StatFs(path.getPath());
        return stats.getBlockSizeLong() * stats.getAvailableBlocksLong();
    }


    /**
     *If the bitmap exists in the memory of the direct loading,
     * if you do not exist on the SD card memory.
     * load bitmap
     */

    public void bindBitmap(final String Url, final ImageView imageView,
                           final int reqWidth, final int reqHeight){
        imageView.setTag(TAG_KEY_URI, Url);
        Bitmap bitmap = loadBitmapFromMemCache(Url);
        if(bitmap != null){
            imageView.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(Url, reqWidth, reqHeight);
                if(bitmap != null){
                    LoaderResult result = new LoaderResult(imageView, Url, bitmap);
                    Message message = mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result);
                    mMainHandler.sendMessage(message);
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    public Bitmap loadBitmap(String url, int reqWidth, int reqHeight){
        Bitmap bitmap = loadBitmapFromMemCache(url);
        if(bitmap != null){
            return bitmap;
        }

        bitmap = loadBitmapFromDiskCache(url, reqWidth, reqHeight);
        if(bitmap != null){
            return bitmap;
        }
        try{
            bitmap = loadBitmapFromHttp(url, reqWidth, reqHeight);
        }catch(IOException e){
            Log.e(TAG, "loadBitmap: " + e.toString());
        }
        if(bitmap == null && !mIsDiskLruCacheCreated){
            bitmap = loadBitmapFormURL(url);
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url, int reqWidth, int reqHeight) throws IOException{
        if(Looper.getMainLooper() == Looper.myLooper()){
            Log.w(TAG, "loadBitmapFromHttp: " + "down Load Bitmap From HTTP!!!" );
        }
        if(mDiskLruCache == null){
            return null;
        }
        String key = hashKeyFormUrl(url);
        DiskLruCache.Editor edotor = mDiskLruCache.edit(key);
        if(edotor != null){
            OutputStream outPutStream = edotor.newOutputStream(DISK_CACHE_INDEX);
            if(downloadUrlToStream(url, outPutStream)){
                edotor.commit();
            }else{
                edotor.abort();
            }
            mDiskLruCache.flush();
        }
        return  loadBitmapFromDiskCache(url, reqWidth, reqHeight);
    }

    /**
     * diskCache not invalive;
     * @return
     */
    private Bitmap loadBitmapFormURL(String UrlString){
        HttpURLConnection urlConnection = null;
        BufferedInputStream buffInputStream = null;
        Bitmap bitmap = null;
        try {
            URL url = new URL(UrlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            buffInputStream = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFF_SIZE);
            bitmap = BitmapFactory.decodeStream(buffInputStream);
        }catch (IOException e){
            Log.e(TAG, "loadBitmapFormURL: " + e.toString());
        }finally {
            if(urlConnection != null){
                urlConnection.disconnect();
            }
            MyUtils.close(buffInputStream);
        }
        return bitmap;
    }


    private Bitmap loadBitmapFromDiskCache(String url, int reqWidth, int reqHeight){
        if(Looper.getMainLooper() == Looper.myLooper()){
            Log.w(TAG, "loadBitmap: " + "load Bitmap in UI Thread !!!" );
        }
        if(mDiskLruCache == null){
            return null;
        }
        Bitmap bitmap = null;
        FileInputStream fileInputStream = null;
        final String key = hashKeyFormUrl(url);
        try{

            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(key);
            if(snapshot != null){
                fileInputStream = (FileInputStream)snapshot
                        .getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fileDescriptor = fileInputStream.getFD();
                bitmap = BitmapOptimised
                        .decodeSampledBitmapFromFileDescriptor(fileDescriptor, reqWidth, reqHeight);
                if(bitmap != null){
                    addBitmapToMemoryCache(key, bitmap);
                }
            }
        }catch(IOException e){
            Log.e(TAG, "loadBitmapFromDiskCache: " + e.toString());
        }finally {
            if(fileInputStream != null){
                MyUtils.close(fileInputStream);
            }
        }
        return bitmap;
    }

    public boolean downloadUrlToStream(String url, OutputStream outPutStream){
        HttpURLConnection urlConnection = null;
        BufferedOutputStream buffOutStream = null;
        BufferedInputStream buffInputStream = null;
        try{
            URL Url = new URL(url);
            urlConnection = (HttpURLConnection) Url.openConnection();
            buffInputStream = new BufferedInputStream(urlConnection.getInputStream(), IO_BUFF_SIZE);
            buffOutStream = new BufferedOutputStream(outPutStream, IO_BUFF_SIZE);
            int b;
            while((b = buffInputStream.read()) != -1){
                buffOutStream.write(b);
            }
            return true;
        }catch (IOException e){
            Log.e(TAG, "downloadUrlToStream: " + e.toString());
        }finally {
            if(urlConnection != null){
                urlConnection.disconnect();
            }
            MyUtils.close(buffInputStream);
            MyUtils.close(buffOutStream);
        }
        return false;
    }

    private Bitmap loadBitmapFromMemCache(String url){
        final String key = hashKeyFormUrl(url);
        Bitmap bitmap = mMemoryCache.get(key);
        return bitmap;
    }

    /**
     * URL To MD5
     * @param url
     * @return
     */
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

    public class LoaderResult{
        ImageView imageView;
        String url;
        Bitmap bitmap;
        public LoaderResult(ImageView image, String url, Bitmap bitmap){
            imageView = image;
            this.url = url;
            bitmap = bitmap;
        }
    }


}
