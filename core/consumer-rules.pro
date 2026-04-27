# Preserve the ALBERT classifier and tokenizer bridge when the core library is
# consumed from a minified app build.
-keep class com.summer.core.ml.model.SmsClassifierModel { *; }
-keep class com.summer.core.ml.tokenizer.HfTokenizerBridge { *; }
-keep class com.summer.core.ml.tokenizer.WordPieceTokenizer { *; }

# JNI entry points must survive shrinking with their method names intact.
-keepclasseswithmembernames class com.summer.core.ml.tokenizer.HfTokenizerBridge {
    native <methods>;
}

# The library owns the ONNX runtime contract used by the classifier.
-keep class ai.onnxruntime.** { *; }
