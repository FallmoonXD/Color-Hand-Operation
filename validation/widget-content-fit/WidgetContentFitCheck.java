public final class WidgetContentFitCheck {
    static int checks;
    static void size(View view,int width,int height,String message){
        Point actual=WidgetContentFit.required(view);checks++;
        if(actual.x!=width||actual.y!=height)throw new AssertionError(message+": "+actual.x+"x"+actual.y);
    }
    static void scale(int width,int height,int requiredWidth,int requiredHeight,float expected){
        checks++;float actual=WidgetContentFit.scale(width,height,requiredWidth,requiredHeight);
        if(Math.abs(actual-expected)>.0001f)throw new AssertionError("scale "+actual);
    }
    public static void main(String[] args){
        ViewGroup root=new ViewGroup(311,147);
        View fixed=new View(311,147);fixed.params=new ViewGroup.LayoutParams(-1,170);root.children=new View[]{fixed};
        size(root,311,170,"fixed inner height survives constrained parent measurement");
        scale(311,147,311,170,147f/170);
        fixed.params=new ViewGroup.LayoutParams(340,170);size(root,340,170,"fixed width and height");
        scale(160,160,340,170,160f/340);
        fixed.params=new ViewGroup.LayoutParams(-1,-1);size(root,311,147,"adaptive layout remains unchanged");
        scale(311,147,311,147,1);scale(400,400,340,170,1);
        ScrollView scroll=new ScrollView(311,147);scroll.children=new View[]{new View(311,2000)};
        size(scroll,311,147,"scrollable content does not shrink its viewport");
        HorizontalScrollView horizontal=new HorizontalScrollView(311,147);horizontal.children=new View[]{new View(2000,147)};
        size(horizontal,311,147,"horizontal scrolling retains viewport");
        AdapterView collection=new AdapterView(311,147);collection.children=new View[]{new View(800,900)};
        size(collection,311,147,"collection children do not define host size");
        LinearLayout column=new LinearLayout(100,100,1);column.children=new View[]{new View(100,60),new View(100,60)};
        size(column,100,120,"vertical fixed siblings need combined height");
        LinearLayout row=new LinearLayout(100,100,0);row.children=new View[]{new View(60,100),new View(60,100)};
        size(row,120,100,"horizontal fixed siblings need combined width");
        row.children[1].visibility=View.GONE;size(row,100,100,"hidden child excluded");
        root.padding=5;fixed.width=301;fixed.height=137;size(root,311,147,"parent padding not double counted");
        fixed.params=new ViewGroup.MarginLayoutParams(340,170,5);size(root,360,190,"fixed child margins and parent padding");
        View minimum=new View(100,100);minimum.minWidth=140;minimum.minHeight=170;
        size(minimum,140,170,"declared minimum survives exact parent constraints");
        System.out.println("PASS "+checks+" widget content fit assertions");
    }
}
class Point {int x,y;Point(int width,int height){x=width;y=height;}}
class View {
    static final int GONE=8;
    int width,height,padding,visibility,minWidth,minHeight;ViewGroup.LayoutParams params;
    View(int width,int height){this.width=width;this.height=height;params=new ViewGroup.LayoutParams(-1,-1);}
    int getMeasuredWidth(){return width;}int getMeasuredHeight(){return height;}
    int getMinimumWidth(){return minWidth;}int getMinimumHeight(){return minHeight;}
    int getVisibility(){return visibility;}ViewGroup.LayoutParams getLayoutParams(){return params;}
    int getPaddingLeft(){return padding;}int getPaddingRight(){return padding;}
    int getPaddingTop(){return padding;}int getPaddingBottom(){return padding;}
}
class ViewGroup extends View {
    View[] children=new View[0];ViewGroup(int width,int height){super(width,height);}
    int getChildCount(){return children.length;}View getChildAt(int index){return children[index];}
    static class LayoutParams {int width,height;LayoutParams(int width,int height){this.width=width;this.height=height;}}
    static class MarginLayoutParams extends LayoutParams {
        int leftMargin,topMargin,rightMargin,bottomMargin;
        MarginLayoutParams(int width,int height,int margin){super(width,height);leftMargin=topMargin=rightMargin=bottomMargin=margin;}
    }
}
class LinearLayout extends ViewGroup {static final int HORIZONTAL=0;int orientation;LinearLayout(int width,int height,int orientation){super(width,height);this.orientation=orientation;}int getOrientation(){return orientation;}}
class AdapterView extends ViewGroup {AdapterView(int width,int height){super(width,height);}}
class ScrollView extends ViewGroup {ScrollView(int width,int height){super(width,height);}}
class HorizontalScrollView extends ViewGroup {HorizontalScrollView(int width,int height){super(width,height);}}
