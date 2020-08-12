package core;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import wrapper.GuildWrapper;

import org.json.JSONObject;

import javax.security.auth.login.LoginException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import static util.FSManager.*;

public class Bot
{
    public static final String dataDir = "data/%s/";
    public static final String AUTHOR = "Symbroson";
    public static final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat logFormat = new SimpleDateFormat("'" + dataDir + "/Logs/'yyyyMMdd'.log'");

    public static JDA jda;
    public static String prefix = "#";
    public static String name = "Stewart";
    public static String desc = "Keeps track of your activity on the server.";

    private static Date logDate = new Date(0);
    private static FileOutputStream logFile;


    // Main method
    public static void main(String[] args) throws LoginException
    {
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

    private static void checkLogDate() throws FileNotFoundException
    {
        Date curDate = new Date();
        if (!dayFormat.format(logDate).equals(dayFormat.format(curDate)))
        {
            logDate = curDate;
            File f = new File(String.format(logFormat.format(new Date()), "Server"));
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            logFile = new FileOutputStream(f, true);
            //Zipper.setFile(serverLogFormat.format(curDate));
        }
    }

    public static void Log(String log)
    {
        try
        {
            checkLogDate();
            System.out.println(log);

            //Zipper.addFile(logFormat.format(new Date()), log);
            logFile.write((log + "\n").getBytes());
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
        }
    }

    public static void Log(String folder, String log)
    {
        System.out.println(log);

        //Zipper.setFile(String.format(userLogFormat.format(new Date()), folder), "passwd");
        //Zipper.addFile(logFormat.format(new Date()), log);

        /*try
        {
            File f = getDataFile(folder);
            new FileOutputStream(f, true).write((log + "\n").getBytes());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }*/
    }

    public static GuildWrapper getGuildData(String guildId) {
	    File f = getDataFile(guildId, "guild.sbf");
	    int key = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	    JSONObject guild = loadSBF(f, key);
	    
	    if(guild == null) {
	        Log("Loading fallback guildBase.sbf");
            File g = getDataFile("Server", "guildBase.sbf");
	        int l = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	        guild = loadSBF(g, l);
	        
	        if(guild == null) {
	            Log("Loading fallback guildBase.json");
	            guild = loadJSON(getDataFile("Server", "guildBase.json"));
	            
	            if(guild == null) {
	                Log("Critical error. GuildData couldn't be loaded nor restored.");
	                return null;
	            }
	        }
	    }
	
	    return new GuildWrapper(guild, guildId);
	}

	public static void withGuildData(String guildId, boolean save, Lambda cb) {
	    File f = getDataFile(guildId, "guild.sbf");
	    int key = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	    JSONObject guild = loadSBF(f, key);
	    
	    if(guild == null && save) {
	        Log("Loading fallback guildBase.sbf");
            File g = getDataFile("Server", "guildBase.sbf");
	        int l = guildId.hashCode() ^ f.getAbsoluteFile().hashCode() ^ host();
	        guild = loadSBF(g, l);
	        
	        if(guild == null) {
	            Log("Loading fallback guildBase.json");
	            guild = loadJSON(getDataFile("Server", "guildBase.json"));
	            saveSBF(g, guild, l);
                
	            if(guild == null) {
	                Log("Critical error. GuildData couldn't be loaded nor restored.");
	                return;
	            }
	        }
	    }
    
        GuildWrapper g = new GuildWrapper(guild, guildId);
        cb.exec(guild == null ? null : g);
	    if(save) saveSBF(f, g.flush().guild, key);
    }
}
