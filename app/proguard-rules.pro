# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed for Kotlin, Hilt, etc.)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

#---------------------------------
# OSGI (suppress warnings)
#---------------------------------
-dontwarn org.osgi.framework.BundleActivator
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.Filter
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.InvalidSyntaxException
-dontwarn org.osgi.util.tracker.ServiceTracker
-dontwarn org.osgi.util.tracker.ServiceTrackerCustomizer

#---------------------------------
# Kotlin
#---------------------------------
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

#---------------------------------
# Kotlin Coroutines
#---------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

#---------------------------------
# Hilt / Dagger
#---------------------------------
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <init>(...);
}

#---------------------------------
# Room Database
#---------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.DatabaseConfiguration { *; }

# Keep Room DAOs
-keep interface * extends androidx.room.RoomDatabase$Callback { *; }
-keep class * implements androidx.room.RoomDatabase$Callback { *; }

#---------------------------------
# Firebase
#---------------------------------
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

#---------------------------------
# Navigation Component
#---------------------------------
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.navigation.Navigator { *; }
-keep class * extends androidx.fragment.app.Fragment { *; }

#---------------------------------
# Data Binding
#---------------------------------
-keep class * extends androidx.databinding.DataBinderMapper
-keep class **.databinding.*Binding { *; }
-keep class **.databinding.*BindingImpl { *; }

#---------------------------------
# App Specific - Entities & Models
#---------------------------------
# Keep all entities (Room needs these)
-keep class com.summer.core.data.local.entities.** { *; }
-keep class com.summer.core.android.phone.data.entity.** { *; }
-keep class com.summer.core.data.local.model.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#---------------------------------
# WorkManager
#---------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

#---------------------------------
# Lottie
#---------------------------------
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

#---------------------------------
# Gson (if used)
#---------------------------------
-keepattributes SerializedName
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}