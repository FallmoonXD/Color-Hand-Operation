package replica;

/** Bounded edit geometry; older callers use a 4×4 grid. */
public final class WidgetGrid {
 public static final int LEFT=1,TOP=2,RIGHT=4,BOTTOM=8;
 public static final class Cell {
  public final int x,y,w,h;
  public Cell(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
 }
 public static boolean dimensions(int columns,int rows){return columns>=2&&columns<=6&&rows>=2&&rows<=6;}
 public static boolean valid(Cell c){return valid(c,4,4);}
 public static boolean valid(Cell c,int columns,int rows){return dimensions(columns,rows)&&c!=null&&c.x>=0&&c.y>=0&&c.w>=1&&c.h>=1&&c.w<=columns&&c.h<=rows&&c.x<=columns-c.w&&c.y<=rows-c.h;}
 public static Cell propose(Cell start,int dx,int dy,int edges){
  return propose(start,dx,dy,edges,4,4);
 }
 public static Cell propose(Cell start,int dx,int dy,int edges,int columns,int rows){
  if(!valid(start,columns,rows)||(edges&~15)!=0||((edges&LEFT)!=0&&(edges&RIGHT)!=0)||((edges&TOP)!=0&&(edges&BOTTOM)!=0))return null;
  int x=start.x,y=start.y,w=start.w,h=start.h;
  if(edges==0){x+=dx;y+=dy;}
  else{
   if((edges&LEFT)!=0){x+=dx;w-=dx;}if((edges&RIGHT)!=0)w+=dx;
   if((edges&TOP)!=0){y+=dy;h-=dy;}if((edges&BOTTOM)!=0)h+=dy;
  }
  Cell result=new Cell(x,y,w,h);return valid(result,columns,rows)?result:null;
 }
 public static boolean overlaps(Cell a,Cell b){return a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}
 /** One pointer gesture; cancellation always restores the original cell. */
 public static final class Edit {
  private final Cell original;private final int edges,columns,rows;private Cell current;
  public Edit(Cell start,int edges){this(start,edges,4,4);}
  public Edit(Cell start,int edges,int columns,int rows){if(!valid(start,columns,rows))throw new IllegalArgumentException("Invalid widget cell");original=start;current=start;this.edges=edges;this.columns=columns;this.rows=rows;}
  public Cell move(int dx,int dy,java.util.function.Predicate<Cell> free){Cell candidate=propose(original,dx,dy,edges,columns,rows);if(candidate!=null&&free.test(candidate))current=candidate;return current;}
  public Cell cancel(){current=original;return current;}
  public Cell current(){return current;}
 }
 private WidgetGrid(){}
}
