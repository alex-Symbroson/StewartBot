package core;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONObject;
import util.Crypt;
import wrapper.GuildWrapper;
import wrapper.UniEvent;

import javax.annotation.Nullable;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.login.LoginException;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

import static util.FSManager.*;

/**
 * main class
 * @author Symbroson
 * @version 2.5
 * @since 24.08.2020
 */
public class Bot
{
    /** developer mode */
    public static final boolean dev = false;

    /** bot version */
    public static final int version = 268;
    /** current sbf version */
    public static final int sbfVersion = 24;

    /** jda reference */
    public static JDA jda;
    /** command prefix string */
    public static final String prefix = dev ? "#" : "/";
    /** Bot name */
    public static final String name = "Stewart";
    /** Bot description */
    public static final String desc = "Keeps track of your activity on the server.\nCommand prefix: " + prefix;
    /** author name */
    public static final String author = "Symbroson";
    /** user-id of bot host */
    public static String ownId;

    /** crypter for sbf files */
    public static Crypt crypt;
    /** guild cache map GUILD_ID:GuildWrapper */
    private static final HashMap<String, GuildWrapper> guildCache = new HashMap<>();
    /** sbf version map NUM:PREFIX */
    public static final Map<Integer, String> sbfVersions = new TreeMap<>(Comparator.reverseOrder());

    /** dataDir pattern */
    public static final String dataDir = "data/%s/";
    /** Server log file format */
    public static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyyMMdd:HHmmss");
    /** logFile format */
    private static final SimpleDateFormat logFormat = new SimpleDateFormat("'Logs/'yyyyMMdd'.log'");

    /** current logFile date */
    private static Date logDate = new Date(0);
    /** current logFile */
    private static FileOutputStream logFile;
    /** temporary result placeholder for bot error or status messages */
    public static String tRes;

    public static void sendOwnerMessage(String s)
    {
        Bot.jda.openPrivateChannelById(ownId).queue(c -> c.sendMessage(s).queue());
    }

    /** lambda interface for withGuildData */
    public interface GuildDataHandler
    {
        void exec(GuildWrapper guild);
    }

    private static void printHelp()
    {
        Log("Syntax: StewartBot [OPTIONS] BOT-TOKEN OWNER_UID CRYPT_KEY\n\n" +
            "OPTIONS\n" +
            "\t--help -h  \tprints this help and exits\n" +
            "\t--icon FILE\tset the bot icon on startup\n\n" +
            "BOT-TOKEN\n" +
            "\tDiscord bot token\n\n" +
            "OWNER_UID\n" +
            "\tYour Discord user id\n\n" +
            "CRYPT_KEY\n" +
            "\tThe key your sbf files should be encrypted with\n\n" +
            "EXIT STATUS\n" +
            "\t0 if everything went ok,\n" +
            "\t1 if minor problems (ie. invalid icon file),\n" +
            "\t2 if serious trouble (ie. no internet connection).");
    }

    /**
     * Starts the bot and the voiceCheckTask
     * @param rawArgs
     * @throws LoginException
     */
    public static void main(String[] rawArgs)
    {
        rawArgs = new String[]{"Njk3NDI0NTU3OTUwMTA3Njc4.Xo3H4Q.F4H7-mPKyyUjWhwZG_YAG1nzK-s", "253544853240152065", "NXFmYs"};
        // parse console arguments
        ArrayList<String> args = new ArrayList<>(Arrays.asList(rawArgs));

        // help option
        if(args.contains("--help") || args.contains("-h"))
        {
            printHelp();
            args.remove("--help");
            args.remove("--h");
            return;
        }

        // set icon option
        int i = args.indexOf("--icon");
        Icon icon = null;
        if(i != -1) try
        {
            args.remove(i);
            icon = Icon.from(new File(args.get(i)));
            args.remove(i);
        }
        catch (IOException e) {
            Log("Invalid icon file");
            System.exit(1);
        }

        // check for unknown left options
        for(String arg : args)
        {
            if (arg.startsWith("-"))
            {
                new IllegalArgumentException("Unknown option " + arg).printStackTrace();
                System.exit(1);
            }
        }

        // request input of arguments
        if(args.size() < 3)
        {
            Console c = System.console();
            if(c != null)
            {
                Log("read from System.console");
                System.out.print("Bot API token: ");
                args.add(new String(c.readPassword()));
                System.out.print("User ID: ");
                args.add(new String(c.readPassword()));
                System.out.print("Crypt password: ");
                args.add(new String(c.readPassword()));
            }
            else try
            {
                Log("read from BufferedReader");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Bot API token: ");
                args.add(reader.readLine());
                System.out.print("User ID: ");
                args.add(reader.readLine());
                System.out.print("Crypt password: ");
                args.add(reader.readLine());
            }
            catch (IOException e) {
                new Exception("Valid bot-token, user ID and crypt password required.").printStackTrace();
                printHelp();
                System.exit(2);
                return;
            }
        }

        Log("initialize versions");
        sbfVersions.put(24, "sbf24|");
        sbfVersions.put(23, "sbf23|");
        sbfVersions.put(22, "sbf22|");
        if(!sbfVersions.containsKey(Bot.sbfVersion))
        {
            new Exception("current sbf version not registered").printStackTrace();
            System.exit(2);
        }

        try
        {
            Log("initialize crypt");
            crypt = new Crypt(args.get(2));

            Log("initialize bot");
            ownId = args.get(1);

            // jda = JDABuilder.create(args.get(0)).build();
            jda = new JDABuilder(AccountType.BOT).setToken(args.get(0)).build();

            // try if UID is valid
            jda.openPrivateChannelById(ownId).onErrorMap(e ->
            {
                e.printStackTrace();
                System.exit(2);
                return null;
            }).complete().sendMessage(Bot.prefix + Bot.name + " is online.").queue();
        }
        catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(2);
        }
        catch (LoginException e) {
            Log("Invalid bot token.");
            e.printStackTrace();
            System.exit(2);
        }
        catch (ErrorResponseException e) {
            Log("No Internet Connection.");
            System.exit(2);
        }

        // configure bot
        EnumSet<GatewayIntent> it = jda.getGatewayIntents();
        it.add(GatewayIntent.GUILD_EMOJIS);
        it.add(GatewayIntent.GUILD_MEMBERS);
        it.add(GatewayIntent.GUILD_MESSAGES);
        it.add(GatewayIntent.GUILD_PRESENCES);
        it.add(GatewayIntent.GUILD_VOICE_STATES);
        it.add(GatewayIntent.GUILD_MESSAGE_REACTIONS);
        it.add(GatewayIntent.DIRECT_MESSAGES);
        it.add(GatewayIntent.DIRECT_MESSAGE_REACTIONS);

        jda.getPresence().setActivity(Activity.playing(
            prefix + "help || version " + ("" + version).replaceAll("(?!^|$)", ".") + (dev ? "dev" : "")));
        if(icon != null) jda.getSelfUser().getManager().setAvatar(icon).queue();
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.setAutoReconnect(true);
        jda.addEventListener(new EventHandler());

        Log("running");

        // schedule voiceCheck every 5 minutes
        EpDistributor.scheduleVoiceCheck(5 * 60 * 1000);
    }

    /** method to add delete action reaction to a message */
	public static Consumer<Message> addX = m -> m.addReaction("❌").queue();


	/* logging */

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
            if(dev) System.out.println(log);
            logFile.write((timeFormat.format(new Date()) + " " + log + "\n").getBytes());
        }
        catch (IOException e) { Log(e.getMessage()); e.printStackTrace(); }
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
            checkLogDate();
            Guild g = jda.getGuildById(folder);
            if(g != null) folder = g.getName();
            if(dev) System.out.println(folder + ":" + log);
            logFile.write((timeFormat.format(new Date()) + " " + folder + ":" + log + "\n").getBytes());
        }
        catch (IOException e) { Log(e.getMessage()); e.printStackTrace(); }
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
        saveSBF(f, guild.flush().guild);
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
	    JSONObject guild = loadSBF(f);

        if(guild == null && save)
        {
            Log("Loading fallback guildBase.sbf");
            File g = getDataFile("Server", "guildBase.sbf");
            guild = loadSBF(g);

            if(guild == null)
            {
                Log("Loading fallback guildBase.json");
                guild = loadJSON(getDataFile("Server", "guildBase.json"));

                if(guild != null)
                {
                    if(!guild.has("version")) guild.put("version", 0);
                    saveSBF(g, new GuildWrapper(guild, guildId).guild);
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
        return e.author.getId().equals(ownId) || (e.guild != null && (e.author.getIdLong() == e.guild.getOwnerIdLong() ||
                                                                      e.guild.getMember(e.author).getRoles().contains(e.guild.getRoleById(getGuildData(e.guild.getId()).adminRole))));
    }

    /**
     * tries to parse a guild role object out of various text patterns
     * @param e
     * @param name
     * @return Role
     */
    @Nullable public static Role tryGetRole(UniEvent e, String name)
    {
        if(e.msg != null)
        {
            List<Role> rs = e.msg.getMentionedRoles();
            if(rs.size() > 0) return rs.get(0);
        }

        Role r = null;
        tRes = null;

        if(name.matches("\\d+")) return e.guild.getRoleById(name);
        // @ referenced
        else if(name.matches("<@&\\d+>")) return e.guild.getRoleById(name.substring(3, name.length() - 1));
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
            else tRes = "Couldn't find nor create role.";
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
    @Nullable public static Member tryGetMember(UniEvent e, String name)
    {
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
            else tRes = "Couldn't find member.";
        } else tRes = "Invalid format.";

        return m;
    }


    /**
     * Checks if a guild member has a permission and log to tRes otherwise
     * @param m
     * @param p
     * @return hasPermission
     */
    public static boolean checkPerm(Member m, Permission p)
    {
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
    public static boolean checkPerm(UniEvent e, User u, Permission p)
    {
        assert e.guild != null;
        return checkPerm(e.guild.getMember(u), p);
    }
    /**
     * Checks if the bot has a permission and log to tRes otherwise
     * @param p
     * @return hasPermission
     */
    public static boolean checkPerm(UniEvent e, Permission p)
    {
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
}
