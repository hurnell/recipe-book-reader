#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_apk_hurnell_recipebookreader_RecipeBookApplication_qzZOdiQCvJTdsCGKBUvuCfTqm(
        JNIEnv *env,
        jobject /* this */) {

    std::string out = "";

    const char *source[] = {"L", "a", "n", "N", "g", "L", "p", "e", "Ʃ", "3", "ŉ", "D", "G", "Z",
                            "i", "t", "t", "0", "L", "7", "1", "Ǜ", "0", "1", "6", "ċ", "5", "Ǜ",
                            "4", "f", "7", "r", "s", "ǭ", "T", "l", "1", "n", "K", "0", "Ë", "4",
                            "Z", "7", "3", "U", "E", "r", "ņ", "0", "X", "l", "Q", "Z", "D", "z",
                            "b", "Ķ", "A", "ŭ", "o", "4", "N", "i", "Q", "1", "7", "u", "e", "H",
                            "e", "W", "Š", "3", "Z", "4", "H", "2", "7", "6", "u", "6", "T", "S",
                            "í", "7", "9", "x", "2", "8", "t", "ǆ", "é", "9", "Ŭ", "8", "ď", "t",
                            "W", "ũ", "r", "f", "T", "8", "N", "6", "X", "6", "j", "Q", "÷", "F",
                            "Ĭ", "8", "q", "ť", "į", "O", "V", "Ű", "8", "X"};

    int key[64] = {108, 74, 62, 11, 58, 1, 51, 32, 54, 62, 0, 0, 60, 51, 58, 111, 2, 47, 118, 50, 2,
                   12, 69, 32, 29, 118, 46, 102, 70, 52, 18, 0, 4, 37, 58, 118, 35, 46, 56, 11, 13,
                   16, 11, 109, 38, 71, 69, 55, 54, 87, 69, 56, 67, 62, 100, 54, 42, 31, 90, 76,
                   101, 68, 46, 0};

    for (int i = 0; i < 64; i++) {
        out += source[key[i]];
    }

    return env->NewStringUTF(out.c_str());
}
