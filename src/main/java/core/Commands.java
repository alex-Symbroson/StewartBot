package core;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;
import util.FSManager;
import wrapper.GuildWrapper;
import util.Helper;
import wrapper.UniEvent;
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
import java.util.function.BiConsumer;

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
                info.setAuthor(author);

                if (e.channel != null && e.channel.getType() == ChannelType.TEXT)
                    info.setFooter("Hosted by " + Bot.jda.getUserById(ownId).getName() +
                                   "\nCreated by " + author, Bot.jda.getUserById(ownId).getAvatarUrl());

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
                    Log(ex.getMessage());
                    ex.printStackTrace();
                }

                e.channel.sendMessage(result.toString().split("i>")[1].replace("</", "")).queue();
            } break;

            case "checkvoice":
                if (!e.author.getId().equals(ownId)) break;
                EpDistributor.voiceCheckTimerTask.run();
                break;

            case "checklevel":
                Member m = arg.isEmpty() ? null : tryGetMember(e, arg);
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
                if (!e.author.getId().equals(ownId)) break;
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
                // split up arguments
                String[] args = arg.replaceFirst("^(\\S+)\\s+(\\d+)\\s*", "$1\0$2\0").split("\0");

                if(args[0].matches("get"))
                {
                    res = "Level Roles:\n";
                    GuildWrapper g = Bot.getGuildData(guildId);
                    Object[] data = g.roles.keySet().toArray();
                    Arrays.sort(data);
                    for(Object en : data)
                    {
                        Role r = Bot.tryGetRole(e, g.roles.get(en));
                        res += en + ": " + (r != null ? r.getAsMention() : "unknown role") + "\n";
                    }
                }
                else if (args[0].matches("add|\\+|remove|rm|delete|del|-"))
                {
                    Bot.withGuildData(guildId, true, g ->
                    {
                        if (args[0].matches("add|\\+"))
                        {
                            Role r = args.length == 3 ? tryGetRole(e, args[2]) : null;

                            if (r == null) return;
                            if (r.getName().matches(".*[^\\w -].*"))
                                g.roles.put(Integer.parseInt(args[1]), r.getId());
                            else
                                g.roles.put(Integer.parseInt(args[1]), r.getName());
                        }
                        else if (args[0].matches("remove|rm|delete|del|-"))
                            g.roles.remove(Integer.parseInt(args[1]));
                    });
                }

                if(tRes != null) addR = (res = tRes).contains("Created role '");
                addX = true;
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
                // split up arguments
                String[] args = arg.replaceFirst("^(\\S+)\\s+(\\S+|\"[^\"]+\")(\\s+(\\d+)(\\s+(.*))?)?$", "$1\0$2\0$4\0$6")
                    .replaceAll("\0$|(\0)\0+", "$1").split("\0");
                if(args.length < 2) break;

                if(args[0].matches("^(add|create|make|\\+)$"))
                {
                    e.guild.createCategory(args[1]).queue(c ->
                    {
                        c.createTextChannel(args[0].toLowerCase().replace(" ", "-")).queue();
                        int n = Integer.parseInt(Helper.get(args, 2, "1"));
                        String name = Helper.get(args, 3, args[0].replaceAll("\\W", ""));
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

            case "clear":
            {
                if (!admin) break;
                String[] args = arg.split("\\s+");
                final LinkedList<Message> msgs = new LinkedList<>();

                BiConsumer<Message, Integer> delMsgBy = (m, i) ->
                {
                    switch (Helper.get(args, i, ""))
                    {
                        case "cmd": if (!m.getContentRaw().startsWith(prefix)) return;
                            break;
                        case "cmds":
                            if (m.getContentRaw().substring(0, 1).matches(Helper.get(args, i + 1, "[/%$§!#~+-]")))
                                return;
                            break;
                        case "bot": if (m.getAuthor().getIdLong() != jda.getSelfUser().getIdLong()) return;
                            break;
                        case "bots": if (!m.getAuthor().isBot()) return;
                            break;
                    }
                    if(m.getTimeCreated().isBefore(OffsetDateTime.now().minusWeeks(2))) return;
                    msgs.add(m);
                };

                switch (Helper.get(args, 0, "dflt"))
                {
                    case "before":
                        e.channel.getHistoryBefore(Helper.get(args, 1, ""), 100).queue(h -> {
                            h.getRetrievedHistory().forEach(m -> delMsgBy.accept(m, 2));
                            ((TextChannel)e.channel).deleteMessages(msgs).queue();
                        });
                        break;
                    case "after":
                        e.channel.getHistoryAfter(Helper.get(args, 1, ""), 100).queue(h -> {
                            h.getRetrievedHistory().forEach(m -> delMsgBy.accept(m, 2));
                            ((TextChannel)e.channel).deleteMessages(msgs).queue();
                        });
                        break;
                    case "around":
                        e.channel.getHistoryAround(Helper.get(args, 1, ""), 100).queue(h -> {
                            h.getRetrievedHistory().forEach(m -> delMsgBy.accept(m, 2));
                            ((TextChannel)e.channel).deleteMessages(msgs).queue();
                        });
                        break;
                    case "beginning":
                        e.channel.getHistoryFromBeginning(100).queue(h ->
                            h.getRetrievedHistory().forEach(m -> {
                                delMsgBy.accept(m, 1);
                                ((TextChannel)e.channel).deleteMessages(msgs).queue();
                            }));
                        break;
                    default:
                        e.channel.getHistory().retrievePast(100).queue(h -> {
                            h.forEach(m -> delMsgBy.accept(m, 1));
                            ((TextChannel)e.channel).deleteMessages(msgs).queue();
                        });
                        break;
                }
            } break;

            case "embed": {
                if(!admin) break;
                EmbedBuilder em = new EmbedBuilder();
                // split up arguments
                String[] args = arg.replaceAll("\\s*\\n\\s*", "\0").split("\0");

                for(String a : args)
                {
                    // split up arguments
                    String[] v = a.replaceAll("\"([^\"]*)\"|(\\S+)\\s+", "$1$2\0").split("\0");
                    boolean inl = false;
                    String url = null, value = null;

                    switch(v[0].toLowerCase())
                    {
                        case "bf": case "blank": case "blankfield": {
                            em.addBlankField(false);
                        } break;
                        case "ib": case "ibf": case "inlineblank": case "inlineblankfield": {
                            em.addBlankField(true);
                        } break;
                        case "f": case "fi": case "sf": case "field": case "singlefield": {
                            if (v.length > 1) value = String.join(" ", Arrays.copyOfRange(v, 2, v.length));
                            em.addField(Helper.get(v, 1), value, false);
                        } break;
                        case "if": case "inlinefield": {
                            if (v.length > 1) value = String.join(" ", Arrays.copyOfRange(v, 2, v.length));
                            em.addField(Helper.get(v, 1), value, true);
                        } break;
                        case "color": case "c": {
                            try { em.setColor((Color)Color.class.getDeclaredField(v[1].toLowerCase()).get(null)); }
                            catch (Exception ex) { em.setColor(Integer.parseInt(arg)); }
                        } break;
                        case "a": case "author": {
                            Member m = tryGetMember(e, Helper.get(v, 1, ""));
                            if (m == null) em.setAuthor(Helper.get(v, 1), Helper.get(v, 2), Helper.get(v, 3));
                            else em.setAuthor(m.getEffectiveName(), Helper.getUrl(Helper.get(v, 2)), m.getUser().getEffectiveAvatarUrl());
                        } break;
                        case "t": case "title": {
                            url = Helper.getUrl(Helper.get(v, v.length - 1));
                            value = String.join(" ", Arrays.copyOfRange(v, 1, v.length - (url != null ? 1 : 0)));
                            em.setTitle(value, url);
                        } break;
                        case "d": case "desc": case "description": {
                            value = String.join(" ", Arrays.copyOfRange(v, 1, v.length));
                            em.appendDescription(value + "\n");
                            } break;
                        case "fo": case "footer": {
                            url = Helper.getUrl(Helper.get(v, v.length - 1));
                            value = String.join(" ", Arrays.copyOfRange(v, 1, v.length - (url != null ? 1 : 0)));
                            em.setFooter(value, url);
                        } break;
                        case "i": case "img": case "image": {
                            em.setImage(Helper.getUrl(Helper.get(v, 1)));
                        } break;
                        case "th": case "thumbnail": {
                            em.setThumbnail(Helper.getUrl(Helper.get(v, 1)));
                        } break;
                        case "tm": case "ts": case "timestamp": {
                            em.setTimestamp(OffsetDateTime.now());
                            } break;
                    }
                }

                if(!em.isEmpty()) {
                    e.channel.sendMessage(em.build()).queue(Bot.addX);
                    em.clear();
                }
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

                // generate ep status bar
                int d = u.level == 0 ? 2 : 1, ep = d * (u.textEp + u.voiceEp) % g.levelEp;
                int sum = u.textEp + u.voiceEp;
                char[] xp = new char[20];
                for(int i = 0; i < 20; i++) xp[i] = i * g.levelEp < 20 * ep ? '█' : '▏';
                xp[20 * ep / g.levelEp] = "▏▎▍▌▋▊▉█".charAt(8 * ep / g.levelEp);
                em.addField(String.format("Xp:  %d / %d            _(%d:%d)_", ep / d, g.levelEp / d,
                    sum == 0 ? 0 : 100 * u.textEp / sum, sum == 0 ? 0 : 100 - 100 * u.textEp / sum),
                    "```┏━━━━━━━━━━━━━━━━━━━━┓\n" +
                    "▕" + new String(xp) + "▏\n" +
                    "┗━━━━━━━━━━━━━━━━━━━━┛```",
                    false);

                e.channel.sendMessage(em.build()).queue(Bot.addX);
                em.clear();
            } break;

            case "mafia":
            {
                String[] args = arg.split("\\s+");

                try
                {
                    if (args[0].matches("\\d+"))
                    {
                        int n = Integer.parseInt(args[0]);
                    }
                    else if (args[0].matches("\\d+\\.\\d+"))
                    {
                        double p = Double.parseDouble(args[0]);
                    }
                    else
                    {
                        ;
                    }
                }
                catch (NumberFormatException ex)
                {
                    ex.printStackTrace();
                }

                break;
            }
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
