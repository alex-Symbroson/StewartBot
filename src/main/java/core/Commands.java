package core;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.restaction.RoleAction;
import util.FSManager;
import util.SECRETS;
import wrapper.GuildWrapper;
import wrapper.UserWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Random;

import com.jayway.jsonpath.JsonPath;

import static core.Bot.AUTHOR;

public class Commands
{
    static boolean handleUniversal(UniEvent e, String cmd, String arg)
    {
        switch (cmd)
        {
            case "info":
            {
                EmbedBuilder info = new EmbedBuilder();
                info.setTitle(Bot.name + " Bot");
                info.setDescription(Bot.desc);
                info.setColor(0xf45642);
                info.setAuthor(AUTHOR);

                if (e.channel != null && e.channel.getType() == ChannelType.TEXT)
                    info.setFooter("Created by " + AUTHOR, e.member.getUser().getAvatarUrl());

                e.channel.sendTyping().queue();
                e.channel.sendMessage(info.build()).queue();
                info.clear();
            }
            break;

            case "help":
            {
                String help = FSManager.readFile("res/help.md");
                if(help == null) break;
                e.channel.sendMessage((isAdmin(e) ? help : help.replaceAll("\\*_ _\\*[^§]+?\\*_ _\\*", ""))
                    .replace("%n", Bot.name).replace("%p", Bot.prefix)
                ).queue();
            }
            break;

            case "roll":
            case "dice":
            {
                int roll = new Random().nextInt(6) + 1;
                e.channel.sendMessage(e.author.getAsMention() + "'s roll: " + roll).queue();
            }
            break;

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
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }

                e.channel.sendMessage(result.toString().split("i>")[1].replace("</", "")).queue();
            }
            break;

            case "poll":
            {
                String res = "";
                final String[] arr = arg.split("\s*[\n/]\s*");
                final String[] reacts = new String[arr.length - 1];

                for(int i = 0; i < Math.min(arr.length, 20); i++) {
                    if(i == 0) res += "**" + arr[i] + "**\n";
                    else {
                        if(Character.UnicodeBlock.of(arr[i].charAt(0)) != Character.UnicodeBlock.BASIC_LATIN) reacts[i-1] = arr[i].substring(0, 2);
                        else if(arr[i].matches("<:.*:\\d+>")) reacts[i-1] = arr[i].replaceAll("<:.*:(\\d+)>", "<$1");
                        else if(i <= 10) arr[i] = (reacts[i-1] = " 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟".split(" ")[i]) + " " + arr[i];
                        else if(i <= 20) arr[i] = (reacts[i-1] = " 🇦 🇧 🇨 🇩 🇪 🇫 🇬 🇭 🇮 🇯".split(" ")[i - 10]) + " " + arr[i];
                        res += arr[i] + "\n";
                    }
                }

                e.channel.sendMessage(res).queue(msg -> {
                    for(String r : reacts)
                        if(!r.startsWith("<")) msg.addReaction(r).queue();
                        else msg.addReaction(e.guild.getEmoteById(r.substring(1))).queue();
                });
            }
            break;

            default:
                return false;
                // e.channel.sendMessage(Bot.name + " doesn't know '" + cmd + "'!").queue();
        }
        return true;
    }

    static boolean isAdmin(UniEvent e) {
        return e.guild == null ? false : e.author.getIdLong() == e.guild.getOwnerIdLong() || e.author.getIdLong() == SECRETS.OWNID ||
               e.guild.getMember(e.author).getRoles().contains(e.guild.getRoleById(Bot.getGuildData(e.guild.getId()).adminRole));
    }

    private static Object[] tryGetRole(UniEvent e, String name) {
        Role r = null;
        String res = "";
        
        // @ referenced
        if(name.matches("<@&\\d+>")) r = e.guild.getRoleById(name.substring(3, name.length() - 1));
        // name referenced
        else if(name.matches("[\\w -]+")) {
            List<Role> roles = e.guild.getRolesByName(name, true);
            
            // create role if not exists
            if(roles.size() == 0) {
                RoleAction a;
                e.guild.createRole().setName(name).setColor(0xff0000).complete();
                res = "Created role '" + name + "'.";
                roles = e.guild.getRolesByName(name, false);
            }

            if(roles.size() > 0) r = roles.get(0);
            else res = "Coulnd't find nor create role.";
        }
        else {
            if(name.isEmpty()) res = "No role specified.";
            else res = "Invalid format.";
        }

        return new Object[]{r, res};
    }

    private static Object[] tryGetMember(UniEvent e, String name) {
        Member m = null;
        String res = "";
        
        // @ referenced
        if(name.matches("<@!\\d+>")) m = e.guild.getMemberById(name.substring(3, name.length() - 1));
        // name referenced
        else if(name.matches("[\\w -]+")) {
            List<Member> members = e.guild.getMembersByName(name, true);

            if(members.size() > 0) m = members.get(0);
            else res = "Coulnd't find member.";
        }

        return new Object[]{m, res};
    }

    static void handleGuild(UniEvent e, String cmd, String arg)
    {
        String res = "";
        final Boolean admin = isAdmin(e);
        final String guildId = e.guild.getId();
        
        switch (cmd)
        {
            case "get":
            {
                if (e.author.getIdLong() != SECRETS.OWNID) return;

                GuildWrapper guild = Bot.getGuildData(guildId);
                assert guild != null;

                if (arg.length() == 0) res = guild.guild.toString();
                else
                    e.author.openPrivateChannel().complete().sendMessage(
                        JsonPath.read(guild.guild.toString(),
                            arg.replaceAll("^<@!([0-9]+)>", "users[\"$1\"]")).toString()
                    ).queue();
            } break;

            case "setAdminRole": 
            {
                if(!admin) break;
                final Object[] r = tryGetRole(e, arg);
                res = (String)r[1];

                if(r[0] == null) break;
                Bot.withGuildData(guildId, true, g -> g.adminRole = ((net.dv8tion.jda.api.entities.Guild)r[0]).getIdLong());
            } break;

            case "warn":
            {
                if(!admin) break;
                String[] args = arg.split(" ", 2);
                Object m[] = tryGetMember(e, args[0]);
                res = (String)m[1];

                if(m[0] == null) break;
                Bot.withGuildData(guildId, true, g ->
                {
                    UserWrapper u = g.getUser(((Member)m[0]).getId());
                    ((Member)m[0]).getUser().openPrivateChannel().complete().sendMessage(
                        "You were warned by " + e.author.getAsTag() + " on " + e.guild.getName() + "." +
                        (args.length == 1 ? "" : " Reason: " + args[1])).queue();

                    if(++u.warnings >= g.warnStatic) handleGuild(e, "static", args[0]);
                    u.flush();
                });
            } break;

            case "static": 
            {
                if(!admin) break;
                String[] args = arg.split(" ", 2);
                Object m[] = tryGetMember(e, args[0]);
                res = (String)m[1];

                if(m[0] == null) break;
                Bot.withGuildData(guildId, true, g ->
                {
                    UserWrapper u = g.getUser(((Member)m[0]).getId());
                    ((Member)m[0]).getUser().openPrivateChannel().complete().sendMessage(
                        "You cannot gain XP on " + e.guild.getName() + " any more." +
                        (args.length == 1 ? "" : " Reason: " + args[1])).queue();

                    u.bstatic = true;
                    u.flush();
                });
            } break;

            case "unstatic": 
            {
                if(!admin) break;
                String[] args = arg.split(" ", 2);
                Object m[] = tryGetMember(e, args[0]);
                res = (String)m[1];

                if(m[0] == null) break;
                Bot.withGuildData(guildId, true, g ->
                {
                    UserWrapper u = g.getUser(((Member)m[0]).getId());
                    ((Member)m[0]).getUser().openPrivateChannel().complete().sendMessage(
                        "You can gain XP on " + e.guild.getName() + " again.").queue();
                        
                    u.bstatic = false;
                    u.flush();
                });
            } break;

            case "rolepoll": {
                if(!admin) break;

                final String[] arr = arg.split("\s*[\n/]\s*");
                final String[] reacts = new String[arr.length - 1];

                for(int i = 0; i < Math.min(arr.length, 20); i++) {
                    if(i == 0) res += "**" + arr[i] + "**\n";
                    else {
                        if(Character.UnicodeBlock.of(arr[i].charAt(0)) != Character.UnicodeBlock.BASIC_LATIN) reacts[i-1] = arr[i].substring(0, 2);
                        else if(arr[i].matches("<:.*:\\d+>")) reacts[i-1] = arr[i].substring(0, arr[i].indexOf(">"));
                        else if(i <= 10) arr[i] = (reacts[i-1] = " 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟".split(" ")[i]) + " " + arr[i];
                        else if(i <= 20) arr[i] = (reacts[i-1] = " 🇦 🇧 🇨 🇩 🇪 🇫 🇬 🇭 🇮 🇯".split(" ")[i - 10]) + " " + arr[i];
                        res += arr[i] + "\n";
                    }
                }

                e.channel.sendMessage(res).queue(msg -> { 
                    for(String r : reacts)
                        if(!r.startsWith("<")) msg.addReaction(r).queue();
                        else msg.addReaction(e.guild.getEmoteById(r));
                });
                return;
            }
        }

        if(!res.isEmpty()) 
            e.channel.sendMessage(res).queue();
    }


    static void handlePrivate(UniEvent e, String cmd, String arg)
    {
        switch (cmd)
        {
        }
    }
}
