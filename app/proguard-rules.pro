# Type-safe Navigation looks up route serializers reflectively (serializer<T>() on the KClass).
# R8 otherwise strips the generated serializer()/INSTANCE of routes that are never serialized directly.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** INSTANCE;
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
