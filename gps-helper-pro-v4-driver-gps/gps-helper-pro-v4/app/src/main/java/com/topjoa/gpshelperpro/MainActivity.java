package com.topjoa.gpshelperpro;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.speech.tts.TextToSpeech;
import java.io.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ = 1001;
    WebView webView;
    TextToSpeech tts;
    boolean ttsReady = false;

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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xff0f172a);
        webView = new WebView(this); root.addView(webView, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        WebSettings s = webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setGeolocationEnabled(true); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb){ cb.invoke(origin, true, false); }
        });
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        requestPerms();
        webView.loadUrl("file:///android_asset/gps.html");
    }
    private void requestPerms(){
        if (Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> p = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.READ_PHONE_STATE);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
        }
    }
    private static String esc(String v){ return v==null?"":v.replace("\\","\\\\").replace("\"","\\\""); }
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
            int nt;
            if(Build.VERSION.SDK_INT>=24) nt=tm.getDataNetworkType(); else nt=tm.getNetworkType();
            detail=mobileTypeName(nt);
            if(Build.VERSION.SDK_INT>=29){
                SignalStrength ss=tm.getSignalStrength();
                if(ss!=null) strength=ss.getLevel()+"/4";
            }
        }catch(Exception e){ detail="권한 필요"; }
        return "{\"type\":\""+esc(type)+"\",\"detail\":\""+esc(detail)+"\",\"strength\":\""+esc(strength)+"\"}";
    }
    public class Bridge {
        @JavascriptInterface public void startService(){ runOnUiThread(() -> { Intent i=new Intent(MainActivity.this, LocationMonitorService.class); if(Build.VERSION.SDK_INT>=26) MainActivity.this.startForegroundService(i); else MainActivity.this.startService(i); }); }
        @JavascriptInterface public void stopService(){ runOnUiThread(() -> MainActivity.this.stopService(new Intent(MainActivity.this, LocationMonitorService.class))); }
        @JavascriptInterface public void openLocationSettings(){ startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
        @JavascriptInterface public void openAppSettings(){ Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())); startActivity(i); }
        @JavascriptInterface public String getNetworkInfo(){ return readNetworkInfo(); }
        @JavascriptInterface public void copyText(String text){ runOnUiThread(() -> { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("GPS", text)); Toast.makeText(MainActivity.this,"복사 완료",Toast.LENGTH_SHORT).show(); }); }
        @JavascriptInterface public void speak(String text){ if(ttsReady && text!=null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gps-alert"); }
        @JavascriptInterface public void shareCsv(String csv){ try { File f=new File(getExternalFilesDir(null), "gps_log.csv"); try(FileOutputStream out=new FileOutputStream(f)){ out.write(csv.getBytes("UTF-8")); } Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/csv"); send.putExtra(Intent.EXTRA_TEXT, csv); startActivity(Intent.createChooser(send, "CSV 공유")); } catch(Exception e){} }
    }
    @Override protected void onDestroy(){ if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy(); }
}
