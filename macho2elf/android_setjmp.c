/* SPDX-License-Identifier: MIT
 * Darwin arm64 allocates 192-byte jmp_buf objects. Bionic owns a larger,
 * version-dependent context (including its security state). Never let Bionic
 * write that context into the original engine's stack frame. Keep a native
 * buffer per Darwin address and thread, and tail-call the real libc functions
 * with the caller's original SP/LR so longjmp returns to the engine itself.
 * Entries are reused by address and freed when the thread exits.
 */
#include <setjmp.h>
#include <pthread.h>
#include <stdlib.h>

struct m2e_jump_entry {
    const void *darwin;
    jmp_buf native;
    struct m2e_jump_entry *next;
};
static pthread_key_t m2e_jump_key;
static pthread_once_t m2e_jump_once = PTHREAD_ONCE_INIT;
static void m2e_jump_free(void *ptr) {
    struct m2e_jump_entry *entry = ptr;
    while (entry) {
        struct m2e_jump_entry *next = entry->next;
        free(entry);
        entry = next;
    }
}
static void m2e_jump_key_init(void) {
    if (pthread_key_create(&m2e_jump_key, m2e_jump_free) != 0) abort();
}
__attribute__((visibility("hidden")))
void *m2e_jump_buffer(const void *darwin, int create) {
    if (pthread_once(&m2e_jump_once, m2e_jump_key_init) != 0) abort();
    struct m2e_jump_entry *head = pthread_getspecific(m2e_jump_key);
    for (struct m2e_jump_entry *p = head; p; p = p->next)
        if (p->darwin == darwin) return p->native;
    if (!create) abort();
    struct m2e_jump_entry *p = calloc(1, sizeof(*p));
    if (!p) abort();
    p->darwin = darwin;
    p->next = head;
    if (pthread_setspecific(m2e_jump_key, p) != 0) { free(p); abort(); }
    return p->native;
}
__asm__(
    ".text\n.balign 4\n"
    ".global m2e_android_setjmp\n.hidden m2e_android_setjmp\n"
    ".type m2e_android_setjmp, %function\n"
    "m2e_android_setjmp:\n"
    "  stp x29, x30, [sp, #-16]!\n"
    "  mov x29, sp\n"
    "  mov w1, #1\n"
    "  bl m2e_jump_buffer\n"
    "  ldp x29, x30, [sp], #16\n"
    "  b setjmp\n"
    ".size m2e_android_setjmp, .-m2e_android_setjmp\n"
    ".global m2e_android_longjmp\n.hidden m2e_android_longjmp\n"
    ".type m2e_android_longjmp, %function\n"
    "m2e_android_longjmp:\n"
    "  stp x29, x30, [sp, #-32]!\n"
    "  mov x29, sp\n"
    "  str w1, [sp, #16]\n"
    "  mov w1, #0\n"
    "  bl m2e_jump_buffer\n"
    "  ldr w1, [sp, #16]\n"
    "  ldp x29, x30, [sp], #32\n"
    "  b longjmp\n"
    ".size m2e_android_longjmp, .-m2e_android_longjmp\n"
);
