package replica;

/** Pure 4×4 edit geometry; invalid proposals leave the last valid layout untouched. */
public final class WidgetGrid {
 public static final int LEFT=1,TOP=2,RIGHT=4,BOTTOM=8;
 public static final class Cell {
  public final int x,y,w,h;
  public Cell(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
 }
 public static boolean valid(Cell c){return c!=null&&c.x>=0&&c.y>=0&&c.w>=1&&c.h>=1&&c.w<=4&&c.h<=4&&c.x<=4-c.w&&c.y<=4-c.h;}
 public static Cell propose(Cell start,int dx,int dy,int edges){
  if(!valid(start)||(edges&~15)!=0||((edges&LEFT)!=0&&(edges&RIGHT)!=0)||((edges&TOP)!=0&&(edges&BOTTOM)!=0))return null;
  int x=start.x,y=start.y,w=start.w,h=start.h;
  if(edges==0){x+=dx;y+=dy;}
  else{
   if((edges&LEFT)!=0){x+=dx;w-=dx;}if((edges&RIGHT)!=0)w+=dx;
   if((edges&TOP)!=0){y+=dy;h-=dy;}if((edges&BOTTOM)!=0)h+=dy;
  }
  Cell result=new Cell(x,y,w,h);return valid(result)?result:null;
 }
 public static boolean overlaps(Cell a,Cell b){return a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}
 /** One pointer gesture; cancellation always restores the original cell. */
 public static final class Edit {
  private final Cell original;private final int edges;private Cell current;
  public Edit(Cell start,int edges){if(!valid(start))throw new IllegalArgumentException("Invalid widget cell");original=start;current=start;this.edges=edges;}
  public Cell move(int dx,int dy,java.util.function.Predicate<Cell> free){Cell candidate=propose(original,dx,dy,edges);if(candidate!=null&&free.test(candidate))current=candidate;return current;}
  public Cell cancel(){current=original;return current;}
  public Cell current(){return current;}
 }
 private WidgetGrid(){}
}
