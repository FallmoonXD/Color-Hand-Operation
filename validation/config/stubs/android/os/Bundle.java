package android.os;
import java.util.*;
public class Bundle implements Parcelable {
 private final Map<String,Object> values=new HashMap<>();public Bundle(){}public void putInt(String k,int v){values.put(k,v);}public int getInt(String k){return getInt(k,0);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public int describeContents(){return 0;}public void writeToParcel(Parcel p,int flags){}
}