# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

#---------------------------------
# Debugging - Keep line numbers for crash reports
#---------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (needed for Kotlin, Hilt, Gson, etc.)
-keepattributes Signature,*Annotation*,Exceptions

#---------------------------------
# Suppress Warnings
#---------------------------------
-dontwarn org.osgi.**
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn dagger.hilt.**
-dontwarn androidx.room.paging.**
-dontwarn androidx.paging.**
-dontwarn com.airbnb.lottie.**

#---------------------------------
# Kotlin
#---------------------------------
-keep class kotlin.Metadata { *; }

#---------------------------------
# Kotlin Coroutines
#---------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

#---------------------------------
# Hilt / Dagger
#---------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

#---------------------------------
# Room Database
#---------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

#---------------------------------
# Navigation Component
#---------------------------------
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.fragment.app.Fragment { *; }
-keep class **Args { *; }
-keep class **Directions { *; }

#---------------------------------
# Data Binding
#---------------------------------
-keep class * extends androidx.databinding.DataBinderMapper
-keep class **.databinding.*Binding { *; }

#---------------------------------
# WorkManager
#---------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

#---------------------------------
# ONNX Runtime (CRITICAL - JNI needs full class names)
#---------------------------------
-keep class ai.onnxruntime.** { *; }

#---------------------------------
# Enums, Parcelable, Serializable
#---------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
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
# ViewModels
#---------------------------------
-keep class * extends androidx.lifecycle.ViewModel { *; }

#---------------------------------
# App Specific
#---------------------------------
-keep class com.summer.notifai.App { *; }
-keep class com.summer.notifai.ui.** { *; }
-keep class com.summer.notifai.service.** { *; }

# Core module - entities and ML
-keep class com.summer.core.data.local.entities.** { *; }
-keep class com.summer.core.data.local.model.** { *; }
-keep class com.summer.core.android.phone.data.entity.** { *; }
-keep class com.summer.core.ml.** { *; }

# Keep DataBinding BR classes
-keep class com.summer.notifai.BR { *; }
-keep class com.summer.core.BR { *; }