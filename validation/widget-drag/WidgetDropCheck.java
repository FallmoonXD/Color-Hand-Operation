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
        System.out.println("PASS "+checks+" production widget exchange checks");
    }
}
