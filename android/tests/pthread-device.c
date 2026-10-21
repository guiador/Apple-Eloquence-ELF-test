/* SPDX-License-Identifier: MIT */
#include <pthread.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
extern int m2e_android_attr_setdetachstate(pthread_attr_t *, int);
static void *worker(void *value) { return value; }
int main(void) {
    pthread_attr_t attr;
    if (pthread_attr_init(&attr)) abort();
    for (int i = 0; i < 200; ++i) {
        pthread_t thread;
        void *result;
        int state;
        if (m2e_android_attr_setdetachstate(&attr, 1) ||
            pthread_attr_getdetachstate(&attr, &state) ||
            state != PTHREAD_CREATE_JOINABLE ||
            pthread_create(&thread, &attr, worker, &attr) ||
            pthread_join(thread, &result) || result != &attr) abort();
    }
    int state;
    if (m2e_android_attr_setdetachstate(&attr, 2) ||
        pthread_attr_getdetachstate(&attr, &state) ||
        state != PTHREAD_CREATE_DETACHED ||
        m2e_android_attr_setdetachstate(&attr, 99) != EINVAL) abort();
    pthread_attr_destroy(&attr);
    puts("PASS: 200 joinable workers; Darwin states 1/2 mapped; invalid state rejected");
    return 0;
}
