package core;

import java.util.Date;

import wrapper.UserWrapper;

import static core.Bot.Log;

public class Messages
{
    static void handleGuild(UniEvent e)
    {
        Bot.withGuildData(e.guild.getId(), true, (guild) -> {
            final String gid = e.guild.getId();
        
            // check if channel is set afk
            if(guild.afkChannel.contains(e.channel.getIdLong()))
                return;
            
            // check if user level is static
            UserWrapper user = guild.getUser(e.author.getId());
            
            if(user == null) {
                Log(gid, "Ld: Couldnt load " + e.author.getAsMention());
                return;
            }

            if(user.bstatic) {
                Log(gid, "Ep: " + e.author.getAsMention() + " is static");
                return;
            }

            // check if text timeout reached
            long now = new Date().getTime();
            if(now - user.lTxtTime < 1000 * guild.textTimeout) {
                Log(gid, "Ep: " + e.author.getAsMention() + " spammed");
                return;
            }

            // replace links, remove whitespace, only count alphanumerics
            String msg = e.msg.getContentStripped().replaceAll("https?://[^\\s]+", "link").replaceAll("\\s+|[^a-zA-Z0-9].*", "");
            if(msg.length() == 0) return;

            // add ep
            int ep = (int)Math.ceil(guild.maxTextEp * Math.min(1.0, msg.length() / (float)guild.maxTextLength));
            user.textEp += ep;
            user.lTxtTime = now;
            Log(gid, "Ep: " + e.author.getAsMention() + " + " + ep);

            // update level
            ep = user.textEp + user.voiceEp + guild.levelEp / 2;
            for(int i = user.level; i < ep / guild.levelEp; i++) {
                user.level = i;
                // TODO: update user level nickname?
                Log(gid, "Lv: " + e.author.getAsMention() + " reached " + user.level);

                // update status role
                if(guild.roles.containsKey(i)) {
                    // TODO: update user role
                    Log(gid, "Lv: " + e.author.getAsMention() + " is now " + guild.roles.get(i));
                }
            }

            user.flush();
        });
    }

    static void handlePrivate(UniEvent e)
    {

    }
}
