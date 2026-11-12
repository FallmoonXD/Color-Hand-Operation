package replica;

import java.util.*;

public final class WidgetDropCheck {
    private static int checks;
    private static WidgetDrop.Slot slot(int id,int page,int x,int y,int w,int h){return new WidgetDrop.Slot(id,page,new WidgetGrid.Cell(x,y,w,h));}
    private static void check(boolean test,String msg){if(!test)throw new AssertionError(msg);checks++;}
    private static void at(List<WidgetDrop.Slot> result,int id,int page,int x,int y){
        WidgetDrop.Slot s=result.stream().filter(v->v.id==id).findFirst().orElseThrow();
        check(s.page==page&&s.cell.x==x&&s.cell.y==y,"Unexpected placement "+id);
    }
    public static void main(String[] args){
        WidgetDrop.Slot a=slot(1,0,0,0,2,1),b=slot(2,0,2,0,2,1);
        List<WidgetDrop.Slot> r=WidgetDrop.plan(List.of(a,b),a,0,2,0);
        at(r,1,0,2,0);at(r,2,0,0,0);check(a.cell.x==0&&b.cell.x==2,"Preview mutated saved positions");
        WidgetDrop.Slot big=slot(3,1,2,2,2,2),one=slot(4,2,0,0,1,1),two=slot(5,2,1,0,1,2);
        r=WidgetDrop.plan(List.of(big,one,two),big,2,0,0);
        at(r,3,2,0,0);at(r,4,1,3,2);at(r,5,1,2,2);
        check(WidgetDrop.plan(List.of(a,slot(9,0,2,3,1,1),slot(7,1,0,0,4,4)),a,1,1,0)==null,"Impossible exchange accepted");
        check(WidgetDrop.plan(List.of(a),a,2,3,0)==null,"Oversize drop accepted");
        r=WidgetDrop.plan(List.of(a,b),a,3,2,3);at(r,1,3,2,3);check(r.size()==1,"Unoccupied drop displaced a neighbor");
        WidgetDrop.Slot small=slot(10,0,0,0,1,1),last=slot(11,1,5,5,1,1),near=slot(12,1,4,5,1,1);
        r=WidgetDrop.plan(List.of(small,last,near),small,1,5,5,6,6);
        at(r,10,1,5,5);at(r,11,0,0,0);check(r.size()==2,"Upper mask bits collided");
        check(WidgetDrop.plan(List.of(small),small,1,6,0,6,6)==null,"Drop beyond six columns");
        check(WidgetGrid.valid(new WidgetGrid.Cell(5,5,1,1),6,6),"Last cell rejected");
        check(!WidgetGrid.valid(new WidgetGrid.Cell(5,5,1,2),6,6),"Overflow row accepted");
        WidgetGrid.Edit edit=new WidgetGrid.Edit(new WidgetGrid.Cell(1,1,1,1),WidgetGrid.RIGHT|WidgetGrid.BOTTOM,6,5);
        check(edit.move(4,3,c->true).w==5&&edit.current().h==4,"Rectangular resize failed");
        edit.move(5,4,c->true);check(edit.current().w==5&&edit.current().h==4,"Invalid resize replaced last valid value");
        check(edit.cancel().w==1&&edit.current().h==1,"Custom grid cancellation failed");
        for(int columns=2;columns<=6;columns++)for(int rows=2;rows<=6;rows++){
            List<WidgetDrop.Slot> full=new ArrayList<>();int id=100;
            for(int y=0;y<rows;y++)for(int x=0;x<columns;x++)full.add(slot(id++,0,x,y,1,1));
            r=WidgetDrop.plan(full,full.get(0),0,columns-1,rows-1,columns,rows);
            check(r!=null&&r.size()==2,"Full grid exchange failed");
            for(WidgetDrop.Slot s:r)check(WidgetGrid.valid(s.cell,columns,rows),"Reflow outside grid");
        }
        System.out.println("PASS "+checks+" production widget exchange checks");
    }
}
