package org.traccar.reports;

import jakarta.inject.Inject;
import org.jxls.util.JxlsHelper;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.GeofenceTimeRecord;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeofenceTimeReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public GeofenceTimeReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    public class ReportFunctions {
        public String formatDuration(long totalSeconds) {
            long days = totalSeconds / 86400;
            long hours = (totalSeconds % 86400) / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        }
    }

    public void getExcel(OutputStream outputStream, long userId,
            List<Long> deviceIds, List<Long> groupIds,
            Date from, Date to, boolean grouped) throws StorageException, IOException {

        String templateName = grouped ? "geofence_time_grouped.xlsx" : "geofence_time_per_device.xlsx";
        File templateFile = Paths.get(config.getString(Keys.TEMPLATES_ROOT), "export", templateName).toFile();

        try (InputStream inputStream = new FileInputStream(templateFile)) {
            var context = reportUtils.initializeContext(userId);
            Collection<GeofenceTimeRecord> records = getGeofenceTimes(userId, deviceIds, groupIds, from, to, grouped);
            context.putVar("items", records);
            context.putVar("grouped", grouped);
            context.putVar("from", from);
            context.putVar("to", to);
            context.putVar("utils", new ReportFunctions());
            JxlsHelper.getInstance()
                    .setUseFastFormulaProcessor(false)
                    .processTemplate(inputStream, outputStream, context);
        }
    }

    public Collection<GeofenceTimeRecord> getGeofenceTimes(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Date from, Date to, boolean grouped) throws StorageException {

        reportUtils.checkPeriodLimit(from, to);
        Map<Long, String> geofenceNames = storage.getObjects(
                Geofence.class,
                new Request(new Columns.All(), new Condition.Permission(Device.class, userId, Geofence.class))).stream()
                .collect(Collectors.toMap(Geofence::getId, Geofence::getName));

        Collection<Device> devices = DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds);
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("No accessible devices found.");
        }

        Map<String, GeofenceTimeRecord> detailedMap = new HashMap<>();
        Map<Long, GeofenceTimeRecord> groupedMap = new HashMap<>();

        for (Device device : devices) {
            Request request = new Request(
                    new Columns.All(),
                    new Condition.And(
                            new Condition.Equals("deviceId", device.getId()),
                            new Condition.Between("eventTime", from, to)));

            Collection<Event> allEvents = storage.getObjects(Event.class, request);
            List<Event> events = allEvents.stream()
                    .filter(e -> "geofenceEnter".equals(e.getType()) || "geofenceExit".equals(e.getType()))
                    .sorted(Comparator.comparing(Event::getEventTime))
                    .toList();

            Map<Long, Date> enterTimes = new HashMap<>();

            for (Event event : events) {
                long geofenceId = event.getGeofenceId();
                Date eventTime = event.getEventTime();

                if ("geofenceEnter".equals(event.getType())) {
                    enterTimes.putIfAbsent(geofenceId, eventTime);
                } else if ("geofenceExit".equals(event.getType())) {
                    Date enterTime = enterTimes.remove(geofenceId);
                    if (enterTime != null && eventTime.after(enterTime)) {
                        long durationSeconds = (eventTime.getTime() - enterTime.getTime()) / 1000;

                        if (grouped) {
                            groupedMap.compute(geofenceId, (id, existing) -> {
                                if (existing == null) {
                                    GeofenceTimeRecord record = new GeofenceTimeRecord();
                                    record.setGeofenceId(id);
                                    String geofenceName = "Unknown";
                                    try {
                                        Geofence geofence = storage.getObject(Geofence.class, new Request(
                                                new Columns.Include("name"),
                                                new Condition.Equals("id", geofenceId)));
                                        if (geofence != null) {
                                            geofenceName = geofence.getName();
                                        }
                                    } catch (StorageException e) {
                                        // Optionally log the error
                                        System.err.println(
                                                "Failed to fetch geofence name for ID " + geofenceId + ": " + e.getMessage());
                                    }
                                    record.setGeofenceName(geofenceName);
                                    record.setDuration(durationSeconds);
                                    return record;
                                } else {
                                    existing.setDuration(existing.getDuration() + durationSeconds);
                                    return existing;
                                }
                            });
                        } else {

                            String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd").format(enterTime);
                            String key = device.getId() + "-" + geofenceId + "-" + dateKey;
                            detailedMap.compute(key, (k, existing) -> {
                                if (existing == null) {
                                    GeofenceTimeRecord record = new GeofenceTimeRecord();
                                    record.setDeviceId(device.getId());
                                    record.setDeviceName(device.getName());
                                    record.setGeofenceId(geofenceId);
                                    String geofenceName = "Unknown";
                                    try {
                                        Geofence geofence = storage.getObject(Geofence.class, new Request(
                                                new Columns.Include("name"),
                                                new Condition.Equals("id", geofenceId)));
                                        if (geofence != null) {
                                            geofenceName = geofence.getName();
                                        }
                                    } catch (StorageException e) {
                                        // Optionally log the error
                                        System.err.println(
                                                "Failed to fetch geofence name for ID " + geofenceId + ": " + e.getMessage());
                                    }
                                    record.setGeofenceName(geofenceName);
                                    record.setDuration(durationSeconds);
                                    record.setDate(java.sql.Date.valueOf(dateKey));
                                    return record;
                                } else {
                                    existing.setDuration(existing.getDuration() + durationSeconds);
                                    return existing;
                                }
                            });

                        }
                    }
                }
            }
        }

        if (grouped && groupedMap.isEmpty() || !grouped && detailedMap.isEmpty()) {
            throw new IllegalArgumentException("No geofence data available for the selected devices and time period.");
        }

        return grouped ? groupedMap.values() : detailedMap.values();
    }
}
