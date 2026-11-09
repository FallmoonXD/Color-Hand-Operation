package replica;

import java.util.*;

/** A drop changes only the dragged widget and widgets covered by its destination. */
final class WidgetDrop {
    static final class Slot {
        final int id,page;
        final WidgetGrid.Cell cell;
        Slot(int id,int page,WidgetGrid.Cell cell){this.id=id;this.page=page;this.cell=cell;}
        Slot at(int p,int x,int y){return new Slot(id,p,new WidgetGrid.Cell(x,y,cell.w,cell.h));}
    }
    private static int mask(WidgetGrid.Cell c){
        int row=(1<<c.w)-1,bits=0;
        for(int y=c.y;y<c.y+c.h;y++)bits|=row<<(4*y+c.x);
        return bits;
    }
    static List<Slot> plan(List<Slot> slots,Slot source,int page,int x,int y){
        Slot target=source.at(page,x,y);if(!WidgetGrid.valid(target.cell))return null;
        List<Slot> displaced=new ArrayList<>();int origin=0,destination=mask(target.cell);
        for(Slot s:slots){
            if(s.id==source.id)continue;
            if(s.page==page&&WidgetGrid.overlaps(s.cell,target.cell))displaced.add(s);
            else if(s.page==page)destination|=mask(s.cell);
            else if(s.page==source.page)origin|=mask(s.cell);
        }
        displaced.sort((a,b)->Integer.compare(b.cell.w*b.cell.h,a.cell.w*a.cell.h));
        List<Slot> result=new ArrayList<>();result.add(target);
        return place(displaced,0,source,page,origin,destination,result,new HashSet<>())?result:null;
    }
    private static boolean place(List<Slot> displaced,int index,Slot source,int page,int origin,int dest,List<Slot> result,Set<Long> visited){
        if(index==displaced.size())return true;
        long key=((long)index<<32)|((long)origin<<16)|dest;
        if(!visited.add(key))return false;
        Slot item=displaced.get(index);List<Slot> choices=new ArrayList<>();
        for(int p:new int[]{source.page,page}){
            for(int y=0;y<=4-item.cell.h;y++)for(int x=0;x<=4-item.cell.w;x++){
                Slot candidate=item.at(p,x,y);int occupied=p==page?dest:origin;
                if((occupied&mask(candidate.cell))==0)choices.add(candidate);
            }
            if(source.page==page)break;
        }
        // A direct exchange back to the old slot wins; keep nearby placements deterministic.
        choices.sort(Comparator.comparingInt(s->rank(s,source)));
        for(Slot s:choices){
            int bits=mask(s.cell);result.add(s);
            if(place(displaced,index+1,source,page,s.page==page?origin:origin|bits,s.page==page?dest|bits:dest,result,visited))return true;
            result.remove(result.size()-1);
        }
        return false;
    }
    private static int rank(Slot candidate,Slot source){
        WidgetGrid.Cell c=candidate.cell,old=source.cell;
        boolean freed=candidate.page==source.page&&c.x>=old.x&&c.y>=old.y&&c.x+c.w<=old.x+old.w&&c.y+c.h<=old.y+old.h;
        return (freed?0:candidate.page==source.page?8:16)+Math.abs(c.x-old.x)+Math.abs(c.y-old.y);
    }
}
