# AMap's renderer uses reflection and JNI. Preserve SDK names/members while optimizing app/Compose code.
-keep class com.amap.api.maps.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# Persistence contracts stay readable independently of runtime class-name obfuscation.
-keep class app.duolian.local.domain.**$$serializer { *; }
-keep class app.duolian.local.data.LocalDatabase_Impl { *; }

# Optional soft-RTK extension referenced by the bundled location implementation, absent from its JAR.
# This app never initializes AMapLocationClient/fusion positioning; it records with Android GPS.
# Do not suppress other SDK dependency failures. FastMath is supplied as a real dependency instead.
-dontwarn com.amap.ams.gnss.GnssSoftLocator
