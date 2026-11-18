"""Verify the independent APK cannot package its compile-only framework/API stubs."""
from pathlib import Path
import hashlib,json,struct,zipfile,argparse

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--integrated',action='store_true');parser.add_argument('--output',type=Path);args=parser.parse_args()
integrated=args.integrated
apk=ROOT/('app/build/outputs/apk/release/app-release.apk' if integrated else 'lsp/build/outputs/apk/debug/lsp-debug.apk')
defined=set()
def u32(data,offset):return struct.unpack_from('<I',data,offset)[0]
def dex_string(data,offset):
    while data[offset]&128:offset+=1
    offset+=1
    return data[offset:data.index(0,offset)].decode('utf8','replace')

with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if not name.endswith('.dex'):continue
        data=archive.read(name)
        strings,types,classes=(u32(data,offset) for offset in (0x3c,0x44,0x64))
        for index in range(u32(data,0x60)):
            type_index=u32(data,classes+index*32)
            string_index=u32(data,types+type_index*4)
            defined.add(dex_string(data,u32(data,strings+string_index*4)))
    assert archive.read('META-INF/xposed/java_init.list').decode().strip()=='dev.edgeflow.lsp.ModuleEntry'
    assert archive.read('META-INF/xposed/scope.list').decode().split()==['com.android.systemui','system']
    properties=dict(line.split('=',1) for line in archive.read('META-INF/xposed/module.prop').decode().splitlines() if '=' in line)
    assert properties['minApiVersion']=='102' and properties['autoHotReload']=='false'
    assert archive.read('assets/licenses/libxposed-service.txt')==(ROOT/'lsp/src/main/assets/licenses/libxposed-service.txt').read_bytes()
    for name in ['Landroid/view/InputChannel;','Landroid/view/InputEventReceiver;','Landroid/app/TaskStackListener;']:assert name not in defined,name
    assert not any(name.startswith('Lio/github/libxposed/api/') for name in defined)
    assert {'Ldev/edgeflow/GestureRecognizer;','Ldev/edgeflow/GestureAngles;','Ldev/edgeflow/lsp/ModuleEntry;'}.issubset(defined)
    if integrated:
        assert not {'Ldev/edgeflow/lsp/PilotActivity;','Ldev/edgeflow/lsp/RootProbe;'}.intersection(defined)
        assert {'Ldev/edgeflow/RuntimeBackend;','Ldev/edgeflow/LspToolHostService;','Ldev/edgeflow/lsp/BackendApplication;'}.issubset(defined)
for name in ['GestureRecognizer.java','GestureAngles.java','GestureHandleConfig.java','GestureWaveRenderer.java','GestureHaptics.java','GestureHapticPlayer.java','GestureAppearance.java','ToolHostContract.java','BackendChoice.java','BackendActions.java']:
    assert (ROOT/'app/src/main/java/dev/edgeflow'/name).read_bytes()==(ROOT/'lsp/build/generated/gesture-core/dev/edgeflow'/name).read_bytes()
for name in ['ColorSpringTrack.java','ColorControlPhysics.java']:
    assert (ROOT/'app/src/main/java/replica'/name).read_bytes()==(ROOT/'lsp/build/generated/gesture-core/replica'/name).read_bytes()
for source in [ROOT/'app/src/main/res/drawable/oho_gesture_arrow.xml',*(ROOT/'app/src/main/res').glob('values*/wave.xml')]:
    assert source.read_bytes()==(ROOT/'lsp/build/generated/feedback-res'/source.parent.name/source.name).read_bytes()
result={'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'own_and_generated_definitions':len(defined),'checks':'metadata, SystemUI and framework scopes, compile-only types excluded, exact shared gesture source'}
output=args.output or ROOT/('verification/lsp-integrated' if integrated else 'verification/lsp-input-prototype');output.mkdir(parents=True,exist_ok=True)
(output/'apk-check.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print('PASS', 'integrated' if integrated else 'pilot','APK packaging and shared production gesture source',result['apk_sha256'])
