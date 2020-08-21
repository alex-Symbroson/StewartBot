package core;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;

import javax.annotation.Nullable;

/**
 * Wrapper for all discord events
 * Undefined fields will be left null
 */
public class UniEvent
{
    @Nullable public final Member member;
    @Nullable public final User author;
    @Nullable public final MessageChannel channel;
    @Nullable public final Message msg;
    @Nullable public final EventType evType;
    @Nullable public Guild guild;
    @Nullable public final ReactionEmote rEmote;

    public enum EventType { PRIVATE, GUILD, REACTADD, REACTRM };

    UniEvent(EventType evType, Guild guild, MessageChannel channel, User author, Member member, Message msg, ReactionEmote rEmote) {
        this.evType = evType;
        this.guild = guild;
        this.channel = channel;
        this.author = author;
        this.member = member;
        this.msg = msg;
        this.rEmote = rEmote;
    }

    public UniEvent(Guild guild, MessageChannel channel, User user) {
        this(null, guild, channel, user, null, null, null);
    }


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
