/* SPDX-License-Identifier: MIT
 * Standalone native renderer. A fresh native process isolates the converted
 * Apple's engine from the ART/JNI runtime and its stale per-process hooks.
 * stdin: Western encoded text; stdout: ELQ1 header + mono 11025 Hz S16 PCM.
 */
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>

#define CHUNK 8192
#define MAX_SAMPLES (16u * 1024u * 1024u)
typedef void *Handle;
static short chunk[CHUNK], *pcm;
static uint32_t count, capacity;
static int failed;
static int wave(Handle handle, int message, long n, void *data) {
    (void)handle; (void)data;
    if (message != 0 || n <= 0) return 1;
    if (n > CHUNK || (uint64_t)count + n > MAX_SAMPLES) { failed = 1; return 2; }
    uint32_t need = count + (uint32_t)n;
    if (need > capacity) {
        uint32_t next = capacity ? capacity * 2 : 65536;
        while (next < need) next *= 2;
        short *grown = realloc(pcm, (size_t)next * sizeof(short));
        if (!grown) { failed = 1; return 2; }
        pcm = grown; capacity = next;
    }
    memcpy(pcm + count, chunk, (size_t)n * sizeof(short)); count = need;
    return 1;
}
static int write_all(const void *bytes, size_t size) {
    const char *p = bytes;
    while (size) {
        ssize_t n = write(STDOUT_FILENO, p, size);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return 0;
        p += n; size -= (size_t)n;
    }
    return 1;
}
int main(int argc, char **argv) {
    if (argc != 5) return 2;
    signal(SIGALRM, SIG_DFL); alarm(25);
    if (chdir(argv[2]) != 0) return 3;
    int dialect = (int)strtol(argv[3], NULL, 10), values[8];
    char *cursor = argv[4];
    for (int i = 0; i < 8; ++i) {
        char *end; long value = strtol(cursor, &end, 10);
        if (cursor == end || value < 0 || value > (i == 0 ? 1 : i == 6 ? 250 : 100)) return 4;
        values[i] = (int)value;
        if (i < 7 && *end != ',') return 4;
        cursor = end + (i < 7 ? 1 : 0);
    }
    if (*cursor) return 4;
    char text[16385]; size_t length = 0;
    while (length < sizeof(text) - 1) {
        ssize_t n = read(STDIN_FILENO, text + length, sizeof(text) - 1 - length);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) return 5;
        if (!n) break;
        length += (size_t)n;
    }
    if (!length || length == sizeof(text) - 1) return 5;
    text[length] = 0;
    char library[4096];
    if (snprintf(library, sizeof(library), "%s/libeci.so", argv[1]) >= (int)sizeof(library)) return 6;
    void *lib = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
    if (!lib) return 6;
    Handle (*NewEx)(int) = dlsym(lib, "eciNewEx");
    int (*SetParam)(Handle,int,int) = dlsym(lib, "eciSetParam");
    int (*SetVoice)(Handle,int,int,int) = dlsym(lib, "eciSetVoiceParam");
    void (*Register)(Handle,void*,void*) = dlsym(lib, "eciRegisterCallback");
    int (*Buffer)(Handle,int,short*) = dlsym(lib, "eciSetOutputBuffer");
    int (*Add)(Handle,const void*) = dlsym(lib, "eciAddText");
    int (*Synthesize)(Handle) = dlsym(lib, "eciSynthesize");
    int (*Synchronize)(Handle) = dlsym(lib, "eciSynchronize");
    int (*Speaking)(Handle) = dlsym(lib, "eciSpeaking");
    if (!NewEx || !SetParam || !SetVoice || !Register || !Buffer || !Add || !Synthesize || !Speaking) return 7;
    Handle h = NewEx(dialect); if (!h) return 8;
    SetParam(h, 5, 1); SetParam(h, 0, 1); SetParam(h, 1, 1); SetParam(h, 3, 1);
    Register(h, (void *)wave, NULL); if (!Buffer(h, CHUNK, chunk)) return 9;
    for (int i = 0; i < 8; ++i) if (SetVoice(h, 0, i, values[i]) < 0) return 10;
    if (!Add(h, text) || !Synthesize(h)) return 11;
    if (Synchronize) Synchronize(h);
    while (Speaking(h)) usleep(1000);
    if (failed || !count) return 12;
    const uint32_t header[4] = { 0x31514c45, 1, 11025, count };
    int ok = write_all(header, sizeof(header)) && write_all(pcm, (size_t)count * sizeof(short));
    /* Exit directly: Apple's atexit hooks are not safe to reuse or unwind. */
    _exit(ok ? 0 : 13);
}
