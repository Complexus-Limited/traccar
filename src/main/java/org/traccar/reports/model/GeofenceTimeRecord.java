package org.traccar.reports.model;

public class GeofenceTimeRecord {

    private long deviceId;
    private String deviceName;
    private long geofenceId;
    private long duration; // in seconds

    private String geofenceName;

    public String getGeofenceName() {
        return geofenceName;
    }

    public void setGeofenceName(String geofenceName) {
        this.geofenceName = geofenceName;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public long getGeofenceId() {
        return geofenceId;
    }

    public void setGeofenceId(long geofenceId) {
        this.geofenceId = geofenceId;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
