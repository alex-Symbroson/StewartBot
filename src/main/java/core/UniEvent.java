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
class UniEvent
{
    @Nullable final Member member;
    final User author;
    final MessageChannel channel;
    @Nullable final Message msg;
    @Nullable final EventType evType;
    Guild guild;
    @Nullable final ReactionEmote rEmote;

    enum EventType { PRIVATE, GUILD, REACTADD, REACTRM };

    UniEvent(@Nullable EventType evType, Guild guild, MessageChannel channel, User author, @Nullable Member member, @Nullable Message msg, @Nullable ReactionEmote rEmote)
    {
        this.evType = evType;
        this.guild = guild;
        this.channel = channel;
        this.author = author;
        this.member = member;
        this.msg = msg;
        this.rEmote = rEmote;
    }

    UniEvent(Guild guild, MessageChannel channel, User user)
    {
        this(null, guild, channel, user, guild != null && user != null ? guild.getMember(user) : null, null, null);
    }


    UniEvent(PrivateMessageReceivedEvent e)
    {
        evType = EventType.PRIVATE;
        author = e.getAuthor();
        msg = e.getMessage();
        guild = null;
        rEmote = null;
        member = null;
        channel = e.getChannel();
    }

    UniEvent(GuildMessageReceivedEvent e)
    {
        evType = EventType.GUILD;
        author = e.getAuthor();
        msg = e.getMessage();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = null;
    }

	UniEvent(GuildMessageReactionAddEvent e, boolean retrieveMsg)
    {
        evType = EventType.REACTADD;
        msg = retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null;
        author = e.getUser();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = e.getReactionEmote();
	}

	UniEvent(GuildMessageReactionRemoveEvent e, boolean retrieveMsg)
    {
	    evType = EventType.REACTRM;
        msg = retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null;
        author = e.getUser();
        guild = e.getGuild();
        member = e.getMember();
        channel = e.getChannel();
        rEmote = e.getReactionEmote();
	}
}
