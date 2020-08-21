package core;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import util.Crypt;
import wrapper.GuildWrapper;

import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import static util.FSManager.*;

/**
 * main class
 * @author Symbroson
 * @version 2.4
 * @since 21.08.2020
 */
public class Bot
{
    /** developer mode */
    public static final boolean dev = false;

    /** bot version */
    public static final int version = 24;
    /** current sbf version */
    public static final int sbfVersion = 23;
    /** sbf version map NUM:PREFIX */
    public static final Map<Integer, String> versions = new TreeMap<>(Comparator.reverseOrder());

    /** dataDir pattern */
    public static final String dataDir = "data/%s/";
    /** Server log file format */
    public static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyyMMdd:HHmmss");
    /** logFile format */
    private static final SimpleDateFormat logFormat = new SimpleDateFormat("'Logs/'yyyyMMdd'.log'");

    /** jda reference */
    public static JDA jda;
    /** command prefix string */
    public static final String prefix = dev ? "#" : "/";
    /** Bot name */
    public static final String name = "Stewart";
    /** Bot description */
    public static final String desc = "Keeps track of your activity on the server.\nCommand prefix: " + prefix;
    /** crypter for sbf files */
    public static Crypt crypt;
    /** guild cache map GUILD_ID:GuildWrapper */
    private static final HashMap<String, GuildWrapper> guildCache = new HashMap<>();

    /** current logFile date */
    private static Date logDate = new Date(0);
    /** current logFile */
    private static FileOutputStream logFile;
    /** temporary result placeholder for bot error or status messages */
    public static String tRes;

    public static void sendOwnerMessage(String s)
    {
        Bot.jda.openPrivateChannelById(SECRETS.OWNID).queue(c -> c.sendMessage(s).queue());
    }

    /** lambda interface for withGuildData */
    public interface GuildDataHandler
    {
        void exec(GuildWrapper guild);
    }

    /**
     * Starts the bot and the voiceCheckTask
     * @param args
     * @throws LoginException
     */
    public static void main(String[] args) throws Exception
    {
        Log("initialize versions");
        versions.put(23, "sbf23|");
        versions.put(22, "sbf22|");
        versions.put(21, "sbfv21");
        versions.put(20, "sbfv2");
        versions.put(10, "sbf");
        if(!versions.containsKey(Bot.sbfVersion)) throw new Exception("current sbf version not registered");

        Log("intialise crypt");
        crypt = new Crypt("AES/ECB/PKCS5Padding", "SHA-1", "AES");

        Log("intialise bot");
        try
        {
            // jda = JDABuilder.createDefault(core.SECRETS.TOKEN).build();
            jda = new JDABuilder(AccountType.BOT).setToken(SECRETS.TOKEN).build();
        }
        catch (LoginException e)
        {
            if(args.length < 2) {
                Log("You need to provide a valid bot token.\nSyntax: StewartBot.jar BOT-TOKEN OWNER_UID");
                return;
            } else {
                SECRETS.TOKEN = args[0];
                SECRETS.OWNID = Long.parseLong(args[1]);
                jda = new JDABuilder(AccountType.BOT).setToken(SECRETS.TOKEN).build();
            }
            e.printStackTrace();
        }
        jda.getPresence().setActivity(Activity.playing(prefix + "help || version " + (""+version).replaceAll("(?!^|$)", ".")));
        jda.getSelfUser().getManager().setAvatar(Icon.from(new File("res/stewart.png"))).queue();
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.setAutoReconnect(true);
        jda.addEventListener(new EventHandler());

        Log("running");

        // schedule voiceCheck every 5 minutes
        EpDistributor.scheduleVoiceCheck(5 * 60 * 1000);
    }

    private static long tStart = 0, tStep = 0, tDiff = 0;

    /** method to add delete action reaction to a message */
	public static Consumer<? super Message> addX = m -> m.addReaction("❌").queue();


	/* logging */

    /** save start time */
    public static void tic()
    {
        tStart = tStep = System.nanoTime();
    }

    /** calculate and print time diff */
    public static void toc()
    {
        long t = System.nanoTime();
        tDiff = t - tStep;
        System.out.println(String.format("dtime: %dus", tDiff / 1000));
        tStep = t;
    }

    /**
     * returns a data file object based on logFormat
     * @param folder
     * @return File
     */
    private static File getLogFile(String folder)
    {
        return getDataFile(folder, logFormat.format(new Date()));
    }

    /**
     * updates the logFile when a new day started
     * @throws FileNotFoundException
     */
    private static void checkLogDate() throws FileNotFoundException
    {
        Date curDate = new Date();
        if (!logFormat.format(logDate).equals(logFormat.format(curDate)))
        {
            logDate = curDate;
            logFile = new FileOutputStream(getLogFile("Server"), true);
        }
    }

    /**
     * logs to the server log
     * @param log
     */
    public static void Log(String log)
    {
        try
        {
            checkLogDate();
            System.out.println(log);
            logFile.write((log + "\n").getBytes());
        }
        catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * logs to a guild log
     * @param folder
     * @param log
     */
    public static void Log(String folder, String log)
    {
        if(Bot.dev) System.out.println(log);
        else try
        {
            FileOutputStream fos = new FileOutputStream(getLogFile(folder), true);
            fos.write((log + "\n").getBytes());
            fos.close();
        }
        catch (IOException e) { e.printStackTrace(); }
    }


    /* guild data */

    /**
     * used when no data needs to be changed.
     * @param guildId
     * @return guildData cache
     */
    public static GuildWrapper getGuildData(String guildId)
    {
        return getGuildData(guildId, false);
    }

    /**
     * used when no data needs to be changed
     * @param guildId
     * @param save indicates if the modified data should be saved afterwards
     * @param cb receives guildData cache as first argument
     */
    public static void withGuildData(String guildId, boolean save, GuildDataHandler cb)
    {
        GuildWrapper g = getGuildData(guildId, save);
        if(g == null) return;
        if(cb != null) cb.exec(g);
        if(save) saveGuildData(g);
    }

    /**
     * Saves guild data to the corresponding guild.sbf file
     * @param guild
     */
    public static void saveGuildData(GuildWrapper guild)
    {
        if(guild == null) return;
        File f = getDataFile(guild.gid, "guild.sbf");
        saveSBF(f, guild.flush().guild, guild.gid.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host());
    }

    /**
     * Returns guildData from guildCache<nr></nr>
     * If the requested data doesn't exist in guildCache it reads the data from dataDir and caches it<br>
     * @param guildId
     * @param save indicates if the copied data should be saved before returning
     * @return GuildWrapper
     */
    static GuildWrapper getGuildData(String guildId, boolean save)
    {
        if(guildCache.containsKey(guildId))
            return guildCache.get(guildId);

        JSONObject guild = getGuildJson(guildId, save);
        if(guild == null)
        {
            String name = Bot.jda.getGuildById(guildId) != null ? Bot.jda.getGuildById(guildId).getName() :
                          Bot.jda.getUserById(guildId) != null ? Bot.jda.getUserById(guildId).getName() : "Unknown";

            sendOwnerMessage("Failed to get Server data: " + guildId + ":" + name);
            return null;
        }
        guildCache.put(guildId, new GuildWrapper(guild, guildId));
        return guildCache.get(guildId);
    }

    /**
     * Reads guild json from file system from the corresponding guild.sbf file<br>
     *     If that fails too it will try guildBase.sbf and after that guildBase.json from the Server data directory<br>
     *     If any read was successful the data will be returned, otherwise null
     * @param guildId
     * @param save indicates if the copied data should be saved before returning
     * @return JSONObject
     */
    @Nullable
    public static JSONObject getGuildJson(String guildId, boolean save)
    {
	    File f = getDataFile(guildId, "guild.sbf");
	    int key = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	    JSONObject guild = loadSBF(f, key);

        if(guild == null && save)
        {
            Log("Loading fallback guildBase.sbf");
            File g = getDataFile("Server", "guildBase.sbf");
            int l = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
            guild = loadSBF(g, l);

            if(guild == null)
            {
                Log("Loading fallback guildBase.json");
                guild = loadJSON(getDataFile("Server", "guildBase.json"));

                if(guild != null) {
                    if(!guild.has("version")) guild.put("version", 0);
                    saveSBF(g, new GuildWrapper(guild, guildId).guild, l);
                }
            }
        }

        if(guild == null)
        {
            Log("Critical error. GuildData couldn't be loaded nor restored.");
            return null;
        }
        else if(!guild.has("version")) guild.put("version", 0);
        return guild;
	}


    /* guild helper functions */

    /**
     * check if the Event author has the guild admin role
     * @param e
     * @return isAdmin
     */
    static boolean isAdmin(UniEvent e)
    {
        return e.author.getIdLong() == SECRETS.OWNID || (e.guild != null && (e.author.getIdLong() == e.guild.getOwnerIdLong() ||
               e.guild.getMember(e.author).getRoles().contains(e.guild.getRoleById(getGuildData(e.guild.getId()).adminRole))));
    }

    /**
     * tries to parse a guild role object out of various text patterns
     * @param e
     * @param name
     * @return Role
     */
    @Nullable public static Role tryGetRole(UniEvent e, String name) {
        if(e.msg != null) {
            List<Role> rs = e.msg.getMentionedRoles();
            if(rs.size() > 0) return rs.get(0);
        }

        Role r = null;
        tRes = null;

        // @ referenced
        if(name.matches("<@&\\d+>")) r = e.guild.getRoleById(name.substring(3, name.length() - 1));
            // name referenced
        else if(name.matches("^@?[\\w -]+$"))
        {
            List<Role> roles = e.guild.getRolesByName(name.replace("@", ""), true);

            // create role if not exists
            if(roles.size() == 0)
            {
                e.guild.createRole().setName(name).setColor(0xff0000).complete();
                tRes = "Created role '" + name + "'.";
                roles = e.guild.getRolesByName(name, false);
            }

            if(roles.size() > 0) r = roles.get(0);
            else tRes = "Coulnd't find nor create role.";
        }
        else
        {
            if(name.isEmpty()) tRes = "No role specified.";
            else tRes = "Invalid format.";
        }

        return r;
    }

    /**
     * tries to parse a guild member object out of various text patterns
     * @param e
     * @param name "&lt;@id&gt;" or "&lt;@!id&gt;" or @Name
     * @return Member
     */
    @Nullable public static Member tryGetMember(UniEvent e, String name) {
        List<Member> ms = e.msg.getMentionedMembers(e.guild);
        if(ms.size() > 0) return ms.get(0);

        Member m = null;
        tRes = null;

        // @ referenced
        if(name.matches("<@!?\\d+>")) m = e.guild.getMemberById(name.replaceAll("^<@!?|>$", ""));
            // name referenced
        else if(name.matches("@?[\\w -]+"))
        {
            List<Member> members = e.guild.getMembersByName(name.replaceFirst("^@", ""), true);

            if(members.size() > 0) m = members.get(0);
            else tRes = "Coulnd't find member.";
        } else tRes = "Invalid format.";

        return m;
    }


    /**
     * Checks if a guild member has a permission and log to tRes otherwise
     * @param m
     * @param p
     * @return hasPermission
     */
    public static boolean checkPerm(Member m, Permission p) {
        assert m != null;
        boolean b = !m.hasPermission(p);
        if(b) tRes = "Missing Permission: " + p.getName();
        return b;
    }
    /**
     * Checks if a user in a guild has a permission and log to tRes otherwise
     * @param u
     * @param p
     * @return hasPermission
     */
    public static boolean checkPerm(UniEvent e, User u, Permission p) {
        assert e.guild != null;
        return checkPerm(e.guild.getMember(u), p);
    }
    /**
     * Checks if the bot has a permission and log to tRes otherwise
     * @param p
     * @return hasPermission
     */
    public static boolean checkPerm(UniEvent e, Permission p) {
        return checkPerm(e, Bot.jda.getSelfUser(), p);
    }

    /**
     * deletes a message<br>
     * also removes the poll entry if the message was one
     * @param e
     */
    static void delMsg(UniEvent e)
    {
        e.msg.delete().onErrorMap(ex -> null).queue();
        withGuildData(e.guild.getId(), true, g -> g.polls.remove(e.msg.getIdLong()));
    }


    /* general helper functions */
    /** get item from array with range checks */
    public static Object get(Object[] arr, int index, Object dflt) {
        if(arr.length <= index) return dflt;
        return arr[index];
    }
    /** get item from array with range checks */
    public static String get(String[] arr, int index, String dflt) {
        if(arr.length <= index) return dflt;
        return arr[index];
    }
    /** get item from array with range checks */
    public static String get(String[] arr, int index) {
        if(arr.length <= index) return null;
        return arr[index];
    }

    /**
     * Checks if the url is valid or returns null otherwise
     * @param url
     * @return url or null
     */
    @Nullable public static String getUrl(String url) {
        return url != null && EmbedBuilder.URL_PATTERN.matcher(url).matches() ? url : null;
    }
}
