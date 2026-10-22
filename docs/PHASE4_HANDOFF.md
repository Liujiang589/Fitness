# Fourth-stage build checkpoint - 2026-10-20 21:52 Asia/Shanghai

- Independent app: app.duolian.local, version 0.2.0-dev; original APK/app data and HTML preview untouched.
- Full Gradle build SUCCEEDED: testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest.
- Real JUnit results: 15 passed, 0 failed/errors/skipped. Five device tests compiled but NOT run; no device installation or runtime acceptance.
- Lint: 0 errors, 2 warnings (DataExtractionRules and MissingApplicationIcon). Do not describe these warnings as errors or all QA as complete.
- APK signature, alignment, ZIP CRC, package id and API 26/35 bounds verified; no INTERNET permission.
- App APK: outputs/Duolian-0.2.0-debug.apk (11,261,456 bytes).
- APK SHA-256: accfa9345927bb6e51bfd9d1364db6a4149b5419e794be6e82e0dcea3568fcdd.
- Current sources, device-test APK and evidence: outputs/Duolian_Android_Phase4_Source_20261020_Built.zip.
- Detailed report: docs/Duolian_Phase4_Build_Result.md; machine evidence: analysis/phase4/build-verification.json.
- Tool installation, SDK licenses and dependency downloads were ALREADY authorized. Do not re-ask for them.
- Tools are isolated in XunjiLab/toolchain. JDK 17.0.20.1, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.0.21, KSP 2.0.21-1.0.28, SDK 35/base revision 2.
- Fixed KAPT path-resolution failure by migrating to KSP2; fixed Windows Gradle-worker encoding by removing forced UTF-8; custom project-local debug signing; API 27 navigation attribute moved to values-v27.
- Local private signing key must be preserved for future upgrades; it is excluded from deliverables.
- SDK license-scope correction was disclosed; one required active license, seven extra license records backed up. This does not undo their previous acceptance.
- Final APK/source/report Library saving FAILED before preparation: hosted apps tools/list HTTP 403, including a retry with freshly fetched helpers. No current delivery was saved remotely. The earlier source-only Library item is historical and is NOT this build.
- Next substantive step: arrange authorized clean-device acceptance (offline persistence, process recreation, SAF backup/restore, UI and timers). Do not install on a phone without the corresponding authorization.

## Physical-device checkpoint - 2026-10-21
- Sandbox local execution recovered; ADB sees an authorized Xiaomi 22011211C on Android 14.
- Independent app was not installed. APK hash matches the verified build.
- App installation was rejected by phone: INSTALL_FAILED_USER_RESTRICTED (Install canceled by user). No instrumentation tests have run; original app untouched.
- Await user enabling/confirming USB install on the phone, then retry installation and run the five compiled device tests. Installation and independent-app acceptance data reset were already authorized in this conversation.
- Evidence: analysis/phase4/device-acceptance-status.json.


## Physical-device results - 2026-10-21
- Main APK and test APK installed successfully.
- All four StorageTest cases passed on the phone. First full suite was interrupted by agent force-stop after the UI phase stalled; retain the incomplete instrumentation result, do not call it a fully passed suite.
- TrainingUiTest retry PASSED: OK (1 test), 47.539 seconds. Five cases passed across the two runs.
- Normal cold launch succeeded (766ms). Restarted app still displayed one completed training; process persistence verified.
- Remaining SAF export/import manual-path checks BLOCKED: adb input tap rejected with INJECT_EVENTS SecurityException.
- Test-fixture cleanup attempt pm clear was also rejected: CLEAR_APP_USER_DATA SecurityException. No alternative bypass attempted.
- CURRENT APP DATA IS STILL TEST FIXTURE (one exercise, one plan, one completed 400kg session), not the normal 40-action starter dataset. Must clean this after user enables the appropriate phone debugging permission or clears app data manually.
- No production source/build changes; original app untouched. Machine evidence and instrumentation logs are in analysis/phase4.


## Current revision: 0.3.0-dev (2026-10-21)
- Read UI03_HANDOFF.md first; it supersedes the earlier 0.2 UI and fixture-cleanup status.
- User rejected the earlier basic UI/function completeness. Native home, catalog, training, history, profile and tools pages revised; source in app/android-native.
- Final build passed four Gradle tasks; 15 unit tests and one full 5-case phone run passed. Normal 40-action starter data restored from exact old test-only fixture.
- Latest APK outputs/Duolian-0.3.0-debug.apk. Old 0.2 source ZIP is historical and does not contain current UI.
- Still not full parity: complex plans/sets, HIIT, authorized exercise media, notifications and challenges remain gaps.

