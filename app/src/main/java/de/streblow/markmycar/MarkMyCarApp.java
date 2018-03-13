package de.streblow.markmycar;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.renderer.MapWorkerPool;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;


/**
 * Created by streblow on 09.03.2018.
 */

public class MarkMyCarApp extends Application {
    public static final String TAG = "Mark My Car";

    /*
     * type to use for maps to store in the external files directory
     */
    public static final String MAPS = "maps";

    public static final String SETTING_DEBUG_TIMING = "debug_timing";
    public static final String SETTING_SCALE = "scale";
    public static final String SETTING_WAYFILTERING = "wayfiltering";
    public static final String SETTING_WAYFILTERING_DISTANCE = "wayfiltering_distance";

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidGraphicFactory.createInstance(this);
        Log.e(TAG,
                "Device scale factor "
                        + Float.toString(DisplayModel.getDeviceScaleFactor()));
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(this);
        float fs = Float.valueOf(preferences.getString(SETTING_SCALE,
                Float.toString(DisplayModel.getDefaultUserScaleFactor())));
        Log.e(TAG, "User ScaleFactor " + Float.toString(fs));
        if (fs != DisplayModel.getDefaultUserScaleFactor()) {
            DisplayModel.setDefaultUserScaleFactor(fs);
        }

        MapFile.wayFilterEnabled = preferences.getBoolean(SETTING_WAYFILTERING, true);
        if (MapFile.wayFilterEnabled) {
            MapFile.wayFilterDistance = Integer.parseInt(preferences.getString(SETTING_WAYFILTERING_DISTANCE, "20"));
        }
        MapWorkerPool.DEBUG_TIMING = preferences.getBoolean(SETTING_DEBUG_TIMING, false);
    }
}
