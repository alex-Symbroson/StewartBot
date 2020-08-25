package core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import wrapper.UniEvent;
import wrapper.UniEvent.EventType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static core.Bot.isAdmin;
import static core.Bot.timeFormat;

class EventHandler extends ListenerAdapter
{
    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event)
    {
        super.onPrivateMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        UniEvent ue = new UniEvent(event);

        ArrayList<String> attach = new ArrayList<>();
        ue.msg.getAttachments().forEach(a -> attach.add(a.getUrl()));
        String cd = ue.msg.getContentDisplay();

        Bot.Log(event.getAuthor().getId(),
            "\033[0;2m" + timeFormat.format(new Date()) + " " +
            "\033[1;30mPrivate\033[2;90m::" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[2;90;3;2m(" + ue.author.getId() + ")" +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            "\033[2;90;0;37m" + (cd.matches("^\\w") ? cd : "Message(" + cd.length() + ")")
        );

        handleEvent(ue);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        super.onGuildMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        UniEvent ue = new UniEvent(event);

        ArrayList<String> attach = new ArrayList<>();
        ue.msg.getAttachments().forEach(a -> attach.add(a.getUrl()));
        String cd = ue.msg.getContentDisplay();

        Bot.Log(ue.guild.getId(),
            "\033[0;2m" + timeFormat.format(new Date()) + " " +
            ue.guild.getName() + "\033[2;90m::\033[0;2m" +
            ue.channel.getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) +
            ": " + (cd.matches("^\\w") ? cd : "Message(" + cd.length() + ")")
        );

        handleEvent(ue);
    }

    /**
     * separates bot commands from other messages
     * @param e
     */
    private static void handleEvent(UniEvent e)
    {
        String msg = e.msg.getContentRaw();

        if(msg.toLowerCase().equals("stewart"))
            msg = Bot.prefix + "info";

        if (msg.startsWith(Bot.prefix))
        {
            msg = msg.substring(Bot.prefix.length()).trim();

            String cmd = msg.split("\\s+")[0];
            msg = msg.replaceAll(cmd + "\\s*", "");

            if(!Commands.handleUniversal(e, cmd, msg) && e.evType == UniEvent.EventType.GUILD)
                Commands.handleGuild(e, cmd, msg);

            try { e.msg.delete().queue(); } catch (Exception ignored) {}

            //if (e.servType == UniEvent.ServType.PRIVATE)
            //  Commands.handlePrivate(e, args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        else
        {
            if (e.evType == UniEvent.EventType.GUILD) EpDistributor.handleGuildMessage(e);
            //if (e.servType == UniEvent.ServType.PRIVATE) Messages.handlePrivate();
        }
    }


    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event)
    {
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
    public void onGuildMessageReactionRemove(@NotNull GuildMessageReactionRemoveEvent event)
    {
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

    /**
     * Handles reaction events for Polls and bot actions
     * @param e
     */
    private static void HandleReaction(UniEvent e)
    {
        if(e.author.isBot()) return;
        String msg = e.msg.getContentRaw();

        // role poll replies
        if(msg.startsWith("Role Poll:"))
            if(Bot.getGuildData(e.guild.getId()).polls.contains(e.msg.getIdLong()))
            {
                for (String s : msg.split("\n"))
                    if (s.contains(e.rEmote.getEmoji()))
                    {
                        Role r = e.guild.getRoleById(s.replaceFirst(".*?<@&(\\d+)>.*", "$1"));
                        if(r == null) continue;
                        if (e.evType == EventType.REACTRM) e.guild.removeRoleFromMember(e.member, r).queue();
                        else if (e.evType == EventType.REACTADD) e.guild.addRoleToMember(e.member, r).queue();
                        break;
                    }
            }

        // bot actions
        if (!e.msg.getAuthor().isBot() || !isAdmin(e)) return;
        Bot.tRes = null;
        if (e.evType == EventType.REACTADD)
        {
            switch (e.rEmote.getName())
            {
                // delete bot message
                case "❌":
                    if (msg.split("\n", 2)[0].matches("^(Role )?Poll:.*"))
                    {
                        if(Bot.checkPerm(e, Permission.MESSAGE_ADD_REACTION)) break;
                        e.msg.addReaction("✅").queue();
                        e.msg.editMessage("Delete " + e.msg.getContentRaw().replaceFirst(":", "?")).queue();
                    } else Bot.delMsg(e);
                    break;

                // undo action
                case "↪":
                    Bot.delMsg(e);
                    for (String l : msg.split("\n"))
                        if (l.matches("Created role '.*'."))
                        {
                            if(Bot.checkPerm(e, Permission.MANAGE_ROLES)) break;
                            List<Role> roles = e.guild.getRolesByName(l.substring(14, l.length() - 2), false);
                            if (!roles.isEmpty()) roles.get(0).delete().queue();
                        }
                    break;

                // confirm action
                case "✅":
                    if (msg.matches("delete category '.*'\\?"))
                    {
                        Bot.delMsg(e);
                        if(Bot.checkPerm(e, Permission.MANAGE_CHANNEL)) break;
                        List<Category> cats = e.guild.getCategoriesByName(msg.substring(17, msg.length() - 2), false);
                        if (cats.isEmpty()) break;
                        cats.get(0).getChannels().forEach(d -> d.delete().queue());
                        cats.get(0).delete().queue();
                    } else if (msg.split("\n", 2)[0].matches("^Delete (Role )?Poll\\?.*"))
                        Bot.delMsg(e);
                    break;
            }
        }
        else
        {
            // undo confirmable action
            if(e.rEmote.getName().equals("❌") && msg.split("\n", 2)[0].matches("^Delete (Role )?Poll\\?.*"))
            {
                e.msg.removeReaction("✅").queue();
                e.msg.editMessage(msg.replaceFirst("^Delete ((Role )?Poll)\\?", "$1:")).queue();
            }
        }

        if(Bot.tRes != null) e.channel.sendMessage(Bot.tRes).queue();
        Bot.tRes = null;
    }
}