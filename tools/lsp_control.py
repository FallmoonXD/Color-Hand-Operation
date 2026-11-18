"""Send test requests through the signed fixture's real app UID, never UID 0."""
import json,shlex,time,uuid
import device as d

PACKAGE='dev.edgeflow.lspfixture'
def request(operation,package=PACKAGE,timeout=7,profile=None,module=None):
    key=uuid.uuid4().hex
    path=f'/data/user/0/{package}/files/control/{key}.json'
    args=['am','broadcast','-f','0x10000020','-n',package+'/dev.edgeflow.lspfixture.ControlReceiver',
         '--es','operation',operation,'--es','request_id',key]
    if profile is not None:args+=['--es','profile',json.dumps(profile,separators=(',',':')) if not isinstance(profile,str) else profile]
    if module is not None:args+=['--es','module_package',module]
    d.sh(*args)
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        raw=d.sh('su','-c',f'if [ -f {shlex.quote(path)} ]; then cat {shlex.quote(path)}; fi').strip()
        if raw:
            try:result=json.loads(raw)
            except ValueError:time.sleep(.05);continue
            d.sh('su','-c','rm -f '+shlex.quote(path))
            return result
        time.sleep(.15)
    raise TimeoutError('No fixture reply to '+operation+'; request was not retried')

if __name__=='__main__':
    import sys
    print(json.dumps(request(sys.argv[1] if len(sys.argv)>1 else 'status'),ensure_ascii=False,indent=2))
