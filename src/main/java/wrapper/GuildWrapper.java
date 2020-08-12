package wrapper;

import java.util.*;

import org.json.JSONObject;

import static core.Bot.Log;

public class GuildWrapper {
    public int maxTextEp, maxTextLength, textTimeout, voiceEppm, warnStatic, levelEp;
    public Map<Integer, String> roles = new HashMap<>();
    public List<Long> afkChannel = new ArrayList<>();
    public long adminRole;

    public final JSONObject guild;
    public final JSONObject users;
    private final String gid;
    private final int version;

    public GuildWrapper(JSONObject guild, String gid) {
        this.gid = gid;
        this.guild = guild;
        this.users = guild.getJSONObject("users");

        version = guild.getInt("version");
        maxTextEp = guild.getInt("maxTextEp");
		maxTextLength = guild.getInt("maxTextLength");
		textTimeout = guild.getInt("textTimeout");
		voiceEppm = guild.getInt("voiceEppm");
		warnStatic = guild.getInt("warnStatic");
		levelEp = guild.getInt("levelEp");
		if(version == 20) adminRole = guild.getLong("adminRole");
        

        for(Object i : guild.getJSONArray("afkChannel")) {
            afkChannel.add((long)(int)i);
        }

        JSONObject jroles = guild.getJSONObject("roles");
        for(String k : jroles.keySet())
            roles.put(Integer.parseInt(k), jroles.getString(k));
    }
    
    public GuildWrapper flush() {
        guild.put("maxTextEp", maxTextEp);
		guild.put("maxTextLength", maxTextLength);
		guild.put("textTimeout", textTimeout);
		guild.put("voiceEppm", voiceEppm);
		guild.put("afkChannel", afkChannel);
		guild.put("warnStatic", warnStatic);
        guild.put("levelEp", levelEp);
        guild.put("adminRole", adminRole);
        return this;
    }

    public UserWrapper getUser(String id) {
        // return user if exists
        if(users.has(id)) return new UserWrapper(users.getJSONObject(id));
        
        // create new user
        UserWrapper u = new UserWrapper(users.getJSONObject("default"));
        users.put(id, u.user);
        flush();
        Log(gid, "Created new entry for " + id);

        return u;
    }
}