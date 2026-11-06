"""Run production retention methods against a narrow Dialog/Window lifecycle model.

This deliberately does not simulate WindowManager focus or Android input dispatch.
"""
from pathlib import Path
import shutil
import subprocess

here = Path(__file__).resolve().parent
project = here.parent.parent
source = (project / "app/src/main/java/replica/ColorActionSheet.java").read_text(encoding="utf-8")
output = project / ".checks/settings-sheet/retention"
output.mkdir(parents=True, exist_ok=True)


def extract(signature):
    begin = source.index(signature)
    at = source.index("{", begin) + 1
    depth = 1
    while depth:
        depth += (source[at] == "{") - (source[at] == "}")
        at += 1
    return source[begin:at]


signatures = [
    "private void checkMutable(", "public boolean isShowing(", "public boolean isSuspended(", "public void refreshContent(",
    "public boolean suspend(", "public void resume(", "public ColorActionSheet show()",
    "public ColorActionSheet showRestored(", "private ColorActionSheet show(boolean",
    "public ColorActionSheet showEmbedded(FrameLayout", "public ColorActionSheet showEmbeddedRestored(",
    "private ColorActionSheet showEmbedded(FrameLayout", "private void syncEmbeddedLayout(", "private void unmountEmbedded(",
    "public void dismissThen(", "public void dismiss()", "private void startDismiss(",
    "public void dismissImmediately(", "private void finish(", "private void release(",
    "private void notifyDismiss(", "private void stopAnimation(", "private void observeOwner(",
    "private boolean ownerAvailable(",
]
entry_begin = source.index("private final Runnable enterWhenLaidOut=")
entry_end = source.index("}};", entry_begin) + 3
methods = "\n".join(extract(signature) for signature in signatures)
template = (here / "SheetRetentionCheck.template.java").read_text(encoding="utf-8")
(output / "ColorActionSheet.java").write_text(
    template.replace("/* PRODUCTION_METHODS */", methods).replace(
        "/* PRODUCTION_ENTRY */", source[entry_begin:entry_end]), encoding="utf-8")
compiler = shutil.which("javac")
if not compiler:
    raise SystemExit("javac is required")
subprocess.run([compiler, "-encoding", "UTF-8", "--release", "8", "-d", str(output),
                str(output / "ColorActionSheet.java")], check=True)
runtime = str(Path(compiler).with_name("java.exe" if Path(compiler).suffix == ".exe" else "java"))
result = subprocess.run([runtime, "-cp", str(output), "ColorActionSheet"], check=True,
                        capture_output=True, text=True, encoding="utf-8")
print(result.stdout, end="")
(output / "results.txt").write_text(result.stdout, encoding="utf-8")
