#include <jni.h>
#include <cstdlib>
#include <cstring>
#include "node.h"

// Node's libuv requires all argv strings to live in contiguous memory.
extern "C" JNIEXPORT jint JNICALL
Java_com_sillytavern_apk_MainActivity_startNodeWithArguments(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray arguments) {

    const jsize argument_count = env->GetArrayLength(arguments);

    int c_arguments_size = 0;
    for (int i = 0; i < argument_count; i++) {
        const jstring arg = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char* arg_chars = env->GetStringUTFChars(arg, nullptr);
        c_arguments_size += static_cast<int>(strlen(arg_chars)) + 1;
        env->ReleaseStringUTFChars(arg, arg_chars);
        env->DeleteLocalRef(arg);
    }

    char* args_buffer = static_cast<char*>(calloc(c_arguments_size, sizeof(char)));
    char* argv[argument_count];
    char* current = args_buffer;

    for (int i = 0; i < argument_count; i++) {
        const jstring arg = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char* arg_chars = env->GetStringUTFChars(arg, nullptr);
        const size_t arg_length = strlen(arg_chars);

        memcpy(current, arg_chars, arg_length);
        current[arg_length] = '\0';
        argv[i] = current;

        current += arg_length + 1;
        env->ReleaseStringUTFChars(arg, arg_chars);
        env->DeleteLocalRef(arg);
    }

    const int result = node::Start(argument_count, argv);
    free(args_buffer);

    return static_cast<jint>(result);
}
