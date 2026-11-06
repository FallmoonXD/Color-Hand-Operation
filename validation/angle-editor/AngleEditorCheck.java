import dev.edgeflow.AngleEditorGeometry;
import dev.edgeflow.GestureAngles;

/** Geometry used by the native view, including hit regions and rollback transactions. */
public final class AngleEditorCheck {
    private static int checks;
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private static boolean close(float a,float b){return Math.abs(a-b)<.0002;}
    public static void main(String[] args){
        float[] saved={80.125f,30.5f,40.25f,79.875f};
        for(boolean mirror:new boolean[]{false,true})for(float density:new float[]{1,2.75f,3.5f}){
            float cx=220*density,cy=220*density,radius=160*density;
            for(int i=0;i<4;i++){
                AngleEditorGeometry.Point point=AngleEditorGeometry.point(cx,cy,radius,mirror,i,saved[i]);
                check(AngleEditorGeometry.hit(point.x,point.y,cx,cy,radius,mirror,saved,24*density,9*density)==i,"actual knob center selects independent boundary "+i);
                check(close(AngleEditorGeometry.pointerAngle(point.x,point.y,cx,cy,mirror,i),saved[i]),"inverse polar mapping preserves angle side="+mirror+" index="+i);
                point=AngleEditorGeometry.point(cx,cy,radius*.65f,mirror,i,saved[i]);
                check(AngleEditorGeometry.hit(point.x,point.y,cx,cy,radius,mirror,saved,24*density,9*density)==i,"outer boundary segment is draggable "+i);
                point=AngleEditorGeometry.point(cx,cy,radius*.25f,mirror,i,saved[i]);
                check(AngleEditorGeometry.hit(point.x,point.y,cx,cy,radius,mirror,saved,24*density,9*density)==-1,"inner line/origin remains available to page scroll");
            }
            check(AngleEditorGeometry.hit(cx,cy,cx,cy,radius,mirror,saved,24*density,9*density)==-1,"origin never steals scrolling");
            check(AngleEditorGeometry.hit(cx+(mirror?-1:1)*radius*.8f,cy,cx,cy,radius,mirror,saved,24*density,9*density)==-1,"blank horizontal sector never steals scrolling");
            check(AngleEditorGeometry.hit(cx+(mirror?1:-1)*radius*.8f,cy,cx,cy,radius,mirror,saved,24*density,9*density)==-1,"outside the fan never steals scrolling");
        }
        for(boolean mirror:new boolean[]{false,true}){
            float[] narrow={75,70,70,75};for(int i=0;i<4;i++){
                AngleEditorGeometry.Point point=AngleEditorGeometry.point(220,220,160,mirror,i,narrow[i]);
                check(AngleEditorGeometry.hit(point.x,point.y,220,220,160,mirror,narrow,24,9)==i,"nearby 5 degree knobs choose nearest center, not array order");
            }
        }
        for(int i=0;i<4;i++){
            AngleEditorGeometry.Drag drag=new AngleEditorGeometry.Drag(saved);float initial=drag.value(i);drag.begin(i,initial-3);
            check(!drag.changed()&&drag.value(i)==initial,"DOWN/grab offset does not change a saved float");
            check(drag.update(initial-2)&&close(drag.value(i),initial+1),"relative dragging preserves initial grab offset");
            drag.cancel();check(drag.value(i)==initial&&drag.active()==-1&&!drag.changed(),"CANCEL restores exact original float");
            drag.begin(i,initial);drag.update(1000);check(drag.value(i)==GestureAngles.max(i),"large drag clamps maximum");
            drag.update(-1000);check(drag.value(i)==GestureAngles.min(i),"large reverse drag clamps minimum");
            drag.cancel();check(drag.value(i)==initial,"CANCEL after clamping restores original");
            drag.begin(i,initial);drag.update(initial+.75f);float committed=drag.value(i);drag.commit();drag.cancel();check(close(committed,initial+.75f)&&drag.value(i)==committed,"UP commit survives later cancel/detach");
            drag.begin(i,committed);check(!drag.update(Float.NaN)&&!drag.update(Float.POSITIVE_INFINITY)&&drag.value(i)==committed,"invalid coordinate cannot corrupt draft");drag.cancel();
        }
        AngleEditorGeometry.Drag drag=new AngleEditorGeometry.Drag(saved);float[] detached=drag.values();detached[0]=85;check(drag.value(0)==saved[0],"callback and caller arrays cannot mutate editor state");
        drag.begin(0,80);drag.update(83);drag.begin(1,30);check(drag.value(0)==saved[0]&&drag.active()==1,"starting another transaction rolls back prior drag");
        drag.update(45);drag.replace(new float[]{75,25,45,75});check(drag.active()==-1&&drag.value(1)==25,"external value refresh cannot retain stale pointer draft");
        check(!drag.set(1,Float.NaN)&&!drag.set(1,Float.NEGATIVE_INFINITY)&&drag.value(1)==25,"accessibility invalid progress rejected");
        check(drag.set(1,55.25f)&&drag.value(1)==55.25f,"accessibility retains valid fractional progress");
        boolean invalid=false;try{new AngleEditorGeometry.Drag(new float[]{1,2,3});}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"incorrect boundary array is explicit error");
        invalid=false;try{new AngleEditorGeometry.Drag(new float[]{80,Float.NaN,30,80});}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"nonfinite initial boundary is explicit error");
        System.out.println("PASS "+checks+" native angle-editor geometry/transaction assertions");
    }
}
