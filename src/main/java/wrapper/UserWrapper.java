package wrapper;

import org.json.JSONObject;

public class UserWrapper {
    public int level, textEp, voiceEp, warnings;
    public boolean bstatic;
    public long lTxtTime;

    public final JSONObject user;

    public UserWrapper(JSONObject user) {
        this.user = user;
        level = user.getInt("level");
        textEp = user.getInt("textEp");
        voiceEp = user.getInt("voiceEp");
        warnings = user.getInt("warnings");
        bstatic = user.getBoolean("static");
        lTxtTime = user.getLong("lTxtTime");
    }

    public void flush() {
        user.put("level", level);
        user.put("textEp", textEp);
        user.put("voiceEp", voiceEp);
        user.put("warnings", warnings);
        user.put("static", bstatic);
        user.put("lTxtTime", lTxtTime);
    }
}