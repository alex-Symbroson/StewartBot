package core;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import util.FSManager;
import wrapper.GuildWrapper;
import wrapper.UserWrapper;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.json.JSONObject;

import static core.Bot.*;

/** contains all command handlers */
class Commands
{
    /**
     * handle commands for private and guild channels
     * @param e
     * @param cmd the command
     * @param arg the argument
     * @return command consumed
     */
    static boolean handleUniversal(UniEvent e, String cmd, String arg)
    {
        switch (cmd.toLowerCase())
        {
            case "info":
            {
                EmbedBuilder info = new EmbedBuilder();
                info.setTitle(Bot.name + " Bot");
                info.setDescription(Bot.desc);
                info.setColor(0xf45642);
                info.setAuthor(SECRETS.AUTHOR);

                if (e.channel != null && e.channel.getType() == ChannelType.TEXT)
                    info.setFooter("Hosted by " + Bot.jda.getUserById(SECRETS.OWNID).getName() +
                                   "\nCreated by " + SECRETS.AUTHOR, Bot.jda.getUserById(SECRETS.OWNID).getAvatarUrl());

                e.channel.sendMessage(info.build()).queue(Bot.addX);
                info.clear();
            } break;

            case "help":
            {
                String help = FSManager.readFile("res/help.md");
                if(help == null) break;
                e.channel.sendMessage((isAdmin(e) ? help : help.replaceAll("\\*_ _\\*[^§]+?\\*_ _\\*", ""))
                    .replace("%n", Bot.name).replace("%p", Bot.prefix)
                ).queue(Bot.addX);
            } break;

            case "roll":
            case "dice":
            {
                int roll = new Random().nextInt(6) + 1;
                e.channel.sendMessage(e.author.getAsMention() + "'s roll: " + roll).queue();
            } break;

            case "funfact":
            {
                StringBuilder result = new StringBuilder();

                try
                {
                    URL url = new URL("http://www.randomfunfacts.com");

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    String line;
                    while ((line = rd.readLine()) != null) result.append(line);
                    rd.close();
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }

                e.channel.sendMessage(result.toString().split("i>")[1].replace("</", "")).queue();
            } break;

            case "checkvoice":
                if (!e.author.getId().equals(SECRETS.OWNID)) break;
                EpDistributor.voiceCheckTimerTask.run();
                break;

            case "checklevel":
                Member m = arg.isEmpty() ? null : e.guild.getMember(e.author);
                withGuildData(e.guild.getId(), true, g ->
                    EpDistributor.updateLevel(
                        new UniEvent(e.guild, e.channel, m == null ? e.author : m.getUser()),
                        g.getUser(m.getId()), g
                    )
                );
                break;

            case "get":
            case "set":
            case "crt":
            case "put":
            case "add":
            case "del": {
                if (!e.author.getId().equals(SECRETS.OWNID)) break;
                String gid;

                if(e.guild != null) gid = e.guild.getId();
                else
                {
                    int p = arg.indexOf(' ');
                    final String guildid = arg.substring(0, p == -1 ? arg.length() : p);
                    if(Bot.getGuildData(gid = guildid, cmd.equals("crt")) == null)
                    {
                        tRes = "Invalid guild '" + guildid + "'";
                        break;
                    }
                    arg = p == -1 ? "" : arg.substring(p + 1);
                }

                final String fArg = arg
                    .replaceAll("^<@!?([0-9]+)>\\s*", "users[\"$1\"]")
                    .replaceFirst("\\s", "\0");

                e.author.openPrivateChannel().queue(c ->
                {
                    String msg;

                    try
                    {
                        if (cmd.equals("get") || cmd.equals("crt"))
                        {
                            GuildWrapper guild = Bot.getGuildData(gid);

                            if (fArg.length() == 0) msg = guild.guild.toString();
                            else
                            {
                                Object o = JsonPath.read(guild.guild.toString(), fArg);
                                if (o instanceof LinkedHashMap) msg = new JSONObject((HashMap) o).toString();
                                else msg = o.toString();
                            }
                        }
                        else
                        {
                            final String[] args = fArg.split("\0");
                            DocumentContext ctx = JsonPath.parse(Bot.getGuildJson(gid, false).toString());

                            switch (cmd)
                            {
                                case "put":
                                    String[] kv = args[1].replaceFirst(" ", "\0").split("\0");
                                    ctx.put(args[0], kv[0], kv[1].startsWith("$") ? ctx.read(args[1]) : JsonPath.parse(kv[1]).json());
                                    break;
                                case "set": ctx.set(args[0], args[1].startsWith("$") ? ctx.read(args[1]) : JsonPath.parse(args[1]).json()); break;
                                case "add": ctx.add(args[0], args[1].startsWith("$") ? ctx.read(args[1]) : JsonPath.parse(args[1]).json()); break;
                                case "del": ctx.delete(args[0]); break;
                            }

                            Object o = ctx.read(args[0]);
                            if (o instanceof LinkedHashMap) msg = new JSONObject((HashMap) o).toString();
                            else msg = o.toString();

                            Bot.withGuildData(gid, true, g -> g.loadJSON(new JSONObject(ctx.jsonString())));
                        }
                    }
                    catch (Exception err) { msg = err.toString(); }

                    for (int i = 0; i < msg.length(); i += 2000)
                        e.channel.sendMessage(msg.substring(i, Math.min(i + 2000, msg.length()))).queue(Bot.addX);
                });
            } break;

            default:
                return false;
            // e.channel.sendMessage(Bot.name + " doesn't know '" + cmd + "'!").queue();
        }
        if(tRes != null && e.channel != null) e.channel.sendMessage(tRes).queue();
        return true;
    }

    /**
     * handle commands for guild channels only
     * @param e
     * @param cmd the command
     * @param arg the argument
     */
    static void handleGuild(UniEvent e, String cmd, String arg)
    {
        String res = "";
        final boolean admin = isAdmin(e);
        final String guildId = e.guild.getId();
        boolean addX = false, addR = false, addT = false;

        switch (cmd.toLowerCase())
        {
            case "setadminrole": {
                if(!admin) break;

                Role r = tryGetRole(e, arg);
                if(tRes != null) res = tRes;
                if(r == null) break;

                addX = true;
                addR = res.contains("Created role '");

                Bot.withGuildData(guildId, true, g -> g.adminRole = r.getIdLong());
            } break;

            case "levelrole": {
                if(!admin) break;
                String[] args = arg.replaceFirst("^(\\S+)\\s+(\\d+)\\s+", "$1\0$2\0").split("\0");
                if(args.length != 3) break;
                // (add|+|remove|rm|delete|del|-)
                Role r = tryGetRole(e, args[2]);
                if(tRes != null) res = tRes;
                if(r == null) break;

                addX = true;
                addR = res.contains("Created role '");

                Bot.withGuildData(guildId, true, g -> {
                    if(args[0].matches("^(add|\\+)$")) g.roles.put(Integer.parseInt(args[1]), r.getName());
                    else if(args[0].matches("^(remove|rm|delete|del|-)$")) g.roles.remove(Integer.parseInt(args[1]));
                });
            } break;

            case "warn": {
                if(!admin) break;
                String[] args = arg.split(" ", 2);

                Member m = tryGetMember(e, args[0]);
                if(m == null)
                {
                    if(tRes != null) res = tRes;
                }
                else Bot.withGuildData(guildId, true, g ->
                {
                    // System.out.println(m.getUser().getId() + "/" + m.getId());
                    UserWrapper u = g.getUser(m.getUser().getId());
                    m.getUser().openPrivateChannel().queue(c ->
                    {
                        c.sendMessage(
                            String.format("You have been warned by %s on %s.%s", e.author.getAsTag(),
                                e.guild.getName(), args.length == 1 ? "" : " Reason: " + args[1])).queue();

                        e.channel.sendMessage(
                            String.format("%s has been warned by %s.%s", m.getAsMention(), e.author.getAsTag(),
                                args.length == 1 ? "" : " Reason: " + args[1])).queue();
                    });

                    if(++u.warnings >= g.warnStatic) handleGuild(e, "static", args[0]);
                    u.flush();
                });
            } break;

            case "static": {
                if(!admin) break;
                String[] args = arg.split(" ", 2);

                Member m = tryGetMember(e, args[0]);
                if(m == null) res = tRes;
                else Bot.withGuildData(guildId, true, g ->
                {
                    UserWrapper u = g.getUser(m.getId());
                    m.getUser().openPrivateChannel().queue(c -> c.sendMessage(
                        "You cannot gain XP on " + e.guild.getName() + " any more." +
                        (args.length == 1 ? "" : " Reason: " + args[1])).queue());

                    u.bstatic = true;
                    u.flush();
                });
            } break;

            case "unstatic": {
                if(!admin) break;
                String[] args = arg.split(" ", 2);

                Member m = tryGetMember(e, args[0]);
                if(m == null) res = tRes;
                else Bot.withGuildData(guildId, true, g ->
                {
                    UserWrapper u = g.getUser(m.getId());
                    m.getUser().openPrivateChannel().queue(c -> c.sendMessage(
                        "You can gain XP on " + e.guild.getName() + " again.").queue());

                    u.bstatic = false;
                    u.flush();
                });
            } break;

            case "category": {
                if(!admin) break;
                String[] args = arg.replaceFirst("^(\\S+)\\s+(\\S+|\"[^\"]+\")(\\s+(\\d+)(\\s+(.*))?)?$", "$1\0$2\0$4\0$6")
                    .replaceAll("\0$|(\0)\0+", "$1").split("\0");
                if(args.length < 2) break;

                if(args[0].matches("^(add|create|make|\\+)$"))
                {
                    e.guild.createCategory(args[1]).queue(c ->
                    {
                        c.createTextChannel(args[0].toLowerCase().replace(" ", "-")).queue();
                        int n = Integer.parseInt(Bot.get(args, 2, "1"));
                        String name = Bot.get(args, 3, args[0].replaceAll("\\W", ""));
                        for(int i = 1; i <= n; i++) c.createVoiceChannel(name + "-" + i).queue();
                    });
                }
                else if(args[0].matches("^(remove|rm|delete|del|-)$"))
                {
                    List<Category> cats = e.guild.getCategoriesByName(args[1], false);
                    if(cats.size() > 0) res = "delete category '" + cats.get(0).getName() + "'?";
                    addX = addT = true;
                }
            } break;

            case "poll":
            case "rolepoll": {
                if(cmd.equals("rolepoll") && !admin) break;

                StringBuilder poll = new StringBuilder();
                final String[] arr = arg.split("\\s*[\n/]\\s*");
                final String[] reacts = new String[Math.min(arr.length, 20)];
                reacts[reacts.length - 1] = "❌";

                for(int i = 0; i < reacts.length; i++)
                {
                    if(i == 0) poll.append(cmd.equals("rolepoll") ? "Role " : "").append("Poll: **").append(arr[i]).append("**\n");
                    else
                    {
                        // Custom Unicode character
                        if(Character.UnicodeBlock.of(arr[i].charAt(0)) != Character.UnicodeBlock.BASIC_LATIN)
                            reacts[i-1] = arr[i].startsWith("❌") ? "✖" : arr[i].startsWith("✅") ? "☑" : arr[i].substring(0, 2);
                            // Server Custom Emote:
                        else if(arr[i].matches("<:.*:\\d+>"))
                            reacts[i-1] = arr[i].substring(0, arr[i].indexOf(">"));
                            // Server Custom Emote
                        else if(arr[i].matches(":.*:"))
                            reacts[i-1] = arr[i].substring(0, arr[i].indexOf(">"));
                            // Default 1-10
                        else if(i <= 10) arr[i] = (reacts[i-1] = " 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟".split(" ")[i]) + " " + arr[i];
                            // Default 11-20
                        else if(i <= 20) arr[i] = (reacts[i-1] = " 🇦 🇧 🇨 🇩 🇪 🇫 🇬 🇭 🇮 🇯".split(" ")[i - 10]) + " " + arr[i];

                        if(cmd.equals("rolepoll"))
                        {
                            // Role
                            String role = arr[i].substring(reacts[i-1].length()).trim();
                            if(!role.matches("<@&\\d+>.*"))
                            {
                                Role r = tryGetRole(e, role.substring(0, role.indexOf(" ")));
                                if(tRes != null) res += tRes + "\n";
                                else arr[i] = arr[i].replace(role.substring(0, role.indexOf(" ")), r.getAsMention());
                            }
                        }

                        poll.append(arr[i]).append("\n");
                    }
                }

                e.channel.sendMessage(poll.toString()).queue(msg ->
                {
                    Bot.withGuildData(guildId, true, g -> g.polls.add(msg.getIdLong()));

                    for(String r : reacts)
                        if(!r.startsWith("<")) msg.addReaction(r).queue();
                        else msg.addReaction(e.guild.getEmoteById(r)).queue();
                });

                addR = res.contains("Created role '");
                addX = true;
            } break;

            case "embed": {
                if(!admin) break;
                EmbedBuilder em = new EmbedBuilder();
                String[] args = arg.replaceAll("(^|\\n|;)(\\w+)( +((\\S+|\"[^\"]*\"| +)*))?", "$2 $3\0").split("\0");

                for(String a : args)
                {
                    String[] v = a.replaceAll("(\"[^\"]*\"|\\S+)\\s+", "$1\0").split("\0");

                    switch(v[0].toLowerCase())
                    {
                        case "bf": case "blank": case "blankField":
                            em.addBlankField(v.length > 1 && v[1].matches("t|true|1"));
                            break;
                        case "f": case "fi": case "field":
                            em.addField(get(v, 1), get(v, 2), v.length > 3 && v[3].matches("t|true|1"));
                            break;
                        case "color": case "c":
                            try { em.setColor((Color)Color.class.getDeclaredField(v[1].toLowerCase()).get(null)); }
                            catch (Exception ex) { em.setColor(Integer.parseInt(arg)); }
                            break;
                        case "a": case "author":
                        {
                            Member m = tryGetMember(e, get(v, 1, ""));
                            if (m == null) em.setAuthor(get(v, 1), get(v, 2), get(v, 3));
                            else em.setAuthor(m.getEffectiveName(), getUrl(get(v, 2)), m.getUser().getEffectiveAvatarUrl());
                        } break;
                        case "t": case "title":
                            em.setTitle(get(v, 1), getUrl(get(v, 2)));
                            break;
                        case "d": case "desc": case "description":
                            em.setDescription(get(v, 1));
                            break;
                        case "fo": case "footer":
                            em.setFooter(get(v, 1), getUrl(get(v,2)));
                            break;
                        case "i": case "img": case "image":
                            em.setImage(getUrl(get(v, 1)));
                            break;
                        case "th": case "thumbnail":
                            em.setThumbnail(getUrl(get(v, 1)));
                            break;
                        case "tm": case "ts": case "timestamp":
                            em.setTimestamp(OffsetDateTime.now());
                            break;
                    }
                }

                e.channel.sendMessage(em.build()).queue(Bot.addX);
                em.clear();
            } break;

            case "status": {
                GuildWrapper g = Bot.getGuildData(e.guild.getId());
                if(g == null)
                {
                    res = "Guild " + e.guild.getName() + " not found.";
                    break;
                }

                EmbedBuilder em = new EmbedBuilder();
                Member m = tryGetMember(e, arg);
                if(m == null) m = e.guild.getMember(e.author);

                UserWrapper u = g.getUser(m.getId());
                if(u == null)
                {
                    res = "User " + m.getAsMention() + " not found.";
                    break;
                }

                em.setAuthor(m.getEffectiveName(), null, m.getUser().getEffectiveAvatarUrl());
                em.setTitle("Level " + u.level);

                int d = u.level == 0 ? 2 : 1, ep = d * (u.textEp + u.voiceEp) % g.levelEp;
                char[] xp = new char[20];
                for(int i = 0; i < 20; i++) xp[i] = i * g.levelEp < 20 * ep ? '█' : '▏';
                xp[20 * ep / g.levelEp] = "▏▎▍▌▋▊▉█".charAt(8 * ep / g.levelEp);
                em.addField(String.format("Xp:  %d / %d            _(%d:%d)_", ep / d, g.levelEp / d,
                    100 * u.textEp / (u.textEp + u.voiceEp), 100 - 100 * u.textEp / (u.textEp + u.voiceEp)),
                    "```┏━━━━━━━━━━━━━━━━━━━━┓\n" +
                    "▕" + new String(xp) + "▏\n" +
                    "┗━━━━━━━━━━━━━━━━━━━━┛```",
                    false);

                e.channel.sendMessage(em.build()).queue(Bot.addX);
                em.clear();
            } break;
        }
        Bot.tRes = null;

        // replies to the command and adds action reactions
        if(res != null && !res.isEmpty())
        {
            boolean fAddX = addX, fAddR = addR, fAddT = addT;
            e.channel.sendMessage(res).queue(m -> {
                if(fAddX) m.addReaction("❌").queue();

                if(fAddR || fAddT)
                {
                    if(fAddR) m.addReaction("↪").queue();
                    if(fAddT) m.addReaction("✅").queue();
                    Bot.withGuildData(guildId, true, g -> g.polls.add(m.getIdLong()));
                }
            });
        }
    }
}
