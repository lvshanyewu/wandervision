package com.tencent.yolov8ncnn;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Locale;

/** Foreground-only platform GPS/network fixes; no Play Services or background service. */
final class LocationTracker implements LocationListener {
    static final long INTERVAL_MS=10_000, FRESH_MS=30_000;
    private final Activity activity;
    private final Runnable changed;
    private final LocationManager manager;
    private Location latest;
    private boolean active, enabled, precise;
    private String unavailable="unavailable";
    LocationTracker(Activity activity,Runnable changed) {
        this.activity=activity;this.changed=changed;
        manager=(LocationManager)activity.getSystemService(Context.LOCATION_SERVICE);
    }
    boolean permitted(){return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void start(boolean enabled) {
        stop();this.enabled=enabled;latest=null;
        precise=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        unavailable=!enabled?"off":!permitted()?"permission_denied":"provider_disabled";
        if(!enabled||!permitted()||manager==null){changed.run();return;}
        active=true;
        subscribe();changed.run();
    }
    private void subscribe() {
        if(!active)return;
        boolean any=false;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}) {
            if(provider.equals(LocationManager.GPS_PROVIDER)&&!precise)continue;
            try {
                if(!manager.isProviderEnabled(provider))continue;
                any=true;
                // minTime is a request, not a guaranteed fix cadence. Zero distance allows stationary updates.
                manager.requestLocationUpdates(provider,INTERVAL_MS,0,this,activity.getMainLooper());
                accept(manager.getLastKnownLocation(provider));
            }catch(SecurityException ignored){unavailable="permission_denied";latest=null;}
             catch(IllegalArgumentException ignored){}
        }
        boolean systemEnabled=false;
        try{systemEnabled=android.os.Build.VERSION.SDK_INT>=28?manager.isLocationEnabled():manager.isProviderEnabled(LocationManager.GPS_PROVIDER)||manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        if(!unavailable.equals("permission_denied"))unavailable=any||systemEnabled?"searching":"provider_disabled";
    }
    void stop(){active=false;if(manager!=null)manager.removeUpdates(this);}
    private void accept(Location fix) {
        if(fix==null||!fix.hasAccuracy()||!Float.isFinite(fix.getAccuracy())||fix.getAccuracy()<=0||!Double.isFinite(fix.getLatitude())||!Double.isFinite(fix.getLongitude())||Math.abs(fix.getLatitude())>90||Math.abs(fix.getLongitude())>180)return;
        long now=SystemClock.elapsedRealtimeNanos();
        if(fix.getElapsedRealtimeNanos()>now||now-fix.getElapsedRealtimeNanos()>120_000_000_000L)return;
        if(latest==null||fix.getElapsedRealtimeNanos()>latest.getElapsedRealtimeNanos()&&
            (fix.getAccuracy()<=latest.getAccuracy()*1.5f||now-latest.getElapsedRealtimeNanos()>FRESH_MS*1_000_000L)) latest=new Location(fix);
    }
    @Override public void onLocationChanged(Location location){if(active){accept(location);changed.run();}}
    @Override public void onProviderEnabled(String provider){if(active){subscribe();changed.run();}}
    @Override public void onProviderDisabled(String provider){if(active){latest=null;subscribe();changed.run();}}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    Fix freeze(){return new Fix(enabled,permitted(),precise,unavailable,latest==null?null:new Location(latest));}
    String label(){return freeze().label(SystemClock.elapsedRealtimeNanos());}
    static final class Fix {
        final boolean enabled,permitted,precise;
        final String unavailable;
        final Location location;
        Fix(boolean enabled,boolean permitted,boolean precise,String unavailable,Location location){this.enabled=enabled;this.permitted=permitted;this.precise=precise;this.unavailable=unavailable;this.location=location;}
        long age(long at){return location==null?-1:Math.max(0,(at-location.getElapsedRealtimeNanos())/1_000_000L);}
        String status(long at){return !enabled?"off":!permitted?"permission_denied":location==null?unavailable:age(at)>FRESH_MS?"stale":precise?"fresh":"approximate";}
        String label(long at){
            if(location==null||!enabled||!permitted){switch(status(at)){
                case "off":return "定位已关闭";
                case "permission_denied":return "定位未授权 · 点击启用";
                case "provider_disabled":return "系统定位已关闭 · 点击设置";
                default:return precise?"正在定位 · 室外可改善信号":"近似定位等待中 · 可在设置开启精确位置";
            }}
            return String.format(Locale.US,"%.5f, %.5f  ±%.0fm%s%s",location.getLatitude(),location.getLongitude(),location.getAccuracy(),age(at)>FRESH_MS?" · 已过期":precise?"":" · 近似",location.isFromMockProvider()?" · 模拟":"");
        }
        JSONObject json(long at) throws Exception {
            JSONObject j=new JSONObject();j.put("status",status(at));j.put("coordinate_system","WGS84");j.put("permission",!permitted?"none":precise?"fine":"coarse");
            boolean present=enabled&&permitted&&location!=null;
            j.put("latitude",present?location.getLatitude():JSONObject.NULL);j.put("longitude",present?location.getLongitude():JSONObject.NULL);
            j.put("accuracy_m",present?location.getAccuracy():JSONObject.NULL);j.put("age_ms",present?age(at):JSONObject.NULL);
            j.put("fix_time_epoch_ms",present?location.getTime():JSONObject.NULL);j.put("provider",present?location.getProvider():JSONObject.NULL);
            j.put("fix_elapsed_ns",present?location.getElapsedRealtimeNanos():JSONObject.NULL);
            j.put("mock",present?location.isFromMockProvider():JSONObject.NULL);
            j.put("altitude_m",present&&location.hasAltitude()?location.getAltitude():JSONObject.NULL);
            j.put("speed_mps",present&&location.hasSpeed()?location.getSpeed():JSONObject.NULL);
            j.put("bearing_deg",present&&location.hasBearing()?location.getBearing():JSONObject.NULL);
            return j;
        }
    }
}
