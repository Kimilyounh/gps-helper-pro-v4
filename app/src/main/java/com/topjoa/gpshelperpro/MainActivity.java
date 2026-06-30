package com.topjoa.gpshelperpro;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telephony.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ = 1001;
    WebView webView;
    TextToSpeech tts;
    boolean ttsReady = false;
    int satelliteCount = 0;
    LocationManager locationManager;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 35) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                ttsReady = true;
            }
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0f172a);
        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb){ cb.invoke(origin, true, false); }
        });
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        requestPerms();
        registerGnss();
        webView.loadUrl("file:///android_asset/gps.html");
    }

    private void requestPerms(){
        if (Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> p = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.READ_PHONE_STATE);
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
        }
    }

    private static String esc(String v){ return v==null?"":v.replace("\\","\\\\").replace("\"","\\\""); }

    private void registerGnss(){
        try{
            locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(locationManager!=null && Build.VERSION.SDK_INT>=24 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                locationManager.registerGnssStatusCallback(new GnssStatus.Callback(){
                    @Override public void onSatelliteStatusChanged(GnssStatus status){
                        int used=0;
                        for(int i=0;i<status.getSatelliteCount();i++) if(status.usedInFix(i)) used++;
                        satelliteCount=used>0?used:status.getSatelliteCount();
                    }
                }, new Handler(Looper.getMainLooper()));
            }
        }catch(Exception ignored){}
    }

    private String mobileTypeName(int t){
        switch(t){
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP: case TelephonyManager.NETWORK_TYPE_HSPA: case TelephonyManager.NETWORK_TYPE_UMTS: return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE: case TelephonyManager.NETWORK_TYPE_GPRS: return "2G";
            default: return "확인중";
        }
    }

    private String readNetworkInfo(){
        String type="알 수 없음", detail="측정 제한", strength="--";
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network n=cm.getActiveNetwork();
            NetworkCapabilities cap=n==null?null:cm.getNetworkCapabilities(n);
            if(cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type="Wi-Fi";
            else if(cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type="모바일";
            TelephonyManager tm=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            int nt = Build.VERSION.SDK_INT>=24 ? tm.getDataNetworkType() : tm.getNetworkType();
            detail=mobileTypeName(nt);
            if(Build.VERSION.SDK_INT>=29){
                SignalStrength ss=tm.getSignalStrength();
                if(ss!=null) strength=ss.getLevel()+"/4";
            }
        }catch(Exception e){ detail="권한 필요"; }
        return "{\"type\":\""+esc(type)+"\",\"detail\":\""+esc(detail)+"\",\"strength\":\""+esc(strength)+"\"}";
    }

    private String readBatteryInfo(){
        try{
            Intent battery=registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale=battery==null?100:battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int pct= scale>0 ? Math.round(level*100f/scale) : level;
            boolean charging = status==BatteryManager.BATTERY_STATUS_CHARGING || status==BatteryManager.BATTERY_STATUS_FULL;
            String est = charging ? "충전중" : (pct>=80?"장시간":pct>=50?"보통":pct>=25?"주의":"부족");
            return "{\"level\":"+pct+",\"charging\":"+charging+",\"estimate\":\""+esc(est)+"\"}";
        }catch(Exception e){ return "{\"level\":0,\"charging\":false,\"estimate\":\"확인 제한\"}"; }
    }

    private String bluetoothInfo(){
        try{
            AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
            boolean on = am!=null && (am.isBluetoothScoOn() || am.isBluetoothA2dpOn());
            return "{\"connected\":"+on+",\"label\":\""+(on?"블루투스 연결":"블루투스 미연결")+"\"}";
        }catch(Exception e){ return "{\"connected\":false,\"label\":\"권한 필요\"}"; }
    }

    private boolean batteryOptimizationIgnored(){
        try{
            if(Build.VERSION.SDK_INT>=23){
                PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
                return pm!=null && pm.isIgnoringBatteryOptimizations(getPackageName());
            }
            return true;
        }catch(Exception e){ return false; }
    }

    private void openBatteryOptimization(){
        try{
            if(Build.VERSION.SDK_INT>=23){
                Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName()));
                startActivity(i);
                return;
            }
        }catch(Exception ignored){}
        try{ startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch(Exception e){ startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    public class Bridge {
        @JavascriptInterface public void startService(){ runOnUiThread(() -> { Intent i=new Intent(MainActivity.this, LocationMonitorService.class); i.setAction(LocationMonitorService.ACTION_START); if(Build.VERSION.SDK_INT>=26) MainActivity.this.startForegroundService(i); else MainActivity.this.startService(i); }); }
        @JavascriptInterface public void stopService(){ runOnUiThread(() -> { Intent i=new Intent(MainActivity.this, LocationMonitorService.class); i.setAction(LocationMonitorService.ACTION_STOP); MainActivity.this.startService(i); MainActivity.this.stopService(new Intent(MainActivity.this, LocationMonitorService.class)); }); }
        @JavascriptInterface public void openLocationSettings(){ startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
        @JavascriptInterface public void openAppSettings(){ Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())); startActivity(i); }
        @JavascriptInterface public void openBatterySettings(){ runOnUiThread(() -> openBatteryOptimization()); }
        @JavascriptInterface public String getNetworkInfo(){ return readNetworkInfo(); }
        @JavascriptInterface public String getBatteryInfo(){ return readBatteryInfo(); }
        @JavascriptInterface public int getSatelliteCount(){ return satelliteCount; }
        @JavascriptInterface public String getBluetoothInfo(){ return bluetoothInfo(); }
        @JavascriptInterface public boolean isBatteryOptimizationIgnored(){ return batteryOptimizationIgnored(); }
        @JavascriptInterface public void saveLastGood(String coord){ getSharedPreferences("gps_helper", MODE_PRIVATE).edit().putString("last_good_coord", coord).apply(); }
        @JavascriptInterface public void copyText(String text){ runOnUiThread(() -> { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("GPS", text)); Toast.makeText(MainActivity.this,"복사 완료",Toast.LENGTH_SHORT).show(); }); }
        @JavascriptInterface public void speak(String text){ if(ttsReady && text!=null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gps-alert"); }
        @JavascriptInterface public void shareCsv(String csv){ try { Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/csv"); send.putExtra(Intent.EXTRA_TEXT, csv); startActivity(Intent.createChooser(send, "CSV 공유")); } catch(Exception e){} }
        @JavascriptInterface public void shareText(String title, String text){ try { Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_SUBJECT, title); send.putExtra(Intent.EXTRA_TEXT, text); startActivity(Intent.createChooser(send, title)); } catch(Exception e){} }
    }
    @Override protected void onDestroy(){ if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy(); }
}
