package com.topjoa.gpshelperpro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocationMonitorService extends Service {
    public static final String ACTION_START = "com.topjoa.gpshelperpro.START_MONITOR";
    public static final String ACTION_STOP = "com.topjoa.gpshelperpro.STOP_MONITOR";

    private static final String CHANNEL_ID = "gps_helper_pro_monitor";
    private static final int NOTIFICATION_ID = 1004;
    private static final long CHECK_INTERVAL_MS = 20_000L;
    private static final long STALE_MS = 30_000L;
    private static final float WARN_ACCURACY = 30f;
    private static final float BAD_ACCURACY = 60f;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler handler;
    private Location lastLocation;
    private long lastLocationAt = 0L;
    private int dropCount = 0;
    private long lastVoiceAt = 0L;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("gps_helper", MODE_PRIVATE);
        dropCount = prefs.getInt(todayKey(), 0);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("GPS 자동점검 실행 중", "위치 신호를 백그라운드에서 감시합니다"));
        acquireWakeLock();
        initTts();
        startLocationUpdates();
        handler.post(checkRunnable);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startLocationUpdates();
        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GPS Helper Pro 자동점검", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("백그라운드 GPS 자동점검 상태 알림");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSHelperPro:MonitorLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(6 * 60 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.KOREAN);
                    ttsReady = true;
                }
            });
        } catch (Exception ignored) {}
    }

    private void startLocationUpdates() {
        if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("위치 권한 필요", "앱 설정에서 위치 권한을 항상 허용하세요");
            return;
        }
        if (locationListener == null) {
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) { handleLocation(location); }
                @Override public void onProviderEnabled(String provider) { updateNotification("GPS 사용 가능", "위치 신호 감시 중"); }
                @Override public void onProviderDisabled(String provider) { markDrop("GPS가 꺼졌습니다"); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (Exception ignored) {}
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, locationListener, Looper.getMainLooper());
        } catch (Exception ignored) {}
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, locationListener, Looper.getMainLooper());
        } catch (Exception ignored) {}
    }

    private void handleLocation(Location location) {
        lastLocation = location;
        lastLocationAt = System.currentTimeMillis();
        float acc = location.hasAccuracy() ? location.getAccuracy() : 999f;
        double speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6 : 0.0;
        String grade;
        if (acc <= 10) grade = "매우 좋음";
        else if (acc <= 20) grade = "좋음";
        else if (acc <= WARN_ACCURACY) grade = "보통";
        else if (acc <= BAD_ACCURACY) grade = "주의";
        else grade = "위험";

        if (acc > BAD_ACCURACY) markDrop("GPS 정확도 위험 " + Math.round(acc) + "m");
        else if (acc > WARN_ACCURACY) speakLimited("GPS 정확도가 낮습니다");

        updateNotification("GPS " + grade + " · " + Math.round(acc) + "m",
                "속도 " + String.format(Locale.KOREA, "%.1f", speedKmh) + "km/h · 끊김 " + dropCount + "회 · " + networkName());
    }

    private final Runnable checkRunnable = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            try {
                if (locationManager != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    markDrop("GPS가 꺼져 있습니다");
                } else if (lastLocationAt == 0L || now - lastLocationAt > STALE_MS) {
                    markDrop("30초 이상 위치 미갱신");
                    startLocationUpdates();
                } else if (lastLocation != null && lastLocation.hasAccuracy() && lastLocation.getAccuracy() > WARN_ACCURACY) {
                    startLocationUpdates();
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private void markDrop(String reason) {
        dropCount++;
        prefs.edit().putInt(todayKey(), dropCount).apply();
        updateNotification("GPS 자동 복구 중", reason + " · 오늘 끊김 " + dropCount + "회");
        speakLimited(reason + ". 자동 복구를 시도합니다");
        startLocationUpdates();
    }

    private void speakLimited(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastVoiceAt < 60_000L) return;
        lastVoiceAt = now;
        try {
            if (ttsReady && tts != null) tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "gps-service-alert");
        } catch (Exception ignored) {}
    }

    private String todayKey() {
        return "drop_" + new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
    }

    private String networkName() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm == null) return "네트워크 확인중";
            int t = Build.VERSION.SDK_INT >= 24 ? tm.getDataNetworkType() : tm.getNetworkType();
            if (t == TelephonyManager.NETWORK_TYPE_NR) return "5G";
            if (t == TelephonyManager.NETWORK_TYPE_LTE) return "LTE";
            return "모바일";
        } catch (Exception e) { return "네트워크 권한 필요"; }
    }

    private void updateNotification(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(title, text));
    }

    private Notification buildNotification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, LocationMonitorService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "중지", stopPi)
                .build();
    }

    @Override public void onDestroy() {
        try { if (handler != null) handler.removeCallbacksAndMessages(null); } catch (Exception ignored) {}
        try { if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
