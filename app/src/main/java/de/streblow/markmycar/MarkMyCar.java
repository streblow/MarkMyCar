package de.streblow.markmycar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Dimension;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.util.LatLongUtils;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.layers.MyLocationOverlay;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.util.MapViewerTemplate;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;

import java.io.File;

/**
 * Created by streblow on 05.03.18.
 */

public class MarkMyCar extends MapViewerTemplate implements LocationListener {

    private static final Paint TRANSPARENT = Utils.createPaint(
            AndroidGraphicFactory.INSTANCE.createColor(Color.TRANSPARENT), 0,
            Style.FILL);

    private static Paint getPaint(int color, int strokeWidth, Style style) {
        Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(style);
        return paint;
    }

    private LocationManager locationManager;
    private MyLocationOverlay myLocationOverlay;

    private String mapfile_path = "";
    private LatLong mycar_location = null;
    private LatLong mycar_location_backup = null;
    private Boolean mycar_location_set = false;
    private Boolean mycar_location_backup_set = false;
    private FixedPixelCircle tappableCircle = null;

    private Menu main_menu = null;
    private TileRendererLayer tileRendererLayer = null;
    private Boolean opt_follow_location = true;

    private Location curr_location = null;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        main_menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.main_menu_follow_position).setChecked(opt_follow_location);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.main_menu_follow_position:
                opt_follow_location = !opt_follow_location;
                item.setChecked(opt_follow_location);
                return true;
            case R.id.main_menu_set_position:
                if (curr_location != null) {
                    onLongPress(new LatLong(curr_location.getLatitude(), curr_location.getLongitude()));
                }
                return true;
            case R.id.main_menu_center_on_position:
                opt_follow_location = false;
                main_menu.findItem(R.id.main_menu_follow_position).setChecked(false);
                if (curr_location != null)
                    this.mapView.setCenter(new LatLong(curr_location.getLatitude(), curr_location.getLongitude()));
                return true;
            case R.id.main_menu_center_on_mycarpoint:
                opt_follow_location = false;
                main_menu.findItem(R.id.main_menu_follow_position).setChecked(false);
                if (mycar_location_set)
                    this.mapView.setCenter(new LatLong(mycar_location.getLatitude(), mycar_location.getLongitude()));
                return true;
            case R.id.main_menu_restore_position:
                if (mycar_location_backup_set) {
                    onLongPress(mycar_location_backup);
                    mycar_location_backup = null;
                    mycar_location_backup_set = false;
                }
                return true;
            case R.id.main_menu_open_map_file:
                SimpleFileDialog FileOpenDialog = new SimpleFileDialog(this, "FileOpen",
                        new SimpleFileDialog.SimpleFileDialogListener() {
                            @Override
                            public void onChosenDir(String chosenFile) {
                                // The code in this function will be executed when the dialog OK button is pushed
                                if (chosenFile != "") {
                                    mapfile_path = chosenFile.replace("//", "/");
                                    MarkMyCar.this.updateMapLayer();
                                }
                            }
                        });
                //You can change the default filename using the public variable "Default_File_Name"
                FileOpenDialog.Default_File_Name = "";
                FileOpenDialog.chooseFile_or_Dir();
                return true;
            case R.id.main_menu_help:
                HelpDialog help = new HelpDialog(this);
                help.setTitle(R.string.help_title);
                help.show();
                return true;
            case R.id.main_menu_about:
                AboutDialog about = new AboutDialog(this);
                about.setTitle(R.string.about_title);
                about.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This MapViewer uses the built-in Osmarender theme.
     *
     * @return the render theme to use
     */
    @Override
    protected XmlRenderTheme getRenderTheme() {
        return InternalRenderTheme.OSMARENDER;
    }

    /**
     * This MapViewer uses the standard xml layout in the Samples app.
     */
    @Override
    protected int getLayoutId() {
        return R.layout.mapviewer;
    }

    /**
     * The id of the mapview inside the layout.
     *
     * @return the id of the MapView inside the layout.
     */
    @Override
    protected int getMapViewId() {
        return R.id.mapView;
    }

    @Override
    protected String getMapFileName() {
        return "";
    }

    @Override
    protected MapDataStore getMapFile() {
        //return new MapFile(new File("/storage/9016-4EF8/Maps", this.getMapFileName()));
        //return new MapFile(new File("/storage/sdcard", this.getMapFileName()));
        if (mapfile_path != "")
            return new MapFile(new File(mapfile_path));
        else
            return null;
    }

    /**
     * Creates a simple tile renderer layer with the AndroidUtil helper.
     */
    @Override
    protected void createLayers() {
        restorePreferences();
        if (mapfile_path == "")
            Toast.makeText(this, getString(R.string.open_map_file),
                    Toast.LENGTH_LONG).show();
        else {
            tileRendererLayer = new TileRendererLayer(
                    this.tileCaches.get(0), getMapFile(),
                    this.mapView.getModel().mapViewPosition,
                    false, true, false,
                    org.mapsforge.map.android.graphics.AndroidGraphicFactory.INSTANCE) {
                @Override
                public boolean onLongPress(LatLong tapLatLong, Point thisXY,
                                           Point tapXY) {
                    MarkMyCar.this.onLongPress(tapLatLong);
                    return true;
                }
            };
            tileRendererLayer.setXmlRenderTheme(this.getRenderTheme());
            mapView.getLayerManager().getLayers().add(tileRendererLayer);
        }

        // marker to show at the location
        Drawable drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? getDrawable(R.drawable.ic_maps_indicator_current_position) : getResources().getDrawable(R.drawable.ic_maps_indicator_current_position);
        Marker marker = new Marker(null, AndroidGraphicFactory.convertToBitmap(drawable), 0, 0);

        // circle to show the location accuracy (optional)
        Circle circle = new Circle(null, 0,
                getPaint(AndroidGraphicFactory.INSTANCE.createColor(48, 0, 0, 255), 0, Style.FILL),
                getPaint(AndroidGraphicFactory.INSTANCE.createColor(160, 0, 0, 255), 2, Style.STROKE));

        // create the overlay
        this.myLocationOverlay = new MyLocationOverlay(marker, circle);
        this.mapView.getLayerManager().getLayers().add(this.myLocationOverlay);
    }

    @Override
    protected void createMapViews() {
        super.createMapViews();
    }

    /**
     * Creates the tile cache with the AndroidUtil helper
     */
    @Override
    protected void createTileCaches() {
        this.tileCaches.add(AndroidUtil.createTileCache(this, getPersistableId(),
                this.mapView.getModel().displayModel.getTileSize(), this.getScreenRatio(),
                this.mapView.getModel().frameBufferModel.getOverdrawFactor()));
    }

    @Override
    protected MapPosition getInitialPosition() {
        int tileSize = this.mapView.getModel().displayModel.getTileSize();
        if (getMapFile() != null) {
            byte zoomLevel = LatLongUtils.zoomForBounds(new Dimension(tileSize * 4, tileSize * 4), getMapFile().boundingBox(), tileSize);
            return new MapPosition(getMapFile().boundingBox().getCenterPoint(), zoomLevel);
        } else
            return new MapPosition(new LatLong(0.0, 0.0), getZoomLevelDefault());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getClass().getSimpleName());

        this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (mycar_location_set) {
            savedInstanceState.putDouble("LATITUDE", mycar_location.getLatitude());
            savedInstanceState.putDouble("LONGITUDE", mycar_location.getLongitude());
        } else {
            savedInstanceState.putDouble("LATITUDE", 0.0);
            savedInstanceState.putDouble("LONGITUDE", 0.0);
        }
        savedInstanceState.putBoolean("LOCATIONSET", mycar_location_set);
        if (mycar_location_backup_set) {
            savedInstanceState.putDouble("LATITUDEBACKUP", mycar_location_backup.getLatitude());
            savedInstanceState.putDouble("LONGITUDEBACKUP", mycar_location_backup.getLongitude());
        } else {
            savedInstanceState.putDouble("LATITUDEBACKUP", 0.0);
            savedInstanceState.putDouble("LONGITUDEBACKUP", 0.0);
        }
        savedInstanceState.putBoolean("LOCATIONBACKUPSET", mycar_location_backup_set);
        savedInstanceState.putBoolean("OPTFOLLOWLOCATION", opt_follow_location);

        savedInstanceState.putString("MAPFILEPATH", mapfile_path);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        double lat = savedInstanceState.getDouble("LATITUDE", 0.0);
        double lon = savedInstanceState.getDouble("LONGITUDE", 0.0);
        mycar_location_set = savedInstanceState.getBoolean("LOCATIONSET", false);
        if (!mycar_location_set)
            mycar_location = null;
        else
            mycar_location = new LatLong(lat, lon);
        double latbackup = savedInstanceState.getDouble("LATITUDEBACKUP", 0.0);
        double lonbackup = savedInstanceState.getDouble("LONGITUDEBACKUP", 0.0);
        mycar_location_backup_set = savedInstanceState.getBoolean("LOCATIONBACKUPSET", false);
        if (!mycar_location_backup_set)
            mycar_location_backup = null;
        else
            mycar_location_backup = new LatLong(latbackup, lonbackup);
        opt_follow_location = savedInstanceState.getBoolean("OPTFOLLOWLOCATION", true);
        mapfile_path = savedInstanceState.getString("MAPFILEPATH", "");
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePreferences();
        enableAvailableProviders();
    }

    @Override
    public void onPause() {
        super.onPause();
        this.locationManager.removeUpdates(this);
        savePreferences();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    public void savePreferences() {
        SharedPreferences settings = getApplicationContext().getSharedPreferences("MarkMyCar", 0);
        SharedPreferences.Editor editor = settings.edit();
        if (mycar_location_set) {
            editor.putInt("LATITUDE", mycar_location.getLatitudeE6());
            editor.putInt("LONGITUDE", mycar_location.getLongitudeE6());
        } else {
            editor.putInt("LATITUDE", 0);
            editor.putInt("LONGITUDE", 0);
        }
        editor.putBoolean("LOCATIONSET", mycar_location_set);
        if (mycar_location_backup_set) {
            editor.putInt("LATITUDEBACKUP", mycar_location_backup.getLatitudeE6());
            editor.putInt("LONGITUDEBACKUP", mycar_location_backup.getLongitudeE6());
        } else {
            editor.putInt("LATITUDEBACKUP", 0);
            editor.putInt("LONGITUDEBACKUP", 0);
        }
        editor.putBoolean("LOCATIONBACKUPSET", mycar_location_backup_set);
        editor.putBoolean("OPTFOLLOWLOCATION", opt_follow_location);
        editor.putString("MAPFILEPATH", mapfile_path);
        editor.apply();
    }

    public void restorePreferences() {
        SharedPreferences settings = getApplicationContext().getSharedPreferences("MarkMyCar", 0);
        double lat = LatLongUtils.microdegreesToDegrees(settings.getInt("LATITUDE", 0));
        double lon = LatLongUtils.microdegreesToDegrees(settings.getInt("LONGITUDE", 0));
        mycar_location_set = settings.getBoolean("LOCATIONSET", false);
        if (!mycar_location_set)
            mycar_location = null;
        else
            mycar_location = new LatLong(lat, lon);
        double latbackup = LatLongUtils.microdegreesToDegrees(settings.getInt("LATITUDEBACKUP", 0));
        double lonbackup = LatLongUtils.microdegreesToDegrees(settings.getInt("LONGITUDEBACKUP", 0));
        mycar_location_backup_set = settings.getBoolean("LOCATIONBACKUPSET", false);
        if (!mycar_location_backup_set)
            mycar_location_backup = null;
        else
            mycar_location_backup = new LatLong(latbackup, lonbackup);
        opt_follow_location = settings.getBoolean("OPTFOLLOWLOCATION", true);
        mapfile_path = settings.getString("MAPFILEPATH", "");
        if (mycar_location_set) {
            LatLong mycar_location_backup_old = mycar_location_backup;
            Boolean mycar_location_backup_set_old = mycar_location_backup_set;
            onLongPress(mycar_location);
            mycar_location_backup = mycar_location_backup_old;
            mycar_location_backup_set = mycar_location_backup_set_old;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        curr_location = location;
        this.myLocationOverlay.setPosition(location.getLatitude(), location.getLongitude(), location.getAccuracy());

        // Follow current location (option opt_follow_location)
        if (opt_follow_location)
            this.mapView.setCenter(new LatLong(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private void updateMapLayer() {
        if (mapfile_path != "") {
            if (tappableCircle != null)
                mapView.getLayerManager().getLayers().remove(tappableCircle);
            if (myLocationOverlay != null)
                mapView.getLayerManager().getLayers().remove(myLocationOverlay);
            if (tileRendererLayer != null)
                mapView.getLayerManager().getLayers().remove(tileRendererLayer);
            tileRendererLayer = new TileRendererLayer(
                    tileCaches.get(0), getMapFile(),
                    mapView.getModel().mapViewPosition,
                    false, true, false,
                    org.mapsforge.map.android.graphics.AndroidGraphicFactory.INSTANCE) {
                @Override
                public boolean onLongPress(LatLong tapLatLong, Point thisXY,
                                           Point tapXY) {
                    MarkMyCar.this.onLongPress(tapLatLong);
                    return true;
                }
            };
            tileRendererLayer.setXmlRenderTheme(this.getRenderTheme());
            mapView.getLayerManager().getLayers().add(tileRendererLayer);
            if (myLocationOverlay != null)
                mapView.getLayerManager().getLayers().add(this.myLocationOverlay);
            if (tappableCircle != null)
                mapView.getLayerManager().getLayers().add(tappableCircle);
            mapView.getLayerManager().redrawLayers();
        }
    }

    @SuppressLint("MissingPermission")
    private void enableAvailableProviders() {
        this.locationManager.removeUpdates(this);

        for (String provider : this.locationManager.getProviders(true)) {
            if (LocationManager.GPS_PROVIDER.equals(provider)
                    || LocationManager.NETWORK_PROVIDER.equals(provider))
                        this.locationManager.requestLocationUpdates(provider, 0, 0, this);
        }
    }

    protected void onLongPress(final LatLong position) {
        if (tappableCircle != null) {
            this.mapView.getLayerManager().getLayers().remove(tappableCircle);
            tappableCircle = null;
            mapView.getLayerManager().redrawLayers();
        }
        if (mycar_location_set) {
            mycar_location_backup = mycar_location;
            mycar_location_backup_set = true;
        }
        mycar_location = position;
        mycar_location_set = true;
        final float circleSize = 16 * this.mapView.getModel().displayModel.getScaleFactor();
        Paint ps = Utils.createPaint(
                AndroidGraphicFactory.INSTANCE.createColor(Color.TRANSPARENT), 1,
                Style.STROKE);
        tappableCircle = new FixedPixelCircle(position,
                circleSize, TRANSPARENT, ps) {

            @Override
            public void draw(BoundingBox boundingBox, byte zoomLevel, Canvas
                    canvas, Point topLeftPoint) {
                super.draw(boundingBox, zoomLevel, canvas, topLeftPoint);

                long mapSize = MercatorProjection.getMapSize(zoomLevel, this.displayModel.getTileSize());

                int pixelX = (int) (MercatorProjection.longitudeToPixelX(position.longitude, mapSize) - topLeftPoint.x);
                int pixelY = (int) (MercatorProjection.latitudeToPixelY(position.latitude, mapSize) - topLeftPoint.y);
                Drawable drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? getDrawable(R.drawable.marker_pin_right) : getResources().getDrawable(R.drawable.marker_pin_right);
                Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
                canvas.drawBitmap(bitmap, pixelX, pixelY - bitmap.getHeight());
            }

            @Override
            public boolean onLongPress(LatLong geoPoint, Point viewPosition,
                                       Point tapPoint) {
                if (this.contains(viewPosition, tapPoint)) {
                    MarkMyCar.this.mycar_location_backup = MarkMyCar.this.mycar_location;
                    MarkMyCar.this.mycar_location_backup_set = true;
                    MarkMyCar.this.mycar_location = null;
                    MarkMyCar.this.mycar_location_set = false;
                    MarkMyCar.this.mapView.getLayerManager()
                            .getLayers().remove(this);
                    tappableCircle = null;
                    MarkMyCar.this.mapView.getLayerManager()
                            .redrawLayers();
                    return true;
                }
                return false;
            }
        };
        this.mapView.getLayerManager().getLayers().add(tappableCircle);
        tappableCircle.requestRedraw();
    }
}