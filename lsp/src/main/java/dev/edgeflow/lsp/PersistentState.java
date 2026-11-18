package dev.edgeflow.lsp;

import org.json.*;

/** One framework preference contains a complete, versioned controller snapshot. */
final class PersistentState {
    static final String GROUP="cho_input_v1",KEY="state";
    final boolean requested;
    final String profile,source,revision;
    final int user;
    PersistentState(boolean requested,String profile,String source,String revision,int user){
        this.requested=requested;this.profile=profile;this.source=source;this.revision=revision;this.user=user;
    }
    static PersistentState parse(String raw)throws JSONException{
        if(raw==null||raw.length()>150000)throw new IllegalArgumentException("Invalid persistent state size");
        JSONObject value=new JSONObject(raw);
        if(value.getInt("version")!=1)throw new IllegalArgumentException("Persistent state version");
        PersistentState state=new PersistentState(value.getBoolean("requested"),value.getString("profile"),value.getString("source"),value.getString("revision"),value.getInt("user"));
        if(state.user!=0||state.revision.isEmpty()||state.source.length()>1024)throw new IllegalArgumentException("Unsupported owner or revision");
        if(state.requested){if(state.source.isEmpty())throw new IllegalArgumentException("Source required");GestureProfile.parse(state.profile);}
        return state;
    }
    String encode()throws JSONException{return new JSONObject().put("version",1).put("requested",requested).put("profile",profile).put("source",source).put("revision",revision).put("user",user).toString();}
}
