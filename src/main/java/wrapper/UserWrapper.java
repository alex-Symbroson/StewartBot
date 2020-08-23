package wrapper;

import org.json.JSONObject;

/** wrapper class for guild user json data */
public class UserWrapper {
    public int level, textEp, voiceEp, warnings;
    public boolean bstatic;
    public long lTxtTime;

    public final JSONObject user;

    /**
     * Parse user data from a json structure
     * @param user
     */
    public UserWrapper(JSONObject user)
    {
        this.user = user;
        level = user.getInt("level");
        textEp = user.getInt("textEp");
        voiceEp = user.getInt("voiceEp");
        warnings = user.getInt("warnings");
        bstatic = user.getBoolean("static");
        lTxtTime = 0;
    }

    /**
     * Writes the modified data to the json structure
     * @return self
     */
    public UserWrapper flush()
    {
        user.put("level", level);
        user.put("textEp", textEp);
        user.put("voiceEp", voiceEp);
        user.put("warnings", warnings);
        user.put("static", bstatic);
        if(user.has("lTxtTime")) user.remove("lTxtTime");
        return this;
    }
}