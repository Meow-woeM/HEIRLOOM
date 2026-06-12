# Heirloom release rules.

# kotlinx.serialization: keep serializers for the engine's save schema.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.heirloom.engine.** {
    *** Companion;
}
-keepclasseswithmembers class com.heirloom.engine.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.heirloom.engine.**$$serializer { *; }

# Play Billing
-keep class com.android.vending.billing.** { *; }
