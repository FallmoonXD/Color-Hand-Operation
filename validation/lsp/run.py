from pathlib import Path
import subprocess,shutil,os,hashlib

ROOT=Path(__file__).resolve().parents[2]
out=ROOT/'.checks/lsp-state';out.mkdir(parents=True,exist_ok=True)
compiler=Path(shutil.which('javac'));runtime=compiler.with_name('java.exe' if compiler.suffix=='.exe' else 'java')
sources=[ROOT/'app/src/main/java/dev/edgeflow'/name for name in ['GestureRecognizer.java','GestureAngles.java']]
sources += [ROOT/'lsp/src/main/java/dev/edgeflow/lsp/EdgeInputSession.java',Path(__file__).with_name('EdgeInputCheck.java')]
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),*map(str,sources)],check=True)
subprocess.run([str(runtime),'-cp',str(out),'EdgeInputCheck'],check=True)
jar=ROOT/'.checks/config-harness/json-20240303.jar'
if not jar.is_file():raise SystemExit('Run the configuration harness as described in docs/config-validation.md to provision the JSON test dependency')
if hashlib.sha256(jar.read_bytes()).hexdigest()!='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':raise SystemExit('Unexpected JSON test dependency hash')
mapped=[ROOT/'app/src/main/java/dev/edgeflow'/name for name in ['GestureRecognizer.java','GestureAngles.java','GestureHandleConfig.java','GestureAppearance.java']]
mapped += [ROOT/'lsp/src/main/java/dev/edgeflow/lsp'/name for name in ['GestureProfile.java','MappedGestureSession.java','PersistentState.java','PersistentPolicy.java','FeedbackTiming.java','ControllerUpdates.java']]
mapped += [Path(__file__).with_name('MappedGestureCheck.java'),Path(__file__).with_name('PersistentCheck.java'),Path(__file__).with_name('FeedbackCheck.java')]
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-cp',str(jar),'-d',str(out),*map(str,mapped)],check=True)
subprocess.run([str(runtime),'-cp',os.pathsep.join([str(out),str(jar)]),'dev.edgeflow.lsp.MappedGestureCheck'],check=True)
subprocess.run([str(runtime),'-cp',os.pathsep.join([str(out),str(jar)]),'dev.edgeflow.lsp.PersistentCheck'],check=True)
subprocess.run([str(runtime),'-cp',os.pathsep.join([str(out),str(jar)]),'dev.edgeflow.lsp.FeedbackCheck'],check=True)
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),str(ROOT/'lsp/src/main/java/dev/edgeflow/lsp/WindowLeaseStore.java'),str(Path(__file__).with_name('WindowLeaseCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.lsp.WindowLeaseCheck'],check=True)
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),*[str(ROOT/'lsp/src/main/java/dev/edgeflow/lsp'/name) for name in ['TouchSequence.java','TouchRequestGate.java','WindowLeaseStore.java']],str(Path(__file__).with_name('TouchSequenceCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.lsp.TouchSequenceCheck'],check=True)
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),str(ROOT/'lsp/src/main/java/dev/edgeflow/lsp/LifecycleDispatch.java'),str(Path(__file__).with_name('LifecycleDispatchCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.lsp.LifecycleDispatchCheck'],check=True)
subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),str(ROOT/'app/src/main/java/dev/edgeflow/RootAuthorization.java'),str(Path(__file__).with_name('RootAuthorizationCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.RootAuthorizationCheck'],check=True)

subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),str(ROOT/'lsp/src/main/java/dev/edgeflow/lsp/PreferenceRetry.java'),str(Path(__file__).with_name('PreferenceRetryCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.lsp.PreferenceRetryCheck'],check=True)

subprocess.run([str(compiler),'--release','17','-encoding','utf8','-d',str(out),
                *[str(ROOT/'app/src/main/java/dev/edgeflow'/name) for name in ['BackendActions.java','ToolHostContract.java']],
                str(Path(__file__).with_name('PowerMenuCapabilityCheck.java'))],check=True)
subprocess.run([str(runtime),'-cp',str(out),'dev.edgeflow.PowerMenuCapabilityCheck'],check=True)
