package replica;

import android.appwidget.AppWidgetProviderInfo;
import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Process;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

/** Capability checks use the AppWidget receiver the user actually bound. */
final class WidgetCapabilities {
    final boolean horizontal,vertical,reconfigure;
    final int minX,minY,maxX,maxY;
    final Intent settings,appearance;
    WidgetCapabilities(Context c,AppWidgetProviderInfo info,int width,int height,WidgetGrid.Cell current,int columns,int rows){
        if(info==null){horizontal=vertical=reconfigure=false;minX=maxX=current.w;minY=maxY=current.h;settings=appearance=null;return;}
        horizontal=(info.resizeMode&AppWidgetProviderInfo.RESIZE_HORIZONTAL)!=0;
        vertical=(info.resizeMode&AppWidgetProviderInfo.RESIZE_VERTICAL)!=0;
        minX=horizontal?Math.min(current.w,minSpan(info.minResizeWidth,width,columns)):current.w;
        minY=vertical?Math.min(current.h,minSpan(info.minResizeHeight,height,rows)):current.h;
        maxX=horizontal?Math.max(current.w,maxSpan(info.maxResizeWidth,width,columns)):current.w;
        maxY=vertical?Math.max(current.h,maxSpan(info.maxResizeHeight,height,rows)):current.h;
        reconfigure=info.configure!=null&&(info.widgetFeatures&AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE)!=0;
        boolean local=Process.myUserHandle().equals(info.getProfile());
        settings=local?readShortcut(c,info):null;
        ComponentName browserWidget=new ComponentName("com.heytap.browser","com.android.browser.widget.WidgetStyle6Provider");
        appearance=local&&browserWidget.equals(info.provider)?accessible(c,new Intent().setComponent(new ComponentName(
                "com.heytap.browser","com.heytap.browser.settings.component.Style6AppearanceSettingActivity"))
                .putExtra("source","widgetLongPress")):null;
    }
    private static int minSpan(int pixels,int side,int count){return Math.max(1,Math.min(count,(int)Math.ceil((double)count*pixels/Math.max(1,side))));}
    private static int maxSpan(int pixels,int side,int count){return pixels<=0?count:Math.max(1,(int)Math.min(count,(long)count*pixels/Math.max(1,side)));}
    boolean resizable(){return maxX>minX||maxY>minY;}
    boolean permits(WidgetGrid.Cell c){return c.w>=minX&&c.w<=maxX&&c.h>=minY&&c.h<=maxY;}
    private static Intent readShortcut(Context c,AppWidgetProviderInfo info){
        PackageManager pm=c.getPackageManager();
        try{
            ActivityInfo receiver=pm.getReceiverInfo(info.provider,PackageManager.GET_META_DATA);Bundle meta=receiver.metaData;
            int id=meta==null?0:meta.getInt("oplus.appwidget.shortcuts",0);if(id==0)return null;
            Resources res=pm.getResourcesForApplication(receiver.applicationInfo);
            try(XmlResourceParser xml=res.getXml(id)){
                int depth=-1;String style=null;Intent intent=null;
                for(int e=xml.next();e!=XmlPullParser.END_DOCUMENT;e=xml.next()){
                    String tag=xml.getName();
                    if(e==XmlPullParser.START_TAG&&"system_shortcut".equals(tag)){depth=xml.getDepth();style=null;intent=null;}
                    else if(depth>=0&&e==XmlPullParser.START_TAG){
                        if("style".equals(tag))style=xml.getAttributeValue(null,"id");
                        if("intent".equals(tag))intent=Intent.parseIntent(res,xml,Xml.asAttributeSet(xml));
                    }else if(e==XmlPullParser.END_TAG&&xml.getDepth()==depth){
                        if("common_settings".equals(style)&&intent!=null){
                            if(intent.getComponent()==null&&intent.getPackage()==null)intent.setPackage(info.provider.getPackageName());
                            Intent resolved=accessible(c,intent);if(resolved!=null)return resolved;
                        }
                        depth=-1;
                    }
                }
            }
        }catch(Exception unavailable){android.util.Log.w("WidgetPanel","Cannot read provider shortcut for "+info.provider,unavailable);}
        return null;
    }
    private static Intent accessible(Context c,Intent intent){
        ResolveInfo target=c.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
        if(target==null||!target.activityInfo.exported||!target.activityInfo.enabled||
                target.activityInfo.permission!=null&&c.checkSelfPermission(target.activityInfo.permission)!=PackageManager.PERMISSION_GRANTED)return null;
        intent.setComponent(new ComponentName(target.activityInfo.packageName,target.activityInfo.name));intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);return intent;
    }
}
