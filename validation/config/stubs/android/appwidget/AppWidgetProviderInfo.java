package android.appwidget;

import android.content.ComponentName;
import android.os.UserHandle;

public class AppWidgetProviderInfo {
    public ComponentName provider,configure;
    public int widgetFeatures,resizeMode,minWidth,minHeight,minResizeWidth,minResizeHeight,maxResizeWidth,maxResizeHeight,targetCellWidth,targetCellHeight;
    public UserHandle profile=new UserHandle(0);
    public UserHandle getProfile(){return profile;}
}
