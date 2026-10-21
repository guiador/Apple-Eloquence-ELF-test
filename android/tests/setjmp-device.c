/* SPDX-License-Identifier: MIT */
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
extern int m2e_android_setjmp(void *) __attribute__((returns_twice));
extern void m2e_android_longjmp(void *, int) __attribute__((noreturn));

static void *exercise(void *unused) {
    (void)unused;
    struct { unsigned char before[16], darwin[192], after[16]; } buffer;
    memset(&buffer, 0xa5, sizeof(buffer));
    volatile int expected = 7;
    for (int i = 0; i < 200; ++i) {
        int result = m2e_android_setjmp(buffer.darwin);
        if (result == 0) m2e_android_longjmp(buffer.darwin, expected);
        if (result != (expected ? expected : 1)) abort();
        for (size_t k = 0; k < sizeof(buffer); ++k)
            if (((unsigned char *)&buffer)[k] != 0xa5) abort();
        expected = expected ? 0 : 7;
    }
    return NULL;
}
int main(void) {
    pthread_t a, b;
    if (pthread_create(&a, NULL, exercise, NULL) ||
        pthread_create(&b, NULL, exercise, NULL)) return 1;
    exercise(NULL);
    pthread_join(a, NULL);
    pthread_join(b, NULL);
    puts("PASS: 600 jumps; zero/nonzero returns; intact 192-byte buffers; 3 threads");
    return 0;
}
