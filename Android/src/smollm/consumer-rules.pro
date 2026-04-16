# Keep native methods
-keepclasseswithmembernames class com.jegly.offlineLLM.smollm.** {
    native <methods>;
}
-keep class com.jegly.offlineLLM.smollm.SmolLM { *; }
-keep class com.jegly.offlineLLM.smollm.GGUFReader { *; }
