# Native device regressions

Validated on Samsung SM-A065M, Android 16, arm64-v8a, 4096-byte pages.

Using an NDK clang targeting `aarch64-linux-android24`, compile and run:

```sh
"$CC" -O2 -fstack-protector-strong macho2elf/android_setjmp.c android/tests/setjmp-device.c -o setjmp-test
"$CC" -O2 -fstack-protector-strong macho2elf/android_pthread.c android/tests/pthread-device.c -o pthread-test
adb push setjmp-test pthread-test /data/local/tmp/
adb shell chmod 700 /data/local/tmp/setjmp-test /data/local/tmp/pthread-test
adb shell /data/local/tmp/setjmp-test
adb shell /data/local/tmp/pthread-test
```

Expected: PASS for 600 jumps across three threads with intact Darwin buffers and guard bytes; PASS for 200 joinable workers, both translated detach states, and rejection of an invalid state. These tests exercise the phone's actual libc, not an emulator substitute.

The APK was separately tested for complete es-ES/es-MX readings, language changes, restart and Stop. The user confirmed audible speech. Other languages, long texts and extended screen-reader use remain unvalidated.
