"""Restore only explicitly named device preferences changed by an integration test."""
import shlex,subprocess,time,xml.etree.ElementTree as E
import device as d

class PreferenceTrial:
    def __init__(self,allowed):
        self.allowed=allowed
        self.before={name:d.preferences(name) for name in allowed}
        self.xml={name:E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/'+name+'.xml')) for name in allowed}
    def __enter__(self):return self
    def __exit__(self,*error):
        changes={}
        for name,keys in self.allowed.items():
            current=d.preferences(name);before=self.before[name]
            if current==before:continue
            assert {k:v for k,v in current.items() if k not in keys}=={k:v for k,v in before.items() if k not in keys},'Unexpected change in '+name
            tree=E.fromstring(d.sh('run-as',d.P,'cat','shared_prefs/'+name+'.xml'))
            for node in list(tree):
                if node.get('name') in keys:tree.remove(node)
            for node in self.xml[name]:
                if node.get('name') in keys:tree.append(node)
            changes[name]=E.tostring(tree,encoding='utf-8',xml_declaration=True)
        if changes:
            d.sh('am','force-stop',d.P)
            for name,payload in changes.items():
                assert name+'.xml.bak' not in d.sh('run-as',d.P,'ls','shared_prefs').split()
                path='shared_prefs/'+name+'.xml'
                command=shlex.join(['run-as',d.P,'sh','-c','cat > '+path+'.ui-test && mv '+path+'.ui-test '+path])
                subprocess.run(d.A+['shell','-T',command],input=payload,check=True,capture_output=True)
            d.sh('am','start','-W','-n',d.P+'/.MainActivity');d.activate();time.sleep(.7)
            # The OEM's retain-service prompt can arrive after the Activity has
            # already resumed. Preserve only this app's previously authorized service.
            for attempt in range(3):
                tree=d.ui()
                own=any('Color Hand Operation' in n.get('text','') and '无障碍' in n.get('text','') for n in tree.iter('node'))
                keep=next((n for n in tree.iter('node') if n.get('text')=='保持开启' and n.get('package')=='com.oplus.safecenter'),None)
                if own and keep is not None:d.tap(keep);time.sleep(.5);break
                time.sleep(.7)
        for name,before in self.before.items():assert d.preferences(name)==before,'Restoration failed: '+name
