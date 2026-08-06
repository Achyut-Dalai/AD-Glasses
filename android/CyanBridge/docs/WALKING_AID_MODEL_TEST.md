# Walking Aid real-model test

The connected-device integration test downloads the catalog YOLO11n and Depth Anything 3 Small
models when they are missing, downloads Ultralytics' public `bus.jpg` fixture, and runs the real
TFLite Walking Aid stack. It verifies detector output, user-focus fuzzy matching against labels in
that output, and Depth Anything inference.

The test is opt-in so the regular Android instrumentation suite does not unexpectedly download
about 60 MB of models. Connect an Android phone with USB debugging enabled, then run:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.fersaiyan.cyanbridge.plugins.walkingaid.WalkingAidVisionStackIntegrationTest \
  -Pandroid.testInstrumentationRunnerArguments.walkingAidRealModels=true
```

The models and image are cached in the instrumentation app sandbox for the duration of the test.
Android's connected-test task may uninstall that debug app after completion. This test intentionally
fails on model incompatibility, unsupported tensor layouts, empty detections, or invalid depth output
so it can be used as an on-device model debugging tool.
