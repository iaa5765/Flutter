/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.flutter;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StrictMode;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import org.floens.flutter.core.cache.FileCache;
import org.floens.flutter.core.database.DatabaseManager;
import org.floens.flutter.core.http.ReplyManager;
import org.floens.flutter.core.manager.BoardManager;
import org.floens.flutter.core.manager.WatchManager;
import org.floens.flutter.core.net.BitmapLruImageCache;
import org.floens.flutter.core.net.ProxiedHurlStack;
import org.floens.flutter.utils.AndroidUtils;
import org.floens.flutter.utils.Logger;
import org.floens.flutter.utils.Time;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;


import de.greenrobot.event.EventBus;

public class Chan extends Application {
    private static final String TAG = "ChanApplication";

    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;

    public static Context con;

    private static Chan instance;
    private static RequestQueue volleyRequestQueue;
    private static ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static WatchManager watchManager;
    private static ReplyManager replyManager;
    private static DatabaseManager databaseManager;
    private static FileCache fileCache;

    private String userAgent;
    private String userID;
    private int activityForegroundCounter = 0;

    public Chan() {
        instance = this;
        con = this;
    }

    public static Chan getInstance() {
        return instance;
    }

    public static RequestQueue getVolleyRequestQueue() {
        return volleyRequestQueue;
    }

    public static ImageLoader getVolleyImageLoader() {
        return imageLoader;
    }

    public static BoardManager getBoardManager() {
        return boardManager;
    }

    public static WatchManager getWatchManager() {
        return watchManager;
    }

    public static ReplyManager getReplyManager() {
        return replyManager;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static FileCache getFileCache() {
        return fileCache;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final long startTime = Time.startTiming();

        AndroidUtils.init();

        userAgent = createUserAgent();

        File cacheDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();

        userID = fetchUserID();
        replyManager = new ReplyManager(this, userAgent, userID);

        volleyRequestQueue = Volley.newRequestQueue(this, userAgent, new ProxiedHurlStack(userAgent), new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);

        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;

        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(lruImageCacheSize));

        fileCache = new FileCache(new File(cacheDir, FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE, getUserAgent());

        databaseManager = new DatabaseManager(this);
        boardManager = new BoardManager(databaseManager);
        watchManager = new WatchManager(databaseManager);

        Time.endTiming("Initializing application", startTime);

        // Start watching for slow disk reads and writes after the heavy initializing is done
        if (ChanBuild.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder()
                            .detectCustomSlowCalls()
                            .detectNetwork()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .penaltyLog()
                            .build());
            StrictMode.setVmPolicy(
                    new StrictMode.VmPolicy.Builder()
                            .detectAll()
                            .penaltyLog()
                            .build());

            //noinspection PointlessBooleanExpression
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf(TAG, "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
    }

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }

    private String fetchUserID() {
        String hexDigits = "0123456789abcdef";
        String temp = "";

        Random gen = new Random();

        for (int i = 0; i < 32; i++)
        {
            int rand = gen.nextInt(hexDigits.length());
            temp += hexDigits.substring(rand, rand+1);
        }

        System.out.println("UID : " + temp);
        return temp;
    }

    private String createUserAgent() {
        // User agent is <appname>/<version>
        String version = "Unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Logger.e(TAG, "Error getting app version", e);
        }
        version = version.toLowerCase(Locale.ENGLISH).replace(" ", "_");
        return getString(R.string.app_name) + "/" + version;
    }
}
