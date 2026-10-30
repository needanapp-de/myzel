# Myzel: Regeln zusätzlich zu proguard-rules.pro von F-Droid, eingebunden über app/myzel.gradle

# Shizuku.newProcess ist seit der API 13 privat; de.needanapp.myzel.install.PrivilegedShell ruft es
# über Reflexion auf, R8 darf es deshalb nicht entfernen oder umbenennen.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
