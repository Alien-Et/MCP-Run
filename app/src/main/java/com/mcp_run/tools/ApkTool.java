package com.mcp_run.tools;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * APK 反编译/回编译全家桶
 * 包含: APK 解析、反编译、回编译、签名、搜索等
 */
public class ApkTool implements MCPTool {

    private final String toolName;
    private final Context context;

    public ApkTool(String toolName, Context context) {
        this.toolName = toolName;
        this.context = context;
    }

    @Override
    public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "apk_decompile": return "反编译 APK 文件。提取 DEX、资源、签名等信息到指定目录。支持 apktool 格式。";
            case "apk_recompile": return "回编译反编译后的 APK。将修改后的文件重新打包签名。";
            case "apk_info": return "获取 APK 详细信息，包括包名、版本、权限、组件等。";
            case "apk_list_files": return "列出 APK 包含的所有文件。";
            case "apk_extract": return "提取 APK 中的文件到指定目录。";
            case "apk_sign": return "签名 APK 文件。支持 v1/v2 签名。";
            case "apk_search": return "在 APK 中搜索指定文本。支持按文件类型过滤。";
            case "apk_permission": return "提取 APK 中声明的权限列表。";
            default: return "APK 工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (toolName) {
                case "apk_decompile":
                case "apk_recompile":
                case "apk_extract":
                    JSONObject pathProp = new JSONObject();
                    pathProp.put("type", "string");
                    pathProp.put("description", "APK 文件路径");
                    props.put("path", pathProp);
                    JSONObject outProp = new JSONObject();
                    outProp.put("type", "string");
                    outProp.put("description", "输出目录路径");
                    props.put("output", outProp);
                    break;

                case "apk_info":
                case "apk_list_files":
                case "apk_sign":
                case "apk_permission":
                    JSONObject pProp = new JSONObject();
                    pProp.put("type", "string");
                    pProp.put("description", "APK 文件路径");
                    props.put("path", pProp);
                    break;

                case "apk_search":
                    JSONObject sPath = new JSONObject();
                    sPath.put("type", "string");
                    sPath.put("description", "APK 文件路径");
                    props.put("path", sPath);
                    JSONObject sQuery = new JSONObject();
                    sQuery.put("type", "string");
                    sQuery.put("description", "搜索关键词");
                    props.put("query", sQuery);
                    JSONObject sExt = new JSONObject();
                    sExt.put("type", "string");
                    sExt.put("description", "文件扩展名过滤，如 .xml,.smali（可选）");
                    props.put("extension", sExt);
                    break;
            }

            schema.put("properties", props);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "apk_decompile": return execApkDecompile(args);
            case "apk_recompile": return execApkRecompile(args);
            case "apk_info": return execApkInfo(args);
            case "apk_list_files": return execApkListFiles(args);
            case "apk_extract": return execApkExtract(args);
            case "apk_sign": return execApkSign(args);
            case "apk_search": return execApkSearch(args);
            case "apk_permission": return execApkPermission(args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    // ========== 实现 ==========

    private JSONObject execApkDecompile(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        String outputDir = args.getString("output");
        File apkFile = new File(apkPath);
        File outDir = new File(outputDir);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);
        if (!apkFile.getName().endsWith(".apk")) throw new Exception("文件不是 APK 格式");

        if (!outDir.exists()) outDir.mkdirs();

        // 尝试使用 apktool
        String apktoolPath = findApktool();
        if (apktoolPath != null) {
            return execApktool(apktoolPath, apkPath, outDir.getAbsolutePath());
        }

        // 回退到基本解包
        return execBasicDecompile(apkPath, outDir.getAbsolutePath());
    }

    private JSONObject execApkRecompile(JSONObject args) throws Exception {
        String inputDir = args.getString("path");
        String outputPath = args.getString("output");
        File inDir = new File(inputDir);
        File outFile = new File(outputPath);

        if (!inDir.exists()) throw new Exception("输入目录不存在: " + inputDir);
        if (!inDir.isDirectory()) throw new Exception("输入路径不是目录");

        // 尝试使用 apktool
        String apktoolPath = findApktool();
        if (apktoolPath != null) {
            return execApktoolBuild(apktoolPath, inDir.getAbsolutePath(), outFile.getAbsolutePath());
        }

        // 回退到基本打包
        return execBasicRecompile(inDir.getAbsolutePath(), outFile.getAbsolutePath());
    }

    private JSONObject execApkInfo(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        File apkFile = new File(apkPath);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);

        JSONObject result = new JSONObject();
        result.put("path", apkFile.getAbsolutePath());
        result.put("size", apkFile.length());
        result.put("size_human", formatSize(apkFile.length()));
        result.put("last_modified", apkFile.lastModified());

        // 尝试通过 PackageManager 获取信息
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (pi != null) {
                result.put("package_name", pi.packageName);
                result.put("version_name", pi.versionName);
                result.put("version_code", pi.versionCode);
                result.put("first_install_time", pi.firstInstallTime);
                result.put("last_update_time", pi.lastUpdateTime);

                // 权限
                if (pi.requestedPermissions != null) {
                    JSONArray perms = new JSONArray();
                    for (String perm : pi.requestedPermissions) {
                        perms.put(perm);
                    }
                    result.put("permissions", perms);
                }

                // Activities
                if (pi.activities != null) {
                    JSONArray activities = new JSONArray();
                    for (ActivityInfo ai : pi.activities) {
                        JSONObject act = new JSONObject();
                        act.put("name", ai.name);
                        act.put("label", ai.labelRes != 0 ? "string资源" : "无标签");
                        activities.put(act);
                    }
                    result.put("activities", activities);
                }

                // Services
                if (pi.services != null) {
                    JSONArray services = new JSONArray();
                    for (ServiceInfo si : pi.services) {
                        JSONObject svc = new JSONObject();
                        svc.put("name", si.name);
                        services.put(svc);
                    }
                    result.put("services", services);
                }

                // Receivers
                if (pi.receivers != null) {
                    JSONArray receivers = new JSONArray();
                    for (ActivityInfo ri : pi.receivers) {
                        JSONObject rec = new JSONObject();
                        rec.put("name", ri.name);
                        receivers.put(rec);
                    }
                    result.put("receivers", receivers);
                }
            }
        } catch (Exception e) {
            result.put("package_info_error", e.getMessage());
        }

        // 从 ZIP 读取基本文件列表
        try (ZipFile zf = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int dexCount = 0;
            long totalSize = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("classes") && entry.getName().endsWith(".dex")) {
                    dexCount++;
                }
                totalSize += entry.getSize();
            }
            result.put("dex_count", dexCount);
            result.put("total_entries", totalSize > 0 ? "many" : 0);
        } catch (Exception e) {
            result.put("zip_read_error", e.getMessage());
        }

        return result;
    }

    private JSONObject execApkListFiles(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        File apkFile = new File(apkPath);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);

        JSONArray files = new JSONArray();
        long totalSize = 0;

        try (ZipFile zf = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                JSONObject item = new JSONObject();
                item.put("name", entry.getName());
                item.put("size", entry.getSize());
                item.put("compressed_size", entry.getCompressedSize());
                item.put("is_directory", entry.isDirectory());
                item.put("last_modified", entry.getTime());
                files.put(item);
                totalSize += entry.getSize();
            }
        }

        JSONObject result = new JSONObject();
        result.put("path", apkFile.getAbsolutePath());
        result.put("total_files", files.length());
        result.put("total_size", totalSize);
        result.put("files", files);
        return result;
    }

    private JSONObject execApkExtract(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        String outputDir = args.getString("output");
        File apkFile = new File(apkPath);
        File outDir = new File(outputDir);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);
        if (!outDir.exists()) outDir.mkdirs();

        int count = 0;
        try (ZipFile zf = new ZipFile(apkFile);
             InputStream in = null) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = new File(outDir, entry.getName());
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }
                destFile.getParentFile().mkdirs();
                try (InputStream is = zf.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
                count++;
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("apk_path", apkFile.getAbsolutePath());
        result.put("output_dir", outDir.getAbsolutePath());
        result.put("extracted_count", count);
        return result;
    }

    /**
     * 签名 APK
     * 优先使用 apksigner > jarsigner > 复制（未签名）
     */
    private JSONObject execApkSign(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        File apkFile = new File(apkPath);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);

        // 尝试 apksigner
        String apksignerPath = findApksigner();
        if (apksignerPath != null) {
            return execSignWithTool(apksignerPath, apkPath, "apksigner", true);
        }

        // 尝试 jarsigner
        String jarsignerPath = findJarsigner();
        if (jarsignerPath != null) {
            return execSignWithTool(jarsignerPath, apkPath, "jarsigner", false);
        }

        // 无签名工具，复制 APK
        String outputPath = apkPath.replace(".apk", "_unsigned.apk");
        java.nio.file.Files.copy(apkFile.toPath(), new File(outputPath).toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("apk_path", apkPath);
        result.put("signed_path", outputPath);
        result.put("signed_size", new File(outputPath).length());
        result.put("tool", "copy");
        result.put("warning", "⚠️ 未签名 - 设备上未找到 apksigner/jarsigner，请在电脑上用 Android Studio 签名");
        return result;
    }

    private JSONObject execSignWithTool(String toolPath, String apkPath, String toolName, boolean isApksigner) throws Exception {
        String outputPath = apkPath.replace(".apk", "_signed.apk");
        String[] cmd;
        String keystorePath = "/workspace/MCP-Run/keystore/mcp_run.keystore";

        if (isApksigner) {
            cmd = new String[]{
                toolPath, "sign",
                "--ks", keystorePath,
                "--ks-pass", "pass:mcp_run_2024",
                "--ks-key-alias", "mcp_run",
                "--key-pass", "pass:mcp_run_2024",
                "--out", outputPath,
                apkPath
            };
        } else {
            cmd = new String[]{
                toolPath,
                "-keystore", keystorePath,
                "-storepass", "mcp_run_2024",
                "-keypass", "mcp_run_2024",
                "-signedjar", outputPath,
                apkPath, "mcp_run"
            };
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int exitCode = p.waitFor();

        JSONObject result = new JSONObject();
        result.put("success", exitCode == 0);
        result.put("apk_path", apkPath);
        result.put("signed_path", outputPath);
        result.put("exit_code", exitCode);
        result.put("tool", toolName);
        return result;
    }

    private JSONObject execApkSearch(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        String query = args.getString("query");
        String extension = args.has("extension") ? args.getString("extension") : "";
        File apkFile = new File(apkPath);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);

        JSONArray matches = new JSONArray();

        try (ZipFile zf = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                // 过滤扩展名
                if (!extension.isEmpty() && !entry.getName().toLowerCase().endsWith(extension.toLowerCase())) {
                    continue;
                }

                // 只搜索文本文件
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".xml") && !name.endsWith(".smali") && 
                    !name.endsWith(".txt") && !name.endsWith(".json") &&
                    !name.endsWith(".properties") && !name.endsWith(".java")) {
                    continue;
                }

                try (InputStream is = zf.getInputStream(entry)) {
                    byte[] data = new byte[is.available()];
                    is.read(data);
                    String content = new String(data, "UTF-8");
                    if (content.contains(query)) {
                        JSONObject match = new JSONObject();
                        match.put("file", entry.getName());
                        match.put("size", entry.getSize());
                        match.put("query", query);
                        matches.put(match);
                    }
                } catch (Exception e) {
                    // 跳过无法读取的文件
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("apk_path", apkFile.getAbsolutePath());
        result.put("query", query);
        result.put("extension", extension);
        result.put("match_count", matches.length());
        result.put("matches", matches);
        return result;
    }

    private JSONObject execApkPermission(JSONObject args) throws Exception {
        String apkPath = args.getString("path");
        File apkFile = new File(apkPath);

        if (!apkFile.exists()) throw new Exception("APK 文件不存在: " + apkPath);

        JSONObject result = new JSONObject();
        result.put("path", apkFile.getAbsolutePath());

        // 通过 PackageManager 获取
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (pi != null && pi.requestedPermissions != null) {
                JSONArray perms = new JSONArray();
                for (String perm : pi.requestedPermissions) {
                    perms.put(perm);
                }
                result.put("requested_permissions", perms);
                result.put("permission_count", perms.length());
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        // 从 AndroidManifest.xml 读取
        try (ZipFile zf = new ZipFile(apkFile)) {
            ZipEntry manifestEntry = zf.getEntry("AndroidManifest.xml");
            if (manifestEntry != null) {
                result.put("has_manifest", true);
                result.put("manifest_size", manifestEntry.getSize());
            }
        } catch (Exception e) {
            result.put("manifest_error", e.getMessage());
        }

        return result;
    }

    // ========== 工具方法 ==========

    private String findApktool() {
        String[] paths = {
            "/data/local/tmp/apktool",
            "/system/bin/apktool",
            "/system/xbin/apktool",
            "apktool"
        };
        for (String path : paths) {
            if (new File(path).exists()) return path;
        }
        return null;
    }

    private String findApksigner() {
        String[] paths = {
            "/data/local/tmp/apksigner",
            "/system/bin/apksigner",
            "apksigner"
        };
        for (String path : paths) {
            if (new File(path).exists()) return path;
        }
        return null;
    }

    private String findJarsigner() {
        String[] paths = {
            "/system/bin/jarsigner",
            "jarsigner"
        };
        for (String path : paths) {
            if (new File(path).exists()) return path;
        }
        return null;
    }

    private JSONObject execApktool(String apktoolPath, String apkPath, String outDir) throws Exception {
        String[] cmd = {apktoolPath, "d", "-f", "-o", outDir, apkPath};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int exitCode = p.waitFor();

        JSONObject result = new JSONObject();
        result.put("success", exitCode == 0);
        result.put("apk_path", apkPath);
        result.put("output_dir", outDir);
        result.put("exit_code", exitCode);
        result.put("tool", "apktool");
        return result;
    }

    private JSONObject execApktoolBuild(String apktoolPath, String inDir, String outApk) throws Exception {
        String[] cmd = {apktoolPath, "b", inDir, "-o", outApk};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process p = pb.start();
        int exitCode = p.waitFor();

        JSONObject result = new JSONObject();
        result.put("success", exitCode == 0);
        result.put("input_dir", inDir);
        result.put("output_apk", outApk);
        result.put("exit_code", exitCode);
        result.put("tool", "apktool");
        return result;
    }

    private JSONObject execBasicDecompile(String apkPath, String outDir) throws Exception {
        File apkFile = new File(apkPath);
        File outDirFile = new File(outDir);
        if (!outDirFile.exists()) outDirFile.mkdirs();

        int count = 0;
        try (ZipFile zf = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File destFile = new File(outDir, entry.getName());
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    continue;
                }
                destFile.getParentFile().mkdirs();
                try (InputStream is = zf.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
                count++;
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("apk_path", apkPath);
        result.put("output_dir", outDir);
        result.put("extracted_count", count);
        result.put("tool", "basic_zip");
        return result;
    }

    private JSONObject execBasicRecompile(String inDir, String outApk) throws Exception {
        File inDirFile = new File(inDir);
        File outFile = new File(outApk);
        if (!inDirFile.exists()) throw new Exception("输入目录不存在");
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

        int count = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
            File[] files = inDirFile.listFiles();
            if (files != null) {
                for (File f : files) {
                    count += addToZip(zos, f, "");
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", count > 0);
        result.put("input_dir", inDir);
        result.put("output_apk", outApk);
        result.put("file_count", count);
        result.put("tool", "basic_zip");
        return result;
    }

    private int addToZip(ZipOutputStream zos, File file, String parent) throws IOException {
        String entryName = parent + file.getName();
        int count = 0;
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    count += addToZip(zos, child, entryName + "/");
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
            count++;
        }
        return count;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int unit = 0;
        double size = bytes;
        while (size >= 1024 && unit < 5) {
            size /= 1024;
            unit++;
        }
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        return String.format("%.2f %s", size, units[unit]);
    }
}