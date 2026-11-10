// Host-side regression test of the actual JNI sample rewriter in stub-libdovi mode.
// Build with a JDK's JNI headers and an Android log stub; see run_dovi_bridge_host_test.sh.
#include <cassert>
#include <cstdarg>
#include <cstdint>
#include <vector>
#include "../../app/src/main/cpp/dovi_bridge.cpp"

int __android_log_print(int, const char*, const char*, ...) { return 0; }
using Bytes = std::vector<jbyte>;
static Bytes& bytes(jarray array) { return *reinterpret_cast<Bytes*>(array); }
static jsize JNICALL length(JNIEnv*, jarray array) { return static_cast<jsize>(bytes(array).size()); }
static void* JNICALL critical(JNIEnv*, jarray array, jboolean*) { return bytes(array).data(); }
static void JNICALL release(JNIEnv*, jarray, void*, jint) {}
static void JNICALL setRegion(JNIEnv*, jbyteArray array, jsize start, jsize len, const jbyte* src) {
    std::copy(src, src + len, bytes(array).begin() + start);
}
static jbyteArray asArray(Bytes& data) { return reinterpret_cast<jbyteArray>(&data); }
static jint process(JNIEnv* env, Bytes& in, Bytes& out, int nalsize = 4) {
    return Java_com_kitsugi_animelist_core_player_DoviBridge_nativeProcessVideoSample(
        env, nullptr, asArray(in), static_cast<jint>(in.size()), 1, nalsize, asArray(out),
        false, 1, 7, true, false);
}
int main() {
    JNINativeInterface_ funcs{};
    funcs.GetArrayLength = length;
    funcs.GetPrimitiveArrayCritical = critical;
    funcs.ReleasePrimitiveArrayCritical = release;
    funcs.SetByteArrayRegion = setRegion;
    JNIEnv_ env{&funcs};
    // A base-layer NAL followed by an RPU must retain the base layer only.
    Bytes good{0,0,0,2, 2,1, 0,0,0,2, 124,1};
    Bytes out(64);
    assert(process(&env, good, out) == 6);
    assert(std::equal(good.begin(), good.begin() + 6, out.begin()));
    // Malformed tails or oversized lengths must not produce truncated rewrites.
    Bytes trailing{0,0,0,2, 124,1, 0};
    assert(process(&env, trailing, out) == 0);
    Bytes overflow{static_cast<jbyte>(0xff),static_cast<jbyte>(0xff),static_cast<jbyte>(0xff),static_cast<jbyte>(0xff), 124,1};
    assert(process(&env, overflow, out) == 0);
    assert(process(&env, good, out, 0) == 0);
    assert(process(&env, good, out, 5) == 0);
    // A too-small destination reports the exact required size without writing.
    Bytes small(2, 42);
    assert(process(&env, good, small) == -6);
    assert(small[0] == 42 && small[1] == 42);
}
