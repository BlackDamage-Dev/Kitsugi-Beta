import subprocess, re, os, sys
SYMS = []
LIBS = ["libXrender.so.1","libXxf86vm.so.1","libXfixes.so.3","libXi.so.6",
        "libxkbcommon.so.0","libSM.so.6","libICE.so.6","libGL.so.1"]
def build():
    src = "".join("void *%s(){return 0;}\n" % s for s in SYMS)
    open("/tmp/fakelibs/stub.c","w").write(src)
    r = subprocess.run(["gcc","-shared","-fPIC","-o","/tmp/fakelibs/libstub.so","/tmp/fakelibs/stub.c"],capture_output=True,text=True)
    if r.returncode: print(r.stderr[:500]); sys.exit(1)
    for l in LIBS:
        subprocess.run(["cp","/tmp/fakelibs/libstub.so","/tmp/fakelibs/"+l])
for it in range(400):
    build()
    env = dict(os.environ, LD_LIBRARY_PATH="/tmp/fakelibs")
    r = subprocess.run(["/tmp/blenv/bin/python","-c","import bpy;print('BPYOK')"],capture_output=True,text=True,env=env)
    if "BPYOK" in r.stdout:
        print("SUCCESS after", it, "iterations,", len(SYMS), "stubs")
        open("/tmp/fakelibs/syms.txt","w").write("\n".join(SYMS))
        sys.exit(0)
    m = re.search(r"undefined symbol: (\w+)", r.stderr)
    if not m:
        print("OTHER ERROR:\n", r.stderr[-1500:]); sys.exit(1)
    s = m.group(1)
    if s in SYMS: print("repeat symbol", s); sys.exit(1)
    SYMS.append(s)
print("too many iterations")
