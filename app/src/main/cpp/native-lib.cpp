#include <jni.h>
#include <android/log.h>
#include <sys/ptrace.h>
#include <unistd.h>
#include <string.h>

#define LOG_TAG "NotflixNative"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_notflix_app_NativeBridge_isSecure(JNIEnv* env, jobject thiz) {
    // Anti-debug: ptrace attach check
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        return JNI_FALSE;
    }
    ptrace(PTRACE_DETACH, 0, 0, 0);
    return JNI_TRUE;
}
