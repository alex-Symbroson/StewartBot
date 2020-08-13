package core;

import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.*;

import core.UniEvent.EventType;
import org.jetbrains.annotations.NotNull;

import static core.Bot.isAdmin;

public class EventHandler extends ListenerAdapter
{

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event)
    {
        super.onPrivateMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        UniEvent ue = new UniEvent(event);

        List<String> attach = new ArrayList<>();
        ue.msg.getAttachments().forEach(a -> attach.add(a.getUrl()));

        Bot.Log(event.getAuthor().getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            "\033[1;30mPrivate\033[2;90m::" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[2;90;3;2m(" + ue.author.getId() + ")" +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            "\033[2;90;0;37m" + ue.msg.getContentDisplay()
        );

        handleEvent(ue);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        super.onGuildMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        UniEvent ue = new UniEvent(event);

        List<String> attach = new ArrayList<>();
        ue.msg.getAttachments().forEach(a -> attach.add(a.getUrl()));

        Bot.Log(ue.guild.getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            ue.guild.getName() + "\033[2;90m::\033[0;2m" +
            ue.channel.getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) +
            ": " + ue.msg.getContentDisplay()
        );

        handleEvent(ue);
    }

    private static void handleEvent(UniEvent e)
    {
        String msg = e.msg.getContentRaw();

        if(msg.toLowerCase().equals("stewart"))
            msg = Bot.prefix + "info";

        if (msg.startsWith(Bot.prefix))
        {
            msg = msg.substring(Bot.prefix.length()).trim();

            String cmd = msg.split("\\s+")[0].toLowerCase();
            msg = msg.replaceAll(cmd + "\\s*", "");

            if(!Commands.handleUniversal(e, cmd, msg) && e.evType == UniEvent.EventType.GUILD)
                Commands.handleGuild(e, cmd, msg);

            try { e.msg.delete().queue(); } catch (Exception ignored) {}

            //if (e.servType == UniEvent.ServType.PRIVATE)
            //  Commands.handlePrivate(e, args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        else
        {
            if (e.evType == UniEvent.EventType.GUILD) Messages.handleGuild(e);
            //if (e.servType == UniEvent.ServType.PRIVATE) Messages.handlePrivate();
        }
    }


    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        super.onGuildMessageReactionAdd(event);
        if (event.getMember().getUser().isBot()) return;

        UniEvent ue = new UniEvent(event, true);

        Bot.Log(ue.guild.getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            ue.guild.getName() + "\033[2;90m::\033[0;2m" +
            ue.channel.getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + ue.author.getAsTag() +
            " {+" + event.getReactionEmote().getName() + "}" +
            ": " + ue.msg.getContentDisplay().replaceFirst("(^.{0,32})[\\w\\W]*", "$1") + " ..."
        );

        HandleReaction(ue);
    }

    @Override
    public void onGuildMessageReactionRemove(@NotNull GuildMessageReactionRemoveEvent event) {
        super.onGuildMessageReactionRemove(event);
        if (event.getMember().getUser().isBot()) return;

        UniEvent ue = new UniEvent(event, true);

        Bot.Log(ue.guild.getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            ue.guild.getName() + "\033[2;90m::\033[0;2m" +
            ue.channel.getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + ue.author.getAsTag() +
            " {-" + event.getReactionEmote().getName() + "}" +
            ": " + ue.msg.getContentDisplay().replaceFirst("(^.{0,32})[\\w\\W]*", "$1") + " ..."
        );

        HandleReaction(ue);
    }

    private static boolean delMsg(UniEvent e)
    {
        try
        {
            e.msg.delete().queue();
            Bot.withGuildData(e.guild.getId(), true, g ->
            {
                g.polls.remove(e.msg.getIdLong());
                g.flush();
            });
            return true;
        }
        catch (Exception __) { return false; }
    }

    private static void HandleReaction(UniEvent e)
    {
        if(e.author.isBot()) return;
        String msg = e.msg.getContentRaw();

        if(msg.startsWith("Role Poll:"))
            if(Objects.requireNonNull(Bot.getGuildData(e.guild.getId())).polls.contains(e.msg.getIdLong()))
            {
                for (String s : msg.split("\n"))
                    if (s.contains(e.rEmote.getEmoji()))
                    {
                        Role r = e.guild.getRoleById(s.replaceFirst(".*?<@&(\\d+)>.*", "$1"));
                        if (e.evType == EventType.REACTRM) e.guild.removeRoleFromMember(e.member, r).queue();
                        else if (e.evType == EventType.REACTADD) e.guild.addRoleToMember(e.member, r).queue();
                        break;
                    }
            }

        if (!e.msg.getAuthor().isBot() || !isAdmin(e)) return;
        if (e.evType == EventType.REACTADD)
        {
            switch (e.rEmote.getName())
            {
                case "❌":
                    if (msg.split("\n", 2)[0].matches("^(Role )?Poll:.*"))
                    {
                        e.msg.addReaction("✅").queue();
                        e.msg.editMessage("Delete " + e.msg.getContentRaw().replaceFirst(":", "?")).queue();
                    } else delMsg(e);
                    break;

                case "↪":
                    for (String l : msg.split("\n"))
                        if (l.matches("Created role '.*'."))
                        {
                            List<Role> roles = e.guild.getRolesByName(l.substring(14, l.length() - 2), false);
                            if (!roles.isEmpty()) roles.get(0).delete().queue();
                        }
                    delMsg(e);
                    break;

                case "✅":
                    if (msg.matches("delete category '.*'\\?"))
                    {
                        List<Category> cats = e.guild.getCategoriesByName(msg.substring(17, msg.length() - 2), false);
                        if (cats.isEmpty()) break;
                        cats.get(0).getChannels().forEach(d -> d.delete().queue());
                        cats.get(0).delete().queue();
                        delMsg(e);
                    } else if (msg.split("\n", 2)[0].matches("^Delete (Role )?Poll\\?.*"))
                        delMsg(e);
                    break;
            }
        }
        else
        {
            if(e.rEmote.getName().equals("❌") && msg.split("\n", 2)[0].matches("^Delete (Role )?Poll\\?.*"))
            {
                e.msg.removeReaction("✅").queue();
                e.msg.editMessage(msg.replaceFirst("^Delete ((Role )?Poll)\\?", "$1:")).queue();
            }
        }
    }
}