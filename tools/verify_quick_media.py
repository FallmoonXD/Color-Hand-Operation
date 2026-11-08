"""On-device quick-tools regression with an isolated, silent Android test player."""
from io import BytesIO
from pathlib import Path
import hashlib
import os
import re
import subprocess
import tempfile
import time
import zipfile

from PIL import Image
import device as d

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'dev.edgeflow.mediatest'


def fixture(folder):
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', ROOT / '.toolchains/android-sdk'))
    android = sdk / 'platforms/android-35/android.jar'
    bt = sdk / 'build-tools/36.0.0'
    source = ROOT / 'validation/quick-media'
    classes, dex = folder / 'classes', folder / 'dex'
    classes.mkdir()
    dex.mkdir()

    def run(*args):
        subprocess.run([str(x) for x in args], check=True)

    run('javac', '--release', '17', '-encoding', 'utf8', '-cp', android, '-d', classes, source / 'Player.java')
    run(bt / 'd8.bat', '--min-api', '33', '--lib', android, '--output', dex, *classes.rglob('*.class'))
    raw, aligned, apk = (folder / name for name in ('raw.apk', 'aligned.apk', 'player.apk'))
    run(bt / 'aapt2.exe', 'link', '-I', android, '--manifest', source / 'AndroidManifest.xml', '-o', raw)
    with zipfile.ZipFile(raw, 'a') as z:
        z.write(dex / 'classes.dex', 'classes.dex')
    run(bt / 'zipalign.exe', '-f', '4', raw, aligned)
    run(bt / 'apksigner.bat', 'sign', '--ks', Path.home() / '.android/debug.keystore',
        '--ks-pass', 'pass:android', '--out', apk, aligned)
    return apk


def muted():
    match = re.search(r'^- STREAM_MUSIC:\s+Muted: (true|false)', d.sh('dumpsys','audio'), re.M)
    assert match, 'Missing stream mute state'
    return match[1] == 'true'


def set_volume(value):
    d.sh('cmd','media_session','volume','--stream','3','--set',str(value))
    time.sleep(.3)


def glyph_center(label):
    x,y,r,b = d.bounds(d.wait_node(label))
    frame = Image.open(BytesIO(subprocess.check_output(d.A + ['exec-out','screencap','-p']))).convert('RGB')
    return min(frame.getpixel(((x+r)//2,(y+b)//2)))


def exercise():
    d.panel_settings('快速工具')
    d.click_item('预览快速工具',True)
    d.wait_node('媒体音量')
    d.sh('am','start','-W','-n',PACKAGE+'/.Player')
    time.sleep(1)
    assert PACKAGE+'/.Player' in d.resumed()
    def player_state():
        dump = d.sh('dumpsys','media_session')
        session = dump.split('package='+PACKAGE,1)[1].split('\n    package=',1)[0]
        match = re.search(r'state=PlaybackState \{state=(PLAYING|PAUSED)\(', session)
        assert match, 'Missing playback state for the test session'
        return match[1]

    set_volume(4)
    d.click('媒体静音'); d.wait_node('取消媒体静音')
    assert muted() and d.vol()==0 and player_state()=='PLAYING'
    assert glyph_center('暂停') < 245
    d.click('取消媒体静音'); d.wait_node('媒体静音')
    assert not muted() and d.vol()==4
    d.click('暂停'); d.wait_node('播放')
    assert player_state()=='PAUSED' and glyph_center('播放') > 245
    d.click('播放'); d.wait_node('暂停')
    assert player_state()=='PLAYING'
    for play, label in [(False,'播放'), (True,'暂停')]:
        d.sh('am','broadcast','-p',PACKAGE,'-a',PACKAGE+'.STATE','--ez','play',str(play).lower())
        d.wait_node(label)
    set_volume(0)
    d.click('取消媒体静音'); d.wait_node('媒体静音')
    assert not muted() and d.vol()>0


def main():
    assert not d.sh('pm','list','packages',PACKAGE).strip(), 'Test player package is already installed'
    assert not muted(), 'Start with an unmuted media stream so its previous audible level can be restored'
    original = d.vol()
    with tempfile.TemporaryDirectory() as tmp:
        apk = fixture(Path(tmp))
        subprocess.run(d.A+['install','--no-incremental',str(apk)],check=True)
        try:
            exercise()
        finally:
            d.sh('am','force-stop',PACKAGE)
            installed = d.sh('pm','path',PACKAGE).strip().removeprefix('package:')
            assert d.sh('sha256sum',installed).split()[0] == hashlib.sha256(apk.read_bytes()).hexdigest()
            subprocess.run(d.A+['uninstall',PACKAGE],check=True)
            if muted():
                d.click('取消媒体静音')
            set_volume(original)
            d.main_home()
            assert not muted() and d.vol()==original
    print('PASS mute/unmute, zero index, play/pause glyphs, media keys and external playback changes')


if __name__ == '__main__':
    main()
