package com.example.locationexamples;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.w3c.dom.Text;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";

    public LocationService locationService;
    private static final int REQUEST_CODE_PERMISSION = 2;

    private GoogleMap mMap;
    private LatLng latlng;

    private Marker userPositionMarker;
    private Circle locationAccuracyCircle;
    private BitmapDescriptor userPositionMarkerBitmapDescriptor;
    private Polyline runningPathPolyline;
    private PolylineOptions polylineOptions;
    private int polylineWidth = 20;

    boolean zoomable = true;
    Timer zoomBlockingTimer;
    boolean didInitialZoom;
    private Handler handlerOnUIThread;

    private BroadcastReceiver locationUpdateReceiver;

    private Chronometer chronometer;
    private TextView distanceText;
    private TextView speedText;
    private ImageButton startButton;
    private ImageButton stopButton;

    private DialogFragment dialogFragment;
    private FragmentManager dialogFragmentManager;

    ArrayList<Circle> malCircles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Permission check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Permission is off.", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_PERMISSION);
            }
            return;
        }

        // LocationService
        final Intent serviceStart = new Intent(this.getApplication(), LocationService.class);
        this.getApplication().startService(serviceStart);
        this.getApplication().bindService(serviceStart, serviceConnection, Context.BIND_AUTO_CREATE);

        // Map
        SupportMapFragment mapFragment =
               (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Get present location
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Location newLocation = intent.getParcelableExtra("location");

                drawLocationAccuracyCircle(newLocation);
                drawUserPositionMarker(newLocation);

                if (locationService.isLogging) {
                    addPolyline();
                    clearExtraPoints();
                    addExtraPoints();
                }
                zoomMapTo(newLocation);

                sumDistance();
                calcSpeed();
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(
                locationUpdateReceiver,
                new IntentFilter("LocationUpdated"));

        // Get text views
        distanceText = (TextView) findViewById(R.id.distance);
        speedText = (TextView) findViewById(R.id.speed);

        // Button and Chronometer
        chronometer = findViewById(R.id.chronometer);

        startButton = (ImageButton) this.findViewById(R.id.start_button);
        stopButton = (ImageButton) this.findViewById(R.id.stop_button);
        stopButton.setVisibility(View.INVISIBLE);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startButton.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.VISIBLE);

                clearPolyline();
                clearExtraPoints();
                locationService.startLogging();

                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.start();

                distanceText.setText(R.string.textView_dist);
                speedText.setText(R.string.textView_speed);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogFragmentManager = getSupportFragmentManager();
                dialogFragment = new AlertDialogFragment();
                dialogFragment.show(dialogFragmentManager, "save alert dialog");
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Test to visible GoogleMap
//        double latitude = 34.97;
//        double longitude = 137.152;
//
//        latlng = new LatLng(latitude, longitude);
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 14));

        // my settings
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.setMyLocationEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Auto zoom settings
        mMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                if(reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE){
                    Log.d(TAG, "onCameraMoveStarted after user's zoom action");

                    zoomable = false;
                    if (zoomBlockingTimer != null) {
                        zoomBlockingTimer.cancel();
                    }

                    handlerOnUIThread = new Handler();

                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            handlerOnUIThread.post(new Runnable() {
                                @Override
                                public void run() {
                                    zoomBlockingTimer = null;
                                    zoomable = true;
                                    try {
                                        zoomable = false;
                                        mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng),
                                                new GoogleMap.CancelableCallback() {
                                                    @Override
                                                    public void onFinish() {
                                                        zoomable = true;
                                                    }

                                                    @Override
                                                    public void onCancel() {
                                                        zoomable = true;
                                                    }
                                                });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    };
                    zoomBlockingTimer = new Timer();
                    zoomBlockingTimer.schedule(task, 5 * 1000);
                    Log.d(TAG, "start blocking auto zoom for 5 seconds");
                }
            }
        });
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            String name = className.getClassName();

            if (name.endsWith("LocationService")) {
                locationService = ((LocationService.LocationServiceBinder) service).getService();

                locationService.startUpdatingLocation();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            if (className.getClassName().equals("LocationService")) {
                locationService = null;
            }

        }
    };

    private void zoomMapTo(Location location) {
        latlng = new LatLng(location.getLatitude(), location.getLongitude());

        if (this.didInitialZoom == false) {
            try {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 17.5f));
                this.didInitialZoom = true;
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (zoomable) {
            try {
                zoomable = false;
                mMap.animateCamera(CameraUpdateFactory.newLatLng(latlng),
                        new GoogleMap.CancelableCallback() {
                            @Override
                            public void onFinish() {
                                zoomable = true;
                            }

                            @Override
                            public void onCancel() {
                                zoomable = true;
                            }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void drawUserPositionMarker(Location location){
        latlng = new LatLng(location.getLatitude(), location.getLongitude());
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 17));

        if(this.userPositionMarkerBitmapDescriptor == null) {
            userPositionMarkerBitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.point_red);
        }

        if(userPositionMarker == null) {
            userPositionMarker = mMap.addMarker(new MarkerOptions()
                    .position(latlng)
                    .flat(true)
                    .anchor(0.5f, 0.5f)
                    .icon(this.userPositionMarkerBitmapDescriptor));
        } else {
            userPositionMarker.setPosition(latlng);
        }
    }

    private void drawLocationAccuracyCircle(Location location) {
        latlng = new LatLng(location.getLatitude(), location.getLongitude());

        if (this.locationAccuracyCircle == null) {
            this.locationAccuracyCircle = mMap.addCircle(new CircleOptions()
                    .center(latlng)
                    .fillColor(Color.argb(64, 0, 0, 0))
                    .strokeColor(Color.argb(64, 0, 0, 0))
                    .strokeWidth(0.0f)
                    .radius(location.getAccuracy()));
        } else {
            this.locationAccuracyCircle.setCenter(latlng);
        }
    }

    private void addPolyline() {
        ArrayList<Location> locationList = locationService.locationList;

        if (locationList.size() == 2) {
            Location fromLocation = locationList.get(0);
            Location toLocation = locationList.get(1);

            LatLng from = new LatLng(((fromLocation.getLatitude())),
                    ((fromLocation.getLongitude())));
            LatLng to = new LatLng(((toLocation.getLatitude())),
                    ((toLocation.getLongitude())));
            this.runningPathPolyline = mMap.addPolyline(new PolylineOptions()
                    .add(from, to).width(polylineWidth)
                    .color(Color.parseColor("#80FF7F50")).geodesic(true));
        } else if (locationList.size() > 2) {
            Location toLocation = locationList.get(locationList.size() - 1);
            LatLng to = new LatLng(((toLocation.getLatitude())),
                    ((toLocation.getLongitude())));

            List<LatLng> points = runningPathPolyline.getPoints();
            points.add(to);

            runningPathPolyline.setPoints(points);
        }
    }

    private void addExtraPoints() {
        ArrayList<Location> oldLocationList = locationService.oldLocationList;
        ArrayList<Location> noAccuracyLocationList = locationService.noAccuracyLocationList;
        ArrayList<Location> inaccuracyLocationList = locationService.inaccurateLocationList;

        drawExtraPoints(oldLocationList, "#80FFA500");
        drawExtraPoints(noAccuracyLocationList, "#80FFD700");
        drawExtraPoints(inaccuracyLocationList, "#80191970");
    }

    private void drawExtraPoints(ArrayList<Location> locationList, String colorString){
        for (Location location: locationList){
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            Circle circle = mMap.addCircle(new CircleOptions()
                    .center(latLng)
                    .radius(1)
                    .fillColor(Color.parseColor(colorString))
                    .strokeWidth(0));

            malCircles.add(circle);
        }
    }

    private void clearPolyline() {
        if (runningPathPolyline != null) {
            runningPathPolyline.remove();
        }
    }

    private void clearExtraPoints() {
        for (Circle circle : malCircles){
            circle.remove();
        }
        malCircles.clear();
    }

    @SuppressLint("DefaultLocale")
    private void sumDistance() {
        if (locationService.locationList.size() > 1){
            double meter = 0;
            float[] results = new float[3];
            int i = 1;

            while (i<locationService.locationList.size()){
                results[0] = 0;
                Location from = locationService.locationList.get(i-1);
                Location to = locationService.locationList.get(i);
                Location.distanceBetween(from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(), results);
                meter += results[0];
                i++;
            }

            double kilometer = meter / 1000;
            distanceText.setText(String.format("%.2f" + " (km)", kilometer));
        }
    }

    @SuppressLint("DefaultLocale")
    private void calcSpeed() {
        int locationNum = locationService.locationList.size();
        if(locationNum > 1){
            // calculate time (sec)
            int pointNum = Math.min(10, locationNum);
            long sec = 0;

            if(Build.VERSION.SDK_INT >= 17){
                long fromTime = locationService.locationList
                        .get(locationNum - pointNum).getElapsedRealtimeNanos();
                long toTime = locationService.locationList
                        .get(locationNum - 1).getElapsedRealtimeNanos();
                sec = (toTime - fromTime) / 1000000000;
                Log.d(TAG, fromTime + " " + toTime + " " + sec);
            } else {
                Log.d(TAG, "else");
                long fromTime = locationService.locationList
                        .get(locationNum - pointNum).getTime();
                long toTime = locationService.locationList
                        .get(locationNum - 1).getTime();
                sec = (toTime - fromTime) / 1000;
            }

            // calculate distance (m)
            int i = locationNum - pointNum + 1;
            double meter = 0;
            float [] results = new float[3];

            while (i<locationNum){
                results[0] = 0;
                Location from = locationService.locationList.get(i-1);
                Location to = locationService.locationList.get(i);
                Location.distanceBetween(from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(), results);
                meter += results[0];
                i++;
            }

            // calculate speed (km/h) and set text
            double speed = meter / 1000 / sec * 3600;
            speedText.setText(String.format("%.2f" + " (km/h)", speed));
        }
    }

    public void stopLogging(boolean saveLog){
        startButton.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.INVISIBLE);
        locationService.stopLogging(saveLog);
        chronometer.stop();
    }

    // DialogFragment
    public static class AlertDialogFragment extends DialogFragment {
        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle("ログの保存")
                    .setMessage("今回のログを保存しますか？")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Yew button pressed
                            stop(true);
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // No button pressed
                            stop(false);
                        }
                    })
                    .setNeutralButton("Cancel", null)
                    .create();
        }

        private void stop(boolean saveLog){
            MainActivity mainActivity = (MainActivity) getActivity();
            if(mainActivity != null) {
                mainActivity.stopLogging(saveLog);
            }
        }
    }
}
