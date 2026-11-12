package replica;

import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

final class WidgetContentFit {
    static Point required(View view) {
        ViewGroup.LayoutParams own=view.getLayoutParams();
        int width=Math.max(view.getMinimumWidth(),Math.max(view.getMeasuredWidth(),own==null?0:own.width));
        int height=Math.max(view.getMinimumHeight(),Math.max(view.getMeasuredHeight(),own==null?0:own.height));
        if(!(view instanceof ViewGroup group)||view instanceof AdapterView||view instanceof ScrollView||view instanceof HorizontalScrollView)
            return new Point(width,height);
        boolean linear=view instanceof LinearLayout;
        boolean horizontal=linear&&((LinearLayout)view).getOrientation()==LinearLayout.HORIZONTAL;
        int childrenWidth=0,childrenHeight=0;
        for(int index=0;index<group.getChildCount();index++){
            View child=group.getChildAt(index);if(child.getVisibility()==View.GONE)continue;
            Point size=required(child);ViewGroup.LayoutParams params=child.getLayoutParams();
            if(params instanceof ViewGroup.MarginLayoutParams margin){size.x+=margin.leftMargin+margin.rightMargin;size.y+=margin.topMargin+margin.bottomMargin;}
            childrenWidth=horizontal?childrenWidth+size.x:Math.max(childrenWidth,size.x);
            childrenHeight=linear&&!horizontal?childrenHeight+size.y:Math.max(childrenHeight,size.y);
        }
        return new Point(Math.max(width,childrenWidth+view.getPaddingLeft()+view.getPaddingRight()),
                Math.max(height,childrenHeight+view.getPaddingTop()+view.getPaddingBottom()));
    }
    static float scale(int width,int height,int requiredWidth,int requiredHeight){
        return Math.min(1f,Math.min((float)Math.max(1,width)/Math.max(1,requiredWidth),(float)Math.max(1,height)/Math.max(1,requiredHeight)));
    }
    private WidgetContentFit(){}
}
