package core;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Bot
{
    public  static final String AUTHOR = "Symbroson";
    public  static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");
    private static final SimpleDateFormat logFormat = new SimpleDateFormat("'log_'yyyyMMdd'.log'");

    public static JDA jda;
    public static String prefix = ">";
    public static String name = "Stewart";
    public static String desc = "Keeps track of your activity on the server.";

    private static long logDate = 0;
    private static PrintStream logFile;


    // Main method
    public static void main(String[] args) throws LoginException {
        Log("started");

        jda = JDABuilder.createDefault(util.SECRETS.TOKEN).build();
        jda.getPresence().setActivity(Activity.playing("..."));
        jda.getPresence().setStatus(OnlineStatus.ONLINE);
        jda.setAutoReconnect(true);
        jda.addEventListener(new Commands());
    }

    private static long tStart = 0, tStep = 0, tDiff = 0;

    public static void tic() {
        tStart = tStep = System.nanoTime();
    }

    public static void toc() {
        long t = System.nanoTime();
        tDiff = t - tStep;
        System.out.println(String.format("dtime: %dus", tDiff / 1000));
        tStep = t;
    }

    private static void checkLogDate() {
        Date now = new Date();
        long curDate = now.getTime() / (1000 * 60 * 60 * 24);
        if (logDate != curDate) {
            logDate = curDate;
            try {
                logFile = new PrintStream(logFormat.format(now));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public static void Log(String log) {
        checkLogDate();
        logFile.println(log);
        System.out.println(log);
    }
}
