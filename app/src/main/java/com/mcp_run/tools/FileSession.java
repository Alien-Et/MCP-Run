package com.mcp_run.tools;

import java.io.File;

/**
 * 文件会话 - 管理当前工作目录和根目录状态
 */
public class FileSession {
    private String rootDir = "/storage/emulated/0/";
    private String currentDir = "/storage/emulated/0/";

    public String getRootDir() { return rootDir; }
    public String getCurrentDir() { return currentDir; }

    public void setRootDir(String path) {
        File f = new File(path);
        if (f.exists() && f.isDirectory()) {
            rootDir = f.getAbsolutePath();
            currentDir = rootDir;
        }
    }

    public void setCurrentDir(String path) {
        File f = new File(path);
        if (f.isAbsolute()) {
            if (f.exists() && f.isDirectory()) {
                currentDir = f.getAbsolutePath();
            }
        } else {
            f = new File(currentDir, path);
            if (f.exists() && f.isDirectory()) {
                currentDir = f.getAbsolutePath();
            }
        }
    }

    /** 解析路径：相对路径转为绝对路径 */
    public String resolve(String path) {
        if (path == null || path.isEmpty()) return currentDir;
        File f = new File(path);
        if (f.isAbsolute()) return f.getAbsolutePath();
        return new File(currentDir, path).getAbsolutePath();
    }
}