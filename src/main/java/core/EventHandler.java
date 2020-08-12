package core;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.*;

public class EventHandler extends ListenerAdapter
{

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event)
    {
        super.onPrivateMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        Message msg = event.getMessage();
        List<String> attach = new ArrayList<>();
        msg.getAttachments().forEach(a -> attach.add(a.getUrl()));

        UniEvent ue = new UniEvent(event);

        Bot.Log(event.getAuthor().getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            "\033[1;30mPrivate\033[2;90m::" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[2;90;3;2m(" + ue.author.getId() + ")" +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            "\033[2;90;0;37m" + msg.getContentRaw()
        );

        handleEvent(ue);
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        super.onGuildMessageReceived(event);
        if (event.getAuthor().isBot()) return;

        Message msg = event.getMessage();
        List<String> attach = new ArrayList<>();
        msg.getAttachments().forEach(a -> attach.add(a.getUrl()));

        UniEvent ue = new UniEvent(event);

        Bot.Log(ue.guild.getId(),
            "\033[0;2m" + new Date().getTime() + " " +
            ue.guild.getName() + "\033[2;90m::\033[0;2m" +
            ue.channel.getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + ue.author.getAsTag() +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            msg.getContentRaw()
        );

        handleEvent(ue);
    }

    private static void handleEvent(UniEvent e)
    {
        String msg = e.msg.getContentRaw();

        if (msg.startsWith(Bot.prefix))
        {
            msg = msg.substring(Bot.prefix.length()).trim();

            String cmd = msg.split("\\s+")[0];
            msg = msg.replaceAll(cmd + "\\s*", "");

            if(!Commands.handleUniversal(e, cmd, msg) && e.servType == UniEvent.ServType.GUILD)
                Commands.handleGuild(e, cmd, msg);

            try { e.msg.delete().queue(); } catch(Exception __) {}

                //if (e.servType == UniEvent.ServType.PRIVATE)
            //  Commands.handlePrivate(e, args[0], Arrays.copyOfRange(args, 1, args.length));
        }
        else
        {
            if (e.servType == UniEvent.ServType.GUILD) Messages.handleGuild(e);
            //if (e.servType == UniEvent.ServType.PRIVATE) Messages.handlePrivate();
        }
    }
}