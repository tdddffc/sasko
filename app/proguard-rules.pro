-keep class com.weatherfocus.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**

-keep class com.weatherfocus.app.data.repository.WeatherBundle { *; }
-keep class com.weatherfocus.app.widget.WidgetCache$Stored { *; }
