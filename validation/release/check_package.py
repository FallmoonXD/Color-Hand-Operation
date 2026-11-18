"""Fast checks for the release packager. Uses only scratch files and local Git objects."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
import sys
import unittest
from unittest.mock import patch
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'tools'))
import package_release as release


class PackageChecks(unittest.TestCase):
    def test_clean_includes_untracked_and_staged_files(self):
        with TemporaryDirectory() as tmp:
            root=Path(tmp)/'source'
            subprocess.run(['git','clone','--quiet','--shared',str(ROOT),str(root)],check=True)
            revision=release.clean_revision(root)
            (root/'.checks').mkdir()
            (root/'.checks/generated.txt').touch()
            self.assertEqual(release.clean_revision(root),revision)
            change=root/'app/src/main/assets/untracked-release-check.txt'
            change.write_text('local source',encoding='utf-8')
            subprocess.run(['git','config','status.showUntrackedFiles','no'],cwd=root,check=True)
            with self.assertRaisesRegex(ValueError,'untracked'):
                release.clean_revision(root)
            subprocess.run(['git','add','--',str(change)],cwd=root,check=True)
            with self.assertRaises(ValueError):
                release.clean_revision(root)

    def test_payload_compares_entries_and_storage_method(self):
        with TemporaryDirectory() as tmp:
            apk=Path(tmp)/'sample.apk'
            def payload(vcs, data, compression=zipfile.ZIP_STORED):
                with zipfile.ZipFile(apk,'w') as out:
                    out.writestr(release.VCS_INFO,vcs)
                    out.writestr('assets/asset',data,compress_type=compression)
                return release.apk_payload(apk)
            original=payload('commit','release')
            self.assertEqual(original,payload('archive checkout','release'))
            self.assertNotEqual(original,payload('commit','local file'))
            self.assertNotEqual(original,payload('commit','release',zipfile.ZIP_DEFLATED))
            with warnings.catch_warnings():
                warnings.simplefilter('ignore',UserWarning)
                with zipfile.ZipFile(apk,'a') as out:
                    out.writestr('assets/asset','duplicate')
            with self.assertRaisesRegex(ValueError,'Duplicate'):
                release.apk_payload(apk)

    def test_empty_unknown_and_additional_signers_are_rejected(self):
        for certs in ('', 'Signer #1 certificate SHA-256 digest: '+'0'*64,
                      'Signer #1 certificate SHA-256 digest: '+release.SIGNER_SHA256+'\nSigner #2 certificate SHA-256 digest: '+'0'*64):
            with self.subTest(certs=certs), patch.object(subprocess,'check_output',return_value=certs):
                with self.assertRaisesRegex(ValueError,'signing certificate'):
                    release.verify_apk(Path('unused.apk'),Path('sdk'),'revision','0.4.1','28')

    def test_partial_output_install_restores_the_previous_set(self):
        with TemporaryDirectory() as tmp:
            root=Path(tmp);dist=root/'dist';dist.mkdir()
            (dist/'old.apk').write_bytes(b'previous')
            (root/'old.apk').write_bytes(b'new')
            (root/'new.zip').write_bytes(b'archive')
            with self.assertRaises(FileNotFoundError):
                release.install_outputs([root/'old.apk',root/'new.zip',root/'missing'],dist)
            self.assertEqual([p.name for p in dist.iterdir()],['old.apk'])
            self.assertEqual((dist/'old.apk').read_bytes(),b'previous')


if __name__=='__main__':
    unittest.main()
