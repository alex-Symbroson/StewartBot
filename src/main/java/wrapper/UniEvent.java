package wrapper;

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
    public final User author;
    public final MessageChannel channel;
    @Nullable public final Message msg;
    @Nullable public final EventType evType;
    public Guild guild;
    @Nullable public final ReactionEmote rEmote;

    public enum EventType { PRIVATE, GUILD, REACTADD, REACTRM };

    public UniEvent(@Nullable EventType evType, Guild guild, MessageChannel channel, User author,
                    @Nullable Member member, @Nullable Message msg, @Nullable ReactionEmote rEmote)
    {
        this.evType = evType;
        this.guild = guild;
        this.channel = channel;
        this.author = author;
        this.member = member != null ? member : guild != null && author != null ? guild.getMember(author) : null;
        this.msg = msg;
        this.rEmote = rEmote;
    }

    public UniEvent(Guild guild, MessageChannel channel, User author)
    {
        this(null, guild, channel, author, guild != null && author != null ? guild.getMember(author) : null, null, null);
    }

    public UniEvent(PrivateMessageReceivedEvent e)
    {
        this(EventType.PRIVATE, null, e.getChannel(), e.getAuthor(), null, e.getMessage(), null);
    }

    public UniEvent(GuildMessageReceivedEvent e)
    {
        this(EventType.GUILD, e.getGuild(), e.getChannel(), e.getAuthor(), e.getMember(), e.getMessage(), null);
    }

    public UniEvent(GuildMessageReactionAddEvent e, boolean retrieveMsg)
    {
        this(EventType.REACTADD, e.getGuild(), e.getChannel(), e.getUser(), e.getMember(),
            retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null, e.getReactionEmote());
	}

    public UniEvent(GuildMessageReactionRemoveEvent e, boolean retrieveMsg)
    {
	    this(EventType.REACTRM, e.getGuild(), e.getChannel(), e.getUser(), e.getMember(),
            retrieveMsg ? e.getChannel().retrieveMessageById(e.getMessageId()).complete() : null, e.getReactionEmote());
	}
}
