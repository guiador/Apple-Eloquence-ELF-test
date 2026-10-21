/* SPDX-License-Identifier: MIT
 * Darwin's JOINABLE/DETACHED are 1/2; Bionic's are 0/1. Passing Darwin's
 * JOINABLE unchanged creates a detached worker, which disappears before the
 * engine can join it during a language switch or shutdown.
 */
#include <pthread.h>
#include <errno.h>
__attribute__((visibility("hidden")))
int m2e_android_attr_setdetachstate(pthread_attr_t *attr, int darwin_state) {
    if (darwin_state == 1)
        return pthread_attr_setdetachstate(attr, PTHREAD_CREATE_JOINABLE);
    if (darwin_state == 2)
        return pthread_attr_setdetachstate(attr, PTHREAD_CREATE_DETACHED);
    return EINVAL;
}
