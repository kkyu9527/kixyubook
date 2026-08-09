# Kixyu Book 性能基准

Baseline Profile 生成与性能回归测试分开维护：

- `BaselineProfileGenerator` 收集启动和阅读路径的 Profile。
- `ReaderPerformanceBenchmark` 测量冷启动、打开大型 EPUB 和连续翻页。

在已经导入固定测试 EPUB 的真机上运行：

```powershell
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.kixyu9527.kixyubook.baselineprofile.ReaderPerformanceBenchmark `
  -Pandroid.testInstrumentationRunnerArguments.benchmarkBookTitle=测试书名
```

Android Benchmark 会在构建报告和设备测试输出中保存每次迭代结果。
`FrameTimingMetric` 的 `frameDurationCpuMs` 包含 P50、P90、P95 和 P99，版本性能对比以 P50 和 P95 为主；
冷启动保留 10 次原始 `timeToInitialDisplayMs` 数据，避免只比较单次结果。
