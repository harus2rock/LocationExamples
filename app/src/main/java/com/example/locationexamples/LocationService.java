package com.example.locationexamples;

import android.Manifest;
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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;
import static android.location.GpsStatus.*;

public class LocationService extends Service implements LocationListener, Listener {
    public static final String LOG_TAG = LocationService.class.getSimpleName();

    private final LocationServiceBinder binder = new LocationServiceBinder();
    boolean isLocationManagerUpdatingLocation;

    ArrayList<Location> locationList;
    boolean isLogging;

    public LocationService() {
    }

    @Override
    public void onCreate() {
        isLocationManagerUpdatingLocation = false;
        locationList = new ArrayList<>();
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
            locationList.add(newLocation);
        }

        Intent intent = new Intent("LocationUpdated");
        intent.putExtra("location", newLocation);

        LocalBroadcastManager.getInstance(this.getApplication()).sendBroadcast(intent);
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
