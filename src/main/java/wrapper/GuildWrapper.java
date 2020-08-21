package wrapper;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

import static core.Bot.Log;

/** wrapper class for guild json data */
public class GuildWrapper
{
    public int maxTextEp, maxTextLength, textTimeout, voiceEppm, warnStatic, levelEp;
    public Map<Integer, String> roles = new HashMap<>();
    public List<Long> afkChannel = new ArrayList<>();
    public List<Long> polls = new ArrayList<>();
    public long adminRole;

    public JSONObject guild;
    public JSONObject users;
    /** sbf version */
    public int version;
    /** guild id */
    public final String gid;

    private HashMap<String, UserWrapper> userCache = new HashMap<>();

    /**
     * Constructor
     * @param guild JSON data
     * @param gid GuildID
     */
    public GuildWrapper(JSONObject guild, String gid)
    {
        this.gid = gid;
        loadJSON(guild);
        flush();
    }

    /**
     * Parse Guild data from a json structure
     * @param guild
     * @return
     */
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

    /**
     * Writes the modified data to the json structure
     * @return self
     */
    public GuildWrapper flush()
    {
        guild.put("maxTextEp", maxTextEp);
		guild.put("maxTextLength", maxTextLength);
		guild.put("textTimeout", textTimeout);
		guild.put("voiceEppm", voiceEppm);
		guild.put("afkChannel", afkChannel);
		guild.put("polls", polls);
        guild.put("roles", roles);
		guild.put("warnStatic", warnStatic);
        guild.put("levelEp", levelEp);
        guild.put("adminRole", adminRole);
        return this;
    }

    /**
     * Parse a user from the json users list
     * @param id
     * @return UserWrapper
     */
    public UserWrapper getUser(String id)
    {
        return getUser(id, false);
    }

    /**
     * Parse a user from the users list
     * @param id
     * @param create indicates if the user should be created if not existent
     * @return UserWrapper
     */
    public UserWrapper getUser(String id, boolean create)
    {
        // return user if exists
        if(users.has(id)) {
            if(!userCache.containsKey(id))
                userCache.put(id, new UserWrapper(users.getJSONObject(id)));
            return userCache.get(id);
        }
        if(!create) return null;
        
        // create new user
        UserWrapper u = new UserWrapper(users.getJSONObject("default"));
        userCache.put(id, u);
        users.put(id, u.user);
        flush();
        Log(gid, "Created new entry for " + id);

        return u;
    }

    /**
     * checks if a channel is afk
     * @param id
     * @return isChannelAfk
     */
    public boolean isAfk(Long id)
    {
        return afkChannel.contains(id);
    }

    /**
     * checks if a user is static
     * @param id
     * @return isUserStatic
     */
    public boolean isStatic(String id)
    {
        UserWrapper u = getUser(id, false);
        return u != null && u.bstatic;
    }
}