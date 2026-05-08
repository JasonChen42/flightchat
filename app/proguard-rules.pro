# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfig, minifyEnabled and shrinkResources properties.

-keep class com.flightchat.model.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
