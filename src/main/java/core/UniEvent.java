package core;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

public class UniEvent
{
    public final Member member;
    public final User author;
    public final MessageChannel channel;
    public final Message msg;
    public final ServType servType;

    public enum ServType { PRIVATE, GUILD };

    UniEvent(PrivateMessageReceivedEvent e) {
        servType = ServType.PRIVATE;
        author = e.getAuthor();
        msg = e.getMessage();
        member = null;
        channel = e.getChannel();
    }

    UniEvent(GuildMessageReceivedEvent e) {
        servType = ServType.GUILD;
        author = e.getAuthor();
        msg = e.getMessage();
        member = e.getMember();
        channel = e.getChannel();
    }
}
