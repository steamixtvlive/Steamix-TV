#include <jni.h>
#include <android/log.h>
#include <sys/ptrace.h>
#include <unistd.h>
#include <cstring>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define LOG_TAG "NotflixNative"

static void hard_fail() {
    // Beyaz ekran + kilitleme: sureci sonlandir
    _exit(0);
}

__attribute__((constructor))
static void on_load() {
    // Anti-debug: ptrace
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        hard_fail();
    }
    ptrace(PTRACE_DETACH, 0, 0, 0);
    // TracerPid kontrolu
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd >= 0) {
        char buf[4096];
        int n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n > 0) {
            buf[n] = 0;
            char* p = strstr(buf, "TracerPid:");
            if (p) {
                p += 10;
                while (*p == ' ' || *p == '\t') p++;
                if (*p != '0') hard_fail();
            }
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_notflix_app_NativeBridge_isSecure(JNIEnv* env, jobject thiz) {
    // Anti-debug tekrar
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) return JNI_FALSE;
    ptrace(PTRACE_DETACH, 0, 0, 0);
    return JNI_TRUE;
}
