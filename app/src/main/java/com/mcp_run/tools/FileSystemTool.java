package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 文件系统工具集 - 文件/目录导航、管理、搜索
 * 包含: pwd, cd, set_root, exists, stat, ls, list_all, tree, find, grep, mkdir, touch, empty, copy, rename, delete, edit
 */
public class FileSystemTool implements MCPTool {
    private final String toolName;
    private final FileSession session;

    public FileSystemTool(String toolName, FileSession session) {
        this.toolName = toolName;
        this.session = session;
    }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "pwd": return "查看当前工作目录路径";
            case "cd": return "切换当前工作目录，支持相对路径和绝对路径";
            case "set_root": return "设置工作根目录，同时会将当前目录切换到根目录";
            case "exists": return "检查文件或目录是否存在";
            case "stat": return "查看文件/目录的详细信息（大小、修改时间、权限等）";
            case "ls": return "查看目录内容列表，按名称排序";
            case "list_all": return "查看目录全部内容，包括隐藏文件（以.开头的文件）";
            case "tree": return "以树形结构查看目录内容，展示层级关系";
            case "find": return "按文件名关键词在指定目录中递归搜索文件";
            case "grep": return "在文件内容中搜索指定文本，支持递归搜索目录";
            case "mkdir": return "创建目录，支持递归创建多级目录";
            case "touch": return "创建空文件，如果文件已存在则更新其最后修改时间";
            case "empty": return "清空文件内容（将文件截断为0字节）";
            case "copy": return "复制文件或目录到目标路径";
            case "rename": return "重命名或移动文件/目录";
            case "delete": return "删除文件或空目录，支持递归删除";
            case "edit": return "替换文件中指定文本内容（支持简单的字符串替换）";
            default: return "文件系统操作工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (toolName) {
                case "pwd": break;
                case "cd": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "目标目录路径（相对或绝对）"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "set_root": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "根目录路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "exists": case "stat": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件或目录路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "ls": case "list_all": case "tree": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "目录路径（可选，默认当前目录）"); p.put("default", ""); props.put("path", p);
                    JSONObject d = new JSONObject(); d.put("type", "number"); d.put("description", "递归最大深度（tree/ls -r 时使用）"); d.put("default", -1); props.put("depth", d);
                    break;
                }
                case "find": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "搜索根目录"); props.put("path", p);
                    JSONObject n = new JSONObject(); n.put("type", "string"); n.put("description", "文件名关键词"); props.put("name", n);
                    schema.put("required", new JSONArray().put("path").put("name")); break;
                }
                case "grep": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "搜索根目录"); props.put("path", p);
                    JSONObject q = new JSONObject(); q.put("type", "string"); q.put("description", "搜索文本"); props.put("query", q);
                    schema.put("required", new JSONArray().put("path").put("query")); break;
                }
                case "mkdir": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "要创建的目录路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "touch": case "empty": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "copy": case "rename": {
                    JSONObject s = new JSONObject(); s.put("type", "string"); s.put("description", "源路径"); props.put("source", s);
                    JSONObject d = new JSONObject(); d.put("type", "string"); d.put("description", "目标路径"); props.put("destination", d);
                    schema.put("required", new JSONArray().put("source").put("destination")); break;
                }
                case "delete": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "要删除的文件或目录路径"); props.put("path", p);
                    JSONObject r = new JSONObject(); r.put("type", "boolean"); r.put("description", "是否递归删除目录"); r.put("default", false); props.put("recursive", r);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "edit": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    JSONObject f = new JSONObject(); f.put("type", "string"); f.put("description", "要查找的文本"); props.put("find", f);
                    JSONObject r = new JSONObject(); r.put("type", "string"); r.put("description", "替换为的文本"); props.put("replace", r);
                    schema.put("required", new JSONArray().put("path").put("find").put("replace")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "pwd": return execPwd();
            case "cd": return execCd(args);
            case "set_root": return execSetRoot(args);
            case "exists": return execExists(args);
            case "stat": return execStat(args);
            case "ls": return execLs(args, false);
            case "list_all": return execLs(args, true);
            case "tree": return execTree(args);
            case "find": return execFind(args);
            case "grep": return execGrep(args);
            case "mkdir": return execMkdir(args);
            case "touch": return execTouch(args);
            case "empty": return execEmpty(args);
            case "copy": return execCopy(args);
            case "rename": return execRename(args);
            case "delete": return execDelete(args);
            case "edit": return execEdit(args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private JSONObject resultOk() throws Exception {
        JSONObject r = new JSONObject(); r.put("success", true); return r;
    }

    // ========== 实现 ==========

    private JSONObject execPwd() throws Exception {
        JSONObject r = new JSONObject();
        r.put("current_dir", session.getCurrentDir());
        r.put("root_dir", session.getRootDir());
        return r;
    }

    private JSONObject execCd(JSONObject args) throws Exception {
        String path = args.getString("path");
        String oldDir = session.getCurrentDir();
        session.setCurrentDir(path);
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("old_dir", oldDir);
        r.put("new_dir", session.getCurrentDir());
        return r;
    }

    private JSONObject execSetRoot(JSONObject args) throws Exception {
        String path = args.getString("path");
        String oldRoot = session.getRootDir();
        session.setRootDir(path);
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("old_root", oldRoot);
        r.put("new_root", session.getRootDir());
        r.put("current_dir", session.getCurrentDir());
        return r;
    }

    private JSONObject execExists(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        File f = new File(path);
        JSONObject r = new JSONObject();
        r.put("exists", f.exists());
        r.put("path", f.getAbsolutePath());
        r.put("is_file", f.isFile());
        r.put("is_directory", f.isDirectory());
        return r;
    }

    private JSONObject execStat(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        File f = new File(path);
        if (!f.exists()) throw new Exception("路径不存在: " + path);
        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("name", f.getName());
        r.put("exists", true);
        r.put("is_file", f.isFile());
        r.put("is_directory", f.isDirectory());
        r.put("is_hidden", f.isHidden());
        r.put("size", f.length());
        r.put("last_modified", f.lastModified());
        r.put("can_read", f.canRead());
        r.put("can_write", f.canWrite());
        r.put("can_execute", f.canExecute());
        r.put("parent", f.getParent());
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            r.put("children_count", children != null ? children.length : 0);
        }
        return r;
    }

    private JSONObject execLs(JSONObject args, boolean all) throws Exception {
        String path = args.optString("path", "");
        if (path.isEmpty()) path = session.getCurrentDir();
        else path = session.resolve(path);
        int depth = args.optInt("depth", -1);

        File dir = new File(path);
        if (!dir.exists()) throw new Exception("目录不存在: " + path);
        if (!dir.isDirectory()) throw new Exception("不是目录: " + path);

        File[] files = dir.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));

        JSONArray items = new JSONArray();
        for (File f : files) {
            if (!all && f.isHidden()) continue;
            JSONObject item = new JSONObject();
            item.put("name", f.getName());
            item.put("path", f.getAbsolutePath());
            item.put("is_directory", f.isDirectory());
            item.put("is_file", f.isFile());
            item.put("size", f.length());
            item.put("last_modified", f.lastModified());
            item.put("is_hidden", f.isHidden());
            items.put(item);
        }

        JSONObject r = new JSONObject();
        r.put("path", dir.getAbsolutePath());
        r.put("total", items.length());
        r.put("entries", items);
        return r;
    }

    private JSONObject execTree(JSONObject args) throws Exception {
        String path = args.optString("path", "");
        if (path.isEmpty()) path = session.getCurrentDir();
        else path = session.resolve(path);
        int maxDepth = args.optInt("depth", 3);

        File dir = new File(path);
        if (!dir.exists()) throw new Exception("目录不存在: " + path);
        if (!dir.isDirectory()) throw new Exception("不是目录: " + path);

        StringBuilder tree = new StringBuilder();
        tree.append(dir.getAbsolutePath()).append("\n");
        buildTree(dir, "", tree, maxDepth, 0);

        JSONObject r = new JSONObject();
        r.put("path", dir.getAbsolutePath());
        r.put("tree", tree.toString());
        return r;
    }

    private void buildTree(File dir, String prefix, StringBuilder sb, int maxDepth, int depth) {
        if (maxDepth >= 0 && depth >= maxDepth) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (int i = 0; i < files.length; i++) {
            boolean last = (i == files.length - 1);
            sb.append(prefix).append(last ? "└── " : "├── ").append(files[i].getName());
            if (files[i].isDirectory()) sb.append("/");
            sb.append("\n");
            if (files[i].isDirectory()) {
                buildTree(files[i], prefix + (last ? "    " : "│   "), sb, maxDepth, depth + 1);
            }
        }
    }

    private JSONObject execFind(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        String name = args.getString("name").toLowerCase();
        JSONArray results = new JSONArray();
        findFiles(new File(path), name, results);
        JSONObject r = new JSONObject();
        r.put("path", path);
        r.put("query", name);
        r.put("total", results.length());
        r.put("results", results);
        return r;
    }

    private void findFiles(File dir, String name, JSONArray results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().contains(name)) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("name", f.getName());
                    item.put("path", f.getAbsolutePath());
                    item.put("is_directory", f.isDirectory());
                    item.put("size", f.length());
                    results.put(item);
                } catch (Exception ignored) {}
            }
            if (f.isDirectory()) findFiles(f, name, results);
        }
    }

    private JSONObject execGrep(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        String query = args.getString("query");
        JSONArray results = new JSONArray();
        grepFiles(new File(path), query, results);
        JSONObject r = new JSONObject();
        r.put("path", path);
        r.put("query", query);
        r.put("total", results.length());
        r.put("results", results);
        return r;
    }

    private void grepFiles(File file, String query, JSONArray results) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) return;
            for (File f : files) grepFiles(f, query, results);
            return;
        }
        if (!file.isFile() || file.length() > 1024 * 1024) return;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.contains(query)) {
                    JSONObject match = new JSONObject();
                    match.put("file", file.getAbsolutePath());
                    match.put("line", lineNum);
                    match.put("content", line.trim());
                    results.put(match);
                }
            }
            br.close();
        } catch (Exception ignored) {}
    }

    private JSONObject execMkdir(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        File dir = new File(path);
        if (dir.exists()) throw new Exception("路径已存在: " + path);
        boolean created = dir.mkdirs();
        JSONObject r = new JSONObject();
        r.put("success", created);
        r.put("path", dir.getAbsolutePath());
        r.put("message", created ? "目录已创建" : "创建失败");
        return r;
    }

    private JSONObject execTouch(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        File f = new File(path);
        boolean existed = f.exists();
        if (!existed) {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            f.createNewFile();
        } else {
            f.setLastModified(System.currentTimeMillis());
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("created", !existed);
        r.put("message", existed ? "已更新时间戳" : "已创建文件");
        return r;
    }

    private JSONObject execEmpty(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);
        if (!f.isFile()) throw new Exception("不是文件: " + path);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            fos.write(new byte[0]);
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("message", "文件已清空");
        return r;
    }

    private JSONObject execCopy(JSONObject args) throws Exception {
        String src = session.resolve(args.getString("source"));
        String dst = session.resolve(args.getString("destination"));
        File srcFile = new File(src);
        File dstFile = new File(dst);
        if (!srcFile.exists()) throw new Exception("源路径不存在: " + src);
        if (dstFile.exists()) throw new Exception("目标已存在: " + dst);
        if (dstFile.getParentFile() != null) dstFile.getParentFile().mkdirs();
        copyRecursive(srcFile, dstFile);
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("source", srcFile.getAbsolutePath());
        r.put("destination", dstFile.getAbsolutePath());
        r.put("is_directory", srcFile.isDirectory());
        return r;
    }

    private void copyRecursive(File src, File dst) throws Exception {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) for (File c : children) copyRecursive(c, new File(dst, c.getName()));
        } else {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(src);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(dst);
                 java.nio.channels.FileChannel in = fis.getChannel();
                 java.nio.channels.FileChannel out = fos.getChannel()) {
                in.transferTo(0, in.size(), out);
            }
        }
    }

    private JSONObject execRename(JSONObject args) throws Exception {
        String src = session.resolve(args.getString("source"));
        String dst = session.resolve(args.getString("destination"));
        File srcFile = new File(src);
        File dstFile = new File(dst);
        if (!srcFile.exists()) throw new Exception("源路径不存在: " + src);
        if (dstFile.getParentFile() != null) dstFile.getParentFile().mkdirs();
        boolean ok = srcFile.renameTo(dstFile);
        if (!ok) {
            copyRecursive(srcFile, dstFile);
            deleteRecursive(srcFile);
            ok = true;
        }
        JSONObject r = new JSONObject();
        r.put("success", ok);
        r.put("source", srcFile.getAbsolutePath());
        r.put("destination", dstFile.getAbsolutePath());
        return r;
    }

    private JSONObject execDelete(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        boolean recursive = args.optBoolean("recursive", false);
        File f = new File(path);
        if (!f.exists()) throw new Exception("路径不存在: " + path);
        boolean deleted;
        if (f.isDirectory() && recursive) {
            deleteRecursive(f);
            deleted = !f.exists();
        } else {
            deleted = f.delete();
        }
        JSONObject r = new JSONObject();
        r.put("success", deleted);
        r.put("path", f.getAbsolutePath());
        r.put("was_directory", f.isDirectory());
        r.put("message", deleted ? "已删除" : "删除失败" + (f.isDirectory() && !recursive ? "（目录非空，请使用recursive=true）" : ""));
        return r;
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private JSONObject execEdit(JSONObject args) throws Exception {
        String path = session.resolve(args.getString("path"));
        String find = args.getString("find");
        String replace = args.optString("replace", "");
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);
        String content = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        int count = 0;
        String newContent = content.replace(find, replace);
        int idx = 0;
        while ((idx = content.indexOf(find, idx)) >= 0) { count++; idx += find.length(); }
        java.nio.file.Files.write(f.toPath(), newContent.getBytes("UTF-8"));
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("replacements", count);
        r.put("message", "已替换 " + count + " 处");
        return r;
    }
}