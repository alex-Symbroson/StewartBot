package core;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import wrapper.GuildWrapper;

import org.json.JSONObject;

import javax.security.auth.login.LoginException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static util.FSManager.*;

public class Bot
{
    public static final boolean dev = false;

    public static final Integer version = 22;
    public static final Map<Integer, String> versions = new TreeMap<>((a, b) -> b.compareTo(a));

    public static final String dataDir = "data/%s/";
    public static final String AUTHOR = "Symbroson";

    public static final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat logFormat = new SimpleDateFormat("'Logs/'yyyyMMdd'.log'");

    public static JDA jda;
    public static String prefix = dev ? "#" : "/";
    public static String name = "Stewart";
    public static String desc = "Keeps track of your activity on the server.";

    private static Date logDate = new Date(0);
    private static FileOutputStream logFile;


    // Main method
    public static void main(String[] args) throws LoginException
    {
        versions.put(22, "sbf22|");
        versions.put(21, "sbfv21");
        versions.put(20, "sbfv2");
        versions.put(10, "sbf");
        Log("started");
        
        //jda = JDABuilder.createDefault(util.SECRETS.TOKEN).build();
        jda = new JDABuilder(AccountType.BOT).setToken(util.SECRETS.TOKEN).build();
        jda.getPresence().setActivity(Activity.playing("..."));
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.setAutoReconnect(true);
        jda.addEventListener(new EventHandler());

        Log("running");
    }

    private static long tStart = 0, tStep = 0, tDiff = 0;

	public static Consumer<? super Message> addX = m -> m.addReaction("❌").queue();

    public static void tic()
    {
        tStart = tStep = System.nanoTime();
    }

    public static void toc()
    {
        long t = System.nanoTime();
        tDiff = t - tStep;
        System.out.println(String.format("dtime: %dus", tDiff / 1000));
        tStep = t;
    }

    private static File getLogFile(String folder)
    {
        return getDataFile(folder, logFormat.format(new Date()));
    }

    private static void checkLogDate() throws FileNotFoundException
    {
        Date curDate = new Date();
        if (!dayFormat.format(logDate).equals(dayFormat.format(curDate)))
        {
            logDate = curDate;
            logFile = new FileOutputStream(getLogFile("Server"), true);
        }
    }

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

    public static GuildWrapper getGuildData(String guildId)
    {
	    File f = getDataFile(guildId, "guild.sbf");
	    int key = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	    JSONObject guild = loadSBF(f, key);
	    
        if(guild == null)
        {
	        Log("Loading fallback guildBase.sbf");
            File g = getDataFile("Server", "guildBase.sbf");
	        int l = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	        guild = loadSBF(g, l);
	        
            if(guild == null)
            {
	            Log("Loading fallback guildBase.json");
	            guild = loadJSON(getDataFile("Server", "guildBase.json"));
                if(guild != null && !guild.has("version")) guild.put("version", 0);
            }
        }
        
        if(guild == null)
        {
            Log("Critical error. GuildData couldn't be loaded nor restored.");
            return null;
        }
        else if(!guild.has("version")) guild.put("version", 0);

	    return new GuildWrapper(guild, guildId);
	}

    public static void withGuildData(String guildId, boolean save, Lambda cb)
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
                if(guild != null && !guild.has("version")) guild.put("version", 0);
                saveSBF(g, new GuildWrapper(guild, guildId).guild, l);
            }
        }

        if(guild == null)
        {
            Log("Critical error. GuildData couldn't be loaded nor restored.");
            return;
        }
        else if(!guild.has("version")) guild.put("version", 0);
    
        GuildWrapper g = new GuildWrapper(guild, guildId);
        cb.exec(guild == null ? null : g);
	    if(save) saveSBF(f, g.flush().guild, key);
    }
}
