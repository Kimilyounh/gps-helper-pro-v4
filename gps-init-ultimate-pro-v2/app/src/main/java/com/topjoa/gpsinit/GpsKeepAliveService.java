package com.topjoa.gpsinit;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

public class GpsKeepAliveService extends Service {
    private LocationManager lm;
    private final LocationListener listener = new LocationListener(){
        @Override public void onLocationChanged(Location location) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };
    @Override public void onCreate(){ super.onCreate(); lm=(LocationManager)getSystemService(LOCATION_SERVICE); createChannel(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId){
        Notification.Builder b = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,"gps_keep") : new Notification.Builder(this);
        b.setContentTitle("GPS Init Ultimate Pro")
         .setContentText("백그라운드 GPS 유지 중")
         .setSmallIcon(android.R.drawable.ic_menu_mylocation)
         .setOngoing(true);
        startForeground(77,b.build());
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try{ lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,1,listener); }catch(Exception ignored){}
        }
        return START_STICKY;
    }
    private void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel("gps_keep","GPS Keep Alive",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    @Override public void onDestroy(){ super.onDestroy(); try{ lm.removeUpdates(listener); }catch(Exception ignored){} }
    @Override public IBinder onBind(Intent i){ return null; }
}
