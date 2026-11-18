package dev.edgeflow;

import android.content.Context;

/** App resources and accessible action text around the shared gesture renderer. */
public final class GestureWaveView extends GestureWaveRenderer {
    private String actionId="";
    public GestureWaveView(Context context){
        super(context,context.getDrawable(R.drawable.oho_gesture_arrow),context.getResources().getDimensionPixelSize(R.dimen.oho_curve_arrow_size));
    }
    public void setActionHint(String id){
        if(id==null||id.equals(actionId))return;actionId=id;
        setContentDescription(ActionExecutor.title(id));
    }
}
