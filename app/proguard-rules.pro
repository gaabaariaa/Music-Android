# TagLib looks up its Java classes and fields by name from native code.
-keep class com.kyant.taglib.** { *; }

# Room, Hilt, WorkManager and Media3 ship their own consumer rules. Entities and DAOs are kept by Room's
# generated code; these lines only silence warnings from optional dependencies.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# Readable stack traces from user reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
