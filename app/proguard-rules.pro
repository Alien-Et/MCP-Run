# MCP·AC ProGuard Rules

# Keep JSON classes (used by MCP protocol)
-keep class org.json.** { *; }

# Keep our MCP tool classes
-keep class com.mcp_run.** { *; }
-keep class com.mcp_run.tools.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep service classes
-keep class * extends android.app.Service { *; }

# Keep BroadcastReceiver classes (notification actions)
-keep class com.mcp_run.NotificationReceiver { *; }
-keep class com.mcp_run.MCPService$NotificationActionReceiver { *; }

# Keep Runnable implementations
-keep class * implements java.lang.Runnable { *; }

# Keep ScriptTool inner classes (JSConsole, JSContextWrapper)
-keep class com.mcp_run.tools.ScriptTool$JSConsole { *; }
-keep class com.mcp_run.tools.ScriptTool$JSContextWrapper { *; }

# Keep Rhino engine classes (if available)
-keep class javax.script.** { *; }
-dontwarn javax.script.**

# Keep JavaScript engine fallback
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Keep Shizuku classes (optional dependency)
-keep class moe.shizuku.** { *; }
-dontwarn moe.shizuku.**
-keep interface moe.shizuku.** { *; }

# Keep Nashorn classes (optional dependency)
-keep class jdk.nashorn.** { *; }
-dontwarn jdk.nashorn.**

# Keep reflection usage
-keep class java.lang.reflect.** { *; }
-keep class java.io.** { *; }
-keep class java.util.concurrent.** { *; }

# Don't shrink the notification receiver
-dontwarn com.mcp_run.NotificationReceiver
