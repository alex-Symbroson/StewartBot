package wrapper;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

import static core.Bot.Log;

public class GuildWrapper
{
    public int maxTextEp, maxTextLength, textTimeout, voiceEppm, warnStatic, levelEp;
    public Map<Integer, String> roles = new HashMap<>();
    public List<Long> afkChannel = new ArrayList<>();
    public List<Long> polls = new ArrayList<>();
    public long adminRole;

    public JSONObject guild;
    public JSONObject users;
    public int version;
    public final String gid;
    
    public GuildWrapper(JSONObject guild, String gid)
    {
        this.gid = gid;
        loadJSON(guild);
        flush();
    }

    public GuildWrapper loadJSON(JSONObject guild) {
        this.guild = guild;
        this.users = guild.getJSONObject("users");

        version = guild.getInt("version");
        maxTextEp = guild.getInt("maxTextEp");
		maxTextLength = guild.getInt("maxTextLength");
		textTimeout = guild.getInt("textTimeout");
		voiceEppm = guild.getInt("voiceEppm");
		warnStatic = guild.getInt("warnStatic");
        levelEp = guild.getInt("levelEp");
        
		if(version >= 20) adminRole = guild.getLong("adminRole");

        afkChannel.clear();
        JSONArray jarr = guild.getJSONArray("afkChannel");
        for(int i = 0; i < jarr.length(); i++)
            afkChannel.add(jarr.getLong(i));

        roles.clear();
        JSONObject jroles = guild.getJSONObject("roles");
        for(String k : jroles.keySet())
            roles.put(Integer.parseInt(k), jroles.getString(k));

        polls.clear();
        if(version >= 22)
        {
            jarr = guild.getJSONArray("polls");
            for(int i = 0; i < jarr.length(); i++)
                polls.add(jarr.getLong(i));
        }
        
        return this;
    }

    public GuildWrapper flush()
    {
        guild.put("maxTextEp", maxTextEp);
		guild.put("maxTextLength", maxTextLength);
		guild.put("textTimeout", textTimeout);
		guild.put("voiceEppm", voiceEppm);
		guild.put("afkChannel", afkChannel);
		guild.put("polls", polls);
		guild.put("warnStatic", warnStatic);
        guild.put("levelEp", levelEp);
        guild.put("adminRole", adminRole);
        return this;
    }

    public UserWrapper getUser(String id)
    {
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