package core;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

public class UniEvent
{
    public final Member member;
    public final User author;
    public final MessageChannel channel;
    public final Message msg;
    public final EventType evType;
    public final Guild guild;
    public final ReactionEmote rEmote;
    public enum EventType { PRIVATE, GUILD, REACTADD, REACTRM };

    UniEvent(PrivateMessageReceivedEvent e) {
        evType = EventType.PRIVATE;
        author = e.getAuthor();
        msg = e.getMessage();
        guild = null;
        rEmote = null;
        member = null;
        channel = e.getChannel();
    }

    UniEvent(GuildMessageReceivedEvent e) {
        evType = EventType.GUILD;
        author = e.getAuthor();
        msg = e.getMessage();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = null;
    }

	UniEvent(GuildMessageReactionAddEvent e, boolean retrieveMsg) {
        evType = EventType.REACTADD;
        msg = retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null;
        author = e.getUser();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = e.getReactionEmote();
	}

	public UniEvent(GuildMessageReactionRemoveEvent e, boolean retrieveMsg) {
	    evType = EventType.REACTRM;
        msg = retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null;
        author = e.getUser();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = e.getReactionEmote();
	}
}
