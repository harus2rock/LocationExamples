package com.example.locationexamples;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static android.content.ContentValues.TAG;
import static android.location.GpsStatus.*;

public class LocationService extends Service implements LocationListener, Listener {
    public static final String LOG_TAG = LocationService.class.getSimpleName();

    private final LocationServiceBinder binder = new LocationServiceBinder();
    boolean isLocationManagerUpdatingLocation;

    ArrayList<Location> locationList;
    boolean isLogging;

    ArrayList<Location> oldLocationList;
    ArrayList<Location> noAccuracyLocationList;
    ArrayList<Location> inaccurateLocationList;

    float currentSpeed = 0.0f; // meters/second

    public LocationService() {
    }

    @Override
    public void onCreate() {
        isLocationManagerUpdatingLocation = false;
        locationList = new ArrayList<>();
        oldLocationList = new ArrayList<>();
        noAccuracyLocationList = new ArrayList<>();
        inaccurateLocationList = new ArrayList<>();

        isLogging = false;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        super.onStartCommand(i, flags, startId);
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @Override
    public void onRebind(Intent intent) {
        Log.d(LOG_TAG, "onRebind ");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(LOG_TAG, "onUnbind ");
        return true;
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy ");
    }

    //This is where we detect the app is being killed, thus stop service.
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(LOG_TAG, "onTaskRemoved ");

        if(this.isLocationManagerUpdatingLocation){
            this.stopUpdatingLocation();
            isLocationManagerUpdatingLocation = false;
        }

        stopSelf();
    }

    /**
     * Binder class
     * @author Takamitsu Mizutori
     *
     */
    class LocationServiceBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {

    }

    @Override
    public void onLocationChanged(Location newLocation) {
        Log.d(TAG, "(" + newLocation.getLatitude() + "," + newLocation.getLongitude() + ")");

        if(isLogging){
//            locationList.add(newLocation);
            filterAndAddLocation(newLocation);
        }

        Intent intent = new Intent("LocationUpdated");
        intent.putExtra("location", newLocation);

        LocalBroadcastManager.getInstance(this.getApplication()).sendBroadcast(intent);
    }

    @SuppressLint("NewApi")
    private long getLocationAge(Location newLocation){
        long locationAge;
        if(Build.VERSION.SDK_INT >= 17){
            long currentTimeInMilli = (long)(SystemClock.elapsedRealtimeNanos() / 1000000);
            long locationTimeInMilli = (long)(newLocation.getElapsedRealtimeNanos() / 1000000);
            locationAge = currentTimeInMilli - locationTimeInMilli;
        }else{
            locationAge = System.currentTimeMillis() - newLocation.getTime();
        }
        return locationAge;
    }

    private boolean filterAndAddLocation(Location location){
        long age = getLocationAge(location);

        if(age > 10 * 1000){ // more than 10 seconds
            Log.d(TAG, "Location is old");
            oldLocationList.add(location);
            return false;
        }

        if(location.getAccuracy() <= 0){
            Log.d(TAG, "Latitude and longitude values are invalid.");
            noAccuracyLocationList.add(location);
            return false;
        }

        float horizontalAccuracy = location.getAccuracy();
        if(horizontalAccuracy > 30){ // 30 meter filter
            Log.d(TAG, "Accuracy is too low.");
            inaccurateLocationList.add(location);
            return false;
        }

        Log.d(TAG, "Location quality is good enough.");
        currentSpeed = location.getSpeed();
        locationList.add(location);

        return true;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                notifyLocationProviderStatusUpdated(false);
            } else {
                notifyLocationProviderStatusUpdated(true);
            }
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)){
            notifyLocationProviderStatusUpdated(false);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            notifyLocationProviderStatusUpdated(true);
        }
    }

    private void notifyLocationProviderStatusUpdated(boolean isLocationProviderAvailable) {
        // Broadcast location provider status change here
    }

    public void startLogging() {
       isLogging = true;
    }

    public void stopLogging() {
//        TODO: saveLog
        if (locationList.size() > 1){
            saveLog();
        }
        isLogging = false;
    }

//  Data Logging
    public synchronized void saveLog(){
        @SuppressLint("SimpleDateFormat") SimpleDateFormat fileNameDateTimeFormat = new SimpleDateFormat("yyyy_MMdd_HHmm");
        String filePath = this.getExternalFilesDir(null).getAbsolutePath() + "/"
                + fileNameDateTimeFormat.format(new Date()) + "log.csv";

        Log.d(TAG, "saving to " + filePath);

        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(filePath, false);
            String record = "Latitude,Longitude,Accuracy,Elapsed(sec)\n";
            Log.d(TAG, record);
            fileWriter.append(record);

            if(Build.VERSION.SDK_INT >= 17){
                long startTime = locationList.get(0).getElapsedRealtimeNanos();

                for (Location location : locationList){
                    long time = location.getElapsedRealtimeNanos() - startTime;
                    record = "" + location.getLatitude() + "," + location.getLongitude()
                            + "," + location.getAccuracy() + "," + (time/1000000000) + "\n";
                    Log.d(TAG, record);
                    fileWriter.append(record);
                }

            } else {
                long startTime = locationList.get(0).getTime();

                for (Location location : locationList){
                    long time = location.getTime() - startTime;
                    record = "" + location.getLatitude() + "," + location.getLongitude()
                            + "," + location.getAccuracy() + "," + (time/1000) + "\n";
                    Log.d(TAG, record);
                    fileWriter.append(record);

                }
            }
//            long startTime = locationList.get(0).getTime();
//            for (Location location : locationList){
//                if(Build.VERSION.SDK_INT >= 17){
//                    record = "" + location.getLatitude() + "," + location.getLongitude() + "," + location.getAccuracy() + "," + (location.getElapsedRealtimeNanos()/1000) + "\n";
//                }else{
//                    record = "" + location.getLatitude() + "," + location.getLongitude() + "," + location.getAccuracy() + "," + (location.getTime()*1000) + "\n";
//                }
//                Log.d(TAG, record);
//                fileWriter.append(record);
//            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void startUpdatingLocation() {

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            criteria.setAltitudeRequired(false);
            criteria.setSpeedRequired(false); // calculate speed: false
            criteria.setCostAllowed(true);
            criteria.setBearingRequired(false); // direction information

            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

            int gpsFreqInMillis = 1000;
            int gpsFreqInDistance = 1; // in meters

            locationManager.addGpsStatusListener(this);
            locationManager.requestLocationUpdates(gpsFreqInMillis, gpsFreqInDistance, criteria, this, null);

        } catch (IllegalArgumentException e){
            Log.e(LOG_TAG, e.getLocalizedMessage());
        } catch (SecurityException e) {
            Log.e(LOG_TAG, e.getLocalizedMessage());
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, e.getLocalizedMessage());
        }
    }

    public  void stopUpdatingLocation(){
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationManager.removeUpdates(this);
    }
}
