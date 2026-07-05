package com.topjoa.gpsinit;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ = 1001;
    private LocationManager lm;
    private TextView status, scoreView, gpsView, gnssView, logView;
    private Location lastLocation;
    private long lastFixMs = 0;
    private int satellitesVisible = 0, satellitesUsed = 0;
    private boolean driveMode = false;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

    private final LocationListener listener = new LocationListener() {
        @Override public void onLocationChanged(Location l) { updateLocation(l); }
        @Override public void onProviderEnabled(String p) { renderStatus("GPS 사용 가능: " + p); }
        @Override public void onProviderDisabled(String p) { renderStatus("GPS 꺼짐: 위치 설정을 켜주세요."); }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    private GnssStatus.Callback gnssCallback;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        buildUi();
        if (hasPermission()) startAll(); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(26, 26, 26, 40);
        root.setBackgroundColor(Color.rgb(7,17,31));
        sv.addView(root);
        TextView title = tv("GPS Init Ultimate Pro v2.0", 25, true); root.addView(title);
        TextView desc = tv("고정밀 GPS 재수신 · GNSS 위성상태 · 자동복구 · 주행모드 · 로그", 14, false); root.addView(desc);
        scoreView = card("GPS 품질 점수: 대기중"); root.addView(scoreView);
        status = card("상태: 권한 확인 중"); root.addView(status);
        gpsView = card("위치 정보: 대기중"); root.addView(gpsView);
        gnssView = card("위성 정보: 대기중"); root.addView(gnssView);
        logView = card("최근 로그: 없음"); root.addView(logView);
        row(root, "GPS BOOST 시작", v -> startAll(), "1회 재측정", v -> requestSingleFix());
        row(root, "백그라운드 유지 ON", v -> startService(new Intent(this, GpsKeepAliveService.class)), "백그라운드 OFF", v -> stopService(new Intent(this, GpsKeepAliveService.class)));
        row(root, "주행 모드 전환", v -> toggleDriveMode(), "지도 열기", v -> openMap());
        row(root, "위치 설정", v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), "로그 저장", v -> saveLog());
        Button nav = btn("콜마노/내비 실행은 패키지명 확인 후 추가 가능"); nav.setOnClickListener(v -> renderStatus("패키지명 확인 후 자동 실행 기능을 연결하세요.")); root.addView(nav);
        setContentView(sv);
    }

    private TextView tv(String s, int sp, boolean bold){ TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); t.setPadding(0,8,0,8); return t; }
    private TextView card(String s){ TextView t = tv(s,16,false); t.setBackgroundColor(Color.rgb(17,24,39)); t.setPadding(22,18,22,18); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,14,0,0); t.setLayoutParams(lp); return t; }
    private Button btn(String s){ Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(37,99,235)); b.setAllCaps(false); b.setPadding(8,12,8,12); return b; }
    private void row(LinearLayout root, String a, View.OnClickListener la, String b, View.OnClickListener lb){ LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0,12,0,0); Button ba=btn(a), bb=btn(b); ba.setOnClickListener(la); bb.setOnClickListener(lb); r.addView(ba,new LinearLayout.LayoutParams(0,-2,1)); r.addView(bb,new LinearLayout.LayoutParams(0,-2,1)); root.addView(r); }

    private boolean hasPermission(){ return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==REQ && hasPermission()) startAll(); else renderStatus("위치 권한이 필요합니다."); }

    private void startAll(){
        if(!hasPermission()) return;
        try{
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, driveMode?1000:3000, driveMode?0:2, listener);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5, listener);
            registerGnss();
            requestSingleFix();
            renderStatus("GPS BOOST 실행 중: 최신 위치를 재수신합니다.");
        }catch(Exception e){ renderStatus("오류: "+e.getMessage()); }
    }

    private void requestSingleFix(){
        if(!hasPermission()) return;
        try{
            Criteria c = new Criteria(); c.setAccuracy(Criteria.ACCURACY_FINE); c.setPowerRequirement(Criteria.POWER_HIGH);
            lm.requestSingleUpdate(c, listener, null);
            renderStatus("1회 고정밀 재측정 요청 완료");
        }catch(Exception e){ renderStatus("재측정 실패: "+e.getMessage()); }
    }

    private void registerGnss(){
        if(Build.VERSION.SDK_INT < 24 || gnssCallback != null) return;
        gnssCallback = new GnssStatus.Callback(){
            @Override public void onSatelliteStatusChanged(GnssStatus s){
                satellitesVisible = s.getSatelliteCount(); satellitesUsed = 0;
                int gps=0,glo=0,gal=0,bei=0,qz=0; float snr=0; int snrCount=0;
                for(int i=0;i<s.getSatelliteCount();i++){
                    if(s.usedInFix(i)) satellitesUsed++;
                    int type=s.getConstellationType(i);
                    if(type==GnssStatus.CONSTELLATION_GPS) gps++; else if(type==GnssStatus.CONSTELLATION_GLONASS) glo++; else if(type==GnssStatus.CONSTELLATION_GALILEO) gal++; else if(type==GnssStatus.CONSTELLATION_BEIDOU) bei++; else if(type==GnssStatus.CONSTELLATION_QZSS) qz++;
                    snr += s.getCn0DbHz(i); snrCount++;
                }
                float avg = snrCount>0 ? snr/snrCount : 0;
                gnssView.setText("위성 정보\n보이는 위성: "+satellitesVisible+"개\n사용 중 위성: "+satellitesUsed+"개\n평균 신호: "+String.format(Locale.KOREA,"%.1f",avg)+" dB-Hz\nGPS:"+gps+" GLONASS:"+glo+" Galileo:"+gal+" BeiDou:"+bei+" QZSS:"+qz);
                updateScore();
            }
        };
        try{ lm.registerGnssStatusCallback(gnssCallback); }catch(SecurityException ignored){}
    }

    private void updateLocation(Location l){
        lastLocation = l; lastFixMs = System.currentTimeMillis();
        String speed = l.hasSpeed()?String.format(Locale.KOREA,"%.1f km/h",l.getSpeed()*3.6):"미지원";
        String alt = l.hasAltitude()?String.format(Locale.KOREA,"%.1f m",l.getAltitude()):"미지원";
        gpsView.setText("위치 정보\n위도: "+l.getLatitude()+"\n경도: "+l.getLongitude()+"\n정확도: 약 "+Math.round(l.getAccuracy())+"m\n속도: "+speed+"\n고도: "+alt+"\n측정: "+fmt.format(new Date(l.getTime())));
        appendLog("FIX acc="+Math.round(l.getAccuracy())+"m speed="+speed);
        updateScore();
        if(l.getAccuracy()>30) requestSingleFix();
    }

    private void updateScore(){
        int score = 30;
        if(lastLocation!=null){ float a=lastLocation.getAccuracy(); score += a<=5?40:a<=10?32:a<=20?22:a<=50?10:0; }
        score += Math.min(20, satellitesUsed*3);
        if(lastFixMs>0 && System.currentTimeMillis()-lastFixMs<10000) score += 10;
        if(score>100) score=100;
        String grade = score>=85?"매우 좋음":score>=70?"좋음":score>=50?"보통":"약함";
        scoreView.setText("GPS 품질 점수: "+score+" / 100  ("+grade+")");
    }

    private void toggleDriveMode(){ driveMode=!driveMode; renderStatus("주행 모드: "+(driveMode?"ON - 빠른 갱신":"OFF - 일반 갱신")); startAll(); }
    private void openMap(){ if(lastLocation==null){ renderStatus("지도 열 위치가 아직 없습니다."); return; } startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:"+lastLocation.getLatitude()+","+lastLocation.getLongitude()+"?q="+lastLocation.getLatitude()+","+lastLocation.getLongitude()+"(GPS)"))); }
    private void renderStatus(String s){ status.setText("상태\n"+s+"\n"+fmt.format(new Date())); appendLog(s); }
    private void appendLog(String s){ String old=logView==null?"":logView.getText().toString(); String line=fmt.format(new Date())+"  "+s; if(logView!=null) logView.setText(("최근 로그\n"+line+"\n"+old.replace("최근 로그\n","")).substring(0, Math.min(900, ("최근 로그\n"+line+"\n"+old.replace("최근 로그\n","")).length()))); }
    private void saveLog(){ try{ File f=new File(getExternalFilesDir(null),"gps-log.txt"); FileWriter w=new FileWriter(f,true); w.write(logView.getText().toString()+"\n\n"); w.close(); renderStatus("로그 저장: "+f.getAbsolutePath()); }catch(Exception e){ renderStatus("로그 저장 실패: "+e.getMessage()); } }

    @Override protected void onDestroy(){ super.onDestroy(); try{ lm.removeUpdates(listener); if(Build.VERSION.SDK_INT>=24 && gnssCallback!=null) lm.unregisterGnssStatusCallback(gnssCallback); }catch(Exception ignored){} }
}
