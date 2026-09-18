package com.mcp_run.tools;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 运动健康工具 - 基于 Health Connect API
 * 需要 Android 14+ (API 34) 和 Health Connect 应用
 * 权限: android.permission.health.READ_HEALTH_DATA
 */
public class HealthTool implements MCPTool {

    private final String toolName;

    public HealthTool(String toolName) {
        this.toolName = toolName;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "get_step_count": return "获取今日步数。需要 Health Connect 权限。";
            case "get_distance": return "获取今日距离（米）。需要 Health Connect 权限。";
            case "get_calories": return "获取今日消耗卡路里。需要 Health Connect 权限。";
            case "get_heart_rate": return "获取最近心率数据。需要 Health Connect 权限。";
            case "get_sleep": return "获取最近睡眠数据。需要 Health Connect 权限。";
            case "get_activity": return "获取今日活动记录。需要 Health Connect 权限。";
            default: return "健康数据查询工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            // limit: 限制返回数量
            JSONObject limitProp = new JSONObject();
            limitProp.put("type", "number");
            limitProp.put("description", "限制返回数量（默认10）");
            limitProp.put("default", 10);
            props.put("limit", limitProp);

            schema.put("properties", props);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        // 检查 Android 版本
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new Exception("Health Connect 需要 Android 14+ (API 34)");
        }

        switch (toolName) {
            case "get_step_count": return getStepCount(context, args);
            case "get_distance": return getDistance(context, args);
            case "get_calories": return getCalories(context, args);
            case "get_heart_rate": return getHeartRate(context, args);
            case "get_sleep": return getSleep(context, args);
            case "get_activity": return getActivity(context, args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    /**
     * 获取今日步数
     */
    private JSONObject getStepCount(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 10);
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.DAYS);

        List<StepCountRecord> records = queryHealthData(context,
            "android.health.STEP_COUNT_DELTA",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_step_count");
        result.put("total_steps", calculateTotalSteps(records));
        result.put("count", records.size());
        result.put("records", formatStepRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    /**
     * 获取今日距离
     */
    private JSONObject getDistance(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 10);
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.DAYS);

        List<DistanceRecord> records = queryHealthData(context,
            "android.health.DISTANCE",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_distance");
        result.put("total_distance_meters", calculateTotalDistance(records));
        result.put("count", records.size());
        result.put("records", formatDistanceRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    /**
     * 获取今日卡路里
     */
    private JSONObject getCalories(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 10);
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.DAYS);

        List<CaloriesBurnedRecord> records = queryHealthData(context,
            "android.health.CALORIES_BURNED",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_calories");
        result.put("total_kcal", calculateTotalCalories(records));
        result.put("count", records.size());
        result.put("records", formatCaloriesRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    /**
     * 获取最近心率数据
     */
    private JSONObject getHeartRate(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 10);
        Instant now = Instant.now();
        Instant start = now.minus(7, ChronoUnit.DAYS); // 最近7天

        List<HeartRateRecord> records = queryHealthData(context,
            "android.health.HEART_RATE",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_heart_rate");
        result.put("count", records.size());
        result.put("records", formatHeartRateRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    /**
     * 获取最近睡眠数据
     */
    private JSONObject getSleep(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 7);
        Instant now = Instant.now();
        Instant start = now.minus(7, ChronoUnit.DAYS); // 最近7天

        List<SleepSessionRecord> records = queryHealthData(context,
            "android.health.SLEEP_SESSION",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_sleep");
        result.put("count", records.size());
        result.put("records", formatSleepRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    /**
     * 获取今日活动记录
     */
    private JSONObject getActivity(Context context, JSONObject args) throws Exception {
        int limit = args.optInt("limit", 10);
        Instant now = Instant.now();
        Instant start = now.minus(1, ChronoUnit.DAYS);

        List<ActiveEnergyBurnedRecord> records = queryHealthData(context,
            "android.health.ACTIVE_ENERGY_BURNED",
            start, now, limit);

        JSONObject result = new JSONObject();
        result.put("tool", "get_activity");
        result.put("count", records.size());
        result.put("records", formatActivityRecords(records));
        result.put("date_range", start.toString() + " ~ " + now.toString());
        return result;
    }

    // ========== Health Connect 查询方法 ==========

    /**
     * 通用 Health Connect 数据查询
     * 注意：这是简化实现，实际需要使用 HealthConnectClient
     */
    private <T> List<T> queryHealthData(Context context, String dataType,
                                         Instant start, Instant end, int limit) throws Exception {
        List<T> records = new ArrayList<>();
        try {
            // 这里需要使用 HealthConnectClient 进行实际查询
            // 由于 Health Connect API 需要异步回调，这里使用简化实现
            // 实际部署时需要替换为真实的 Health Connect 查询逻辑

            // 模拟数据（实际使用时替换为真实查询）
            // 这里返回空列表，实际应用中需要实现真实的 Health Connect 查询

        } catch (Exception e) {
            throw new Exception("Health Connect 查询失败: " + e.getMessage());
        }
        return records;
    }

    // ========== 数据格式化方法 ==========

    private JSONArray formatStepRecords(List<StepCountRecord> records) {
        JSONArray arr = new JSONArray();
        for (StepCountRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("count", r.getCount());
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private JSONArray formatDistanceRecords(List<DistanceRecord> records) {
        JSONArray arr = new JSONArray();
        for (DistanceRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("distance_meters", r.getDistance());
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private JSONArray formatCaloriesRecords(List<CaloriesBurnedRecord> records) {
        JSONArray arr = new JSONArray();
        for (CaloriesBurnedRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("energy_kcal", r.getEnergy());
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private JSONArray formatHeartRateRecords(List<HeartRateRecord> records) {
        JSONArray arr = new JSONArray();
        for (HeartRateRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("bpm", r.getSamples().isEmpty() ? 0 : r.getSamples().get(0).getBeatsPerMinute());
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private JSONArray formatSleepRecords(List<SleepSessionRecord> records) {
        JSONArray arr = new JSONArray();
        for (SleepSessionRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private JSONArray formatActivityRecords(List<ActiveEnergyBurnedRecord> records) {
        JSONArray arr = new JSONArray();
        for (ActiveEnergyBurnedRecord r : records) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("energy_kcal", r.getEnergy());
                obj.put("start_time", r.getStartTime().toString());
                obj.put("end_time", r.getEndTime().toString());
                obj.put("source", r.getPackageName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            arr.put(obj);
        }
        return arr;
    }

    private long calculateTotalSteps(List<StepCountRecord> records) {
        long total = 0;
        for (StepCountRecord r : records) total += r.getCount();
        return total;
    }

    private double calculateTotalDistance(List<DistanceRecord> records) {
        double total = 0;
        for (DistanceRecord r : records) total += r.getDistance();
        return total;
    }

    private double calculateTotalCalories(List<CaloriesBurnedRecord> records) {
        double total = 0;
        for (CaloriesBurnedRecord r : records) total += r.getEnergy();
        return total;
    }

    // ========== Health Connect 数据类型 ==========

    // 简化的数据类型定义（实际使用时替换为 Health Connect API 类型）
    static class StepCountRecord {
        private final long count;
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public StepCountRecord(long count, Instant startTime, Instant endTime, String packageName) {
            this.count = count;
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public long getCount() { return count; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }

    static class DistanceRecord {
        private final double distance;
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public DistanceRecord(double distance, Instant startTime, Instant endTime, String packageName) {
            this.distance = distance;
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public double getDistance() { return distance; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }

    static class CaloriesBurnedRecord {
        private final double energy;
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public CaloriesBurnedRecord(double energy, Instant startTime, Instant endTime, String packageName) {
            this.energy = energy;
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public double getEnergy() { return energy; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }

    static class HeartRateRecord {
        private final List<HeartRateSample> samples;
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public HeartRateRecord(List<HeartRateSample> samples, Instant startTime, Instant endTime, String packageName) {
            this.samples = samples;
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public List<HeartRateSample> getSamples() { return samples; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }

    static class HeartRateSample {
        private final int beatsPerMinute;
        private final Instant time;

        public HeartRateSample(int bpm, Instant time) {
            this.beatsPerMinute = bpm;
            this.time = time;
        }

        public int getBeatsPerMinute() { return beatsPerMinute; }
        public Instant getTime() { return time; }
    }

    static class SleepSessionRecord {
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public SleepSessionRecord(Instant startTime, Instant endTime, String packageName) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }

    static class ActiveEnergyBurnedRecord {
        private final double energy;
        private final Instant startTime;
        private final Instant endTime;
        private final String packageName;

        public ActiveEnergyBurnedRecord(double energy, Instant startTime, Instant endTime, String packageName) {
            this.energy = energy;
            this.startTime = startTime;
            this.endTime = endTime;
            this.packageName = packageName;
        }

        public double getEnergy() { return energy; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public String getPackageName() { return packageName; }
    }
}