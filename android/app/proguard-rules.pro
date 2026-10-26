# AMap's renderer uses reflection and JNI. Preserve SDK names/members while optimizing app/Compose code.
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.location.** { *; }
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# Persistence contracts stay readable independently of runtime class-name obfuscation.
-keep class app.duolian.local.domain.**$$serializer { *; }
-keep class app.duolian.local.data.LocalDatabase_Impl { *; }

# Missing SDK classes must fail the Release build; do not hide them with dontwarn.
