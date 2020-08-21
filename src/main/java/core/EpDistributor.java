package core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import wrapper.GuildWrapper;
import wrapper.UserWrapper;

import java.util.*;

import static core.Bot.Log;
import static core.Bot.tryGetRole;

/** Manages voice and text ep distribution */
public class EpDistributor
{
    /** timer for {@link #voiceCheckTimerTask}  task */
    public static final Timer voiceCheckTimer = new Timer();

    private static class Voice
    {
        /** Voice user status flags */
        public static final int LEFT = -1, TALKED = 0, JOINED = 1;
        /** maps names to status IDs */
        private static final String[] statusName = { "left","talked","joined" };

        public final User user;
        public final Guild guild;
        /**
         * -1: left voice<br>
         *  0: in voice<br>
         *  1: joined voice
         */
        public int status;
        /** last voice check timestamp */
        public long lVoiceTime;

        /**
         * Create new voiceUser with status -1
         * @param user the guild member represented by the object
         * @param guild the guild the voice channel is in
         */
        public Voice(User user, Guild guild, long time)
        {
            status = TALKED;
            lVoiceTime = time;
            this.user = user;
            this.guild = guild;
        }

        /**
         * hashes userId and guildId together
         * @return hash
         */
        public int hash()
        {
            return Objects.hash(user.getIdLong(), guild.getIdLong());
        }

        /**
         * Saves a voice participant to a hash map if not existent<br>
         * The users status will be increased by 1 leaving it in 2 possible states:<br>
         *   - if the user did not exist in voiceUsers and just has been created the status will change from 0 to 1 (joined)<br>
         *   - if the user did exist in voiceUsers the status will be changed from -1 to 0
         * @param targetMap
         * @return saved voice object
         */
        public Voice genStatus(LinkedList<Voice> targetMap)
        {
            Voice voice = null;
            for(Voice v : targetMap)
                if (hash() == v.hash())
                {
                    voice = v;
                    break;
                }

            if(voice == null) targetMap.push(voice = this);
            return voice.inc();
        }

        /**
         * increment status
         * @return self
         */
        private Voice inc()
        {
            ++status;
            return this;
        }
    }

    /** Check voice channels of every guild and update user ep */
    static final TimerTask voiceCheckTimerTask = new TimerTask()
    {
        private final LinkedList<EpDistributor.Voice> voiceUsers = new LinkedList<>();

        @Override
        public void run()
        {
            // reset status
            long time = new Date().getTime();
            voiceUsers.forEach(v -> v.status = Voice.LEFT);

            // check voice channel members of every guild
            Bot.jda.getGuilds().forEach(g ->
            {
                GuildWrapper guild = Bot.getGuildData(g.getId());
                if (guild == null) return;
                g.getVoiceChannels().stream().filter(vc -> vc.getMembers().size() > 0).forEach(vc ->
                {
                    if (guild.isAfk(vc.getIdLong())) return;

                    List<Member> ms = vc.getMembers();
                    final long n = ms.stream().filter(m -> !m.getUser().isBot()).count();
                    for (Member m : ms)
                    {
                        if (m.getUser().isBot() || guild.isStatic(m.getId())) continue;
                        Voice v = new EpDistributor.Voice(m.getUser(), g, time).genStatus(voiceUsers);
                        if(n < 2) v.status = Voice.JOINED;
                    }
                });
            });

            ArrayList<String> changed = new ArrayList<>();

            /* do actions on voiceUsers based on the user status:
             * -1: remove from voiceUsers
             *  0: apply ep and save guild to changed
             *  1: nothing
             */
            for (EpDistributor.Voice v : voiceUsers)
            {
                if(v.status != Voice.TALKED)
                    Bot.Log("voice " + v.guild.getName() + ":" + v.user.getName() + " " +
                            Bot.get(Voice.statusName, v.status + 1, "invalid " + v.status));

                GuildWrapper g = Bot.getGuildData(v.guild.getId());
                if (g == null) continue;
                if (v.status == Voice.TALKED || v.status == Voice.LEFT)
                {
                    if (!changed.contains(v.guild.getId())) changed.add("g" + v.guild.getId());
                    UserWrapper u = g.getUser(v.user.getId());
                    u.voiceEp += Math.round(g.voiceEppm * (time - v.lVoiceTime) / 6e4);
                    System.out.println(v.user.getName() + ": dt " + ((time - v.lVoiceTime) / 6e4) + " = " + ((int) Math.round(g.voiceEppm * (time - v.lVoiceTime) / 6e4)) + "vEp");
                    v.lVoiceTime = time;
                    updateLevel(new UniEvent(v.guild, null, v.user), u, g);
                }
                if (v.status == Voice.LEFT)
                    changed.add("d" + v.hash());
            }

            // save changed guild data
            changed.forEach(id ->
            {
                if (id.charAt(0) == 'g') Bot.withGuildData(id.substring(1), true, null);
                else if (id.charAt(0) == 'd') voiceUsers.remove(Integer.parseInt(id.substring(1)));
            });
        }
    };

    /** Start {@link #voiceCheckTimerTask} */
    static void scheduleVoiceCheck(long period) {
        voiceCheckTimer.scheduleAtFixedRate(voiceCheckTimerTask, 5000, period);
    }

    /** handles guild messages*/
    static void handleGuildMessage(UniEvent e)
    {
        Bot.withGuildData(e.guild.getId(), true, guild -> {
            if(guild.isAfk(e.channel.getIdLong())) return;

            UserWrapper user = guild.getUser(e.author.getId(), true);
            if(guild.isStatic(e.author.getId())) return;

            // check if text timeout reached
            long now = new Date().getTime();
            if(now - user.lTxtTime < 1000 * guild.textTimeout) {
                Log(e.guild.getId(), "Ep: " + e.author.getAsMention() + " spammed");
                return;
            }

            // replace links, remove whitespace, only count alphanumerics
            String msg = e.msg.getContentStripped()
                .replaceAll("https?://[^\\s]+", "link")
                .replaceAll("\\s+|[^a-zA-Z0-9].*", "");
            if(msg.length() == 0) return;

            // add ep
            int ep = (int)Math.ceil(guild.maxTextEp * Math.min(1.0, msg.length() / (float)guild.maxTextLength));
            ep += e.msg.getAttachments().size() * guild.maxTextEp / 2;
            user.textEp += ep;
            user.lTxtTime = now;
            Log(e.guild.getId(), "Ep: " + e.author.getAsMention() + " + " + ep);
            updateLevel(e, user, guild);
        });
    }

    /**
     * Updates the user level based on the current ep and tries to update the member nickname, the level role and sends a notification
     *
     * @param e UniEvent containing the message/voice author, the notify channel and the target guild
     * @param user
     * @param guild
     */
    private static void updateLevel(UniEvent e, UserWrapper user, GuildWrapper guild)
    {
        // update level
        int ep = user.textEp + user.voiceEp + guild.levelEp / 2;
        Bot.tRes = null;

        for(int level = user.level + 1; level <= ep / guild.levelEp; level++) {
            user.level = level;

            Member m = e.guild.getMember(e.author);
            if(m != null) {
                try
                {
                    if(Bot.checkPerm(e, Permission.NICKNAME_MANAGE)) break;
                    m.modifyNickname(m.getEffectiveName().replaceFirst(" ?(Lv. \\d+)*$", " Lv. " + user.level)).queue();
                } catch (HierarchyException ex) {
                    Bot.Log("Hierarchy exception for " + m.getEffectiveName());
                }
            }
            Log(guild.gid, "Lv: " + e.author.getAsMention() + " reached " + user.level);

            // update level role
            if(guild.roles.containsKey(level)) {
                if(Bot.checkPerm(e, Permission.MANAGE_ROLES)) break;
                try
                {
                    for (Map.Entry<Integer, String> lvRole : guild.roles.entrySet())
                    {
                        Role r = tryGetRole(e, lvRole.getValue());
                        if(r == null)
                        {
                            Bot.sendOwnerMessage("Invalid role " + e.guild.getName() + ":" + lvRole.getValue());
                            continue;
                        }
                        if (lvRole.getKey() == level) e.guild.addRoleToMember(e.author.getId(), r).queue();
                        else if(e.member.getRoles().contains(r)) e.guild.removeRoleFromMember(e.author.getId(), r).queue();
                        Log(guild.gid, "Lv: " + e.author.getAsMention() + " is now " + r.getAsMention());
                    }
                }
                catch(HierarchyException ex)
                {
                    Bot.Log("Hierarchy exception for " + e.author.getName());
                }
            }
        }
        user.flush();

        // notify
        if(Bot.tRes != null && e.channel != null) e.channel.sendMessage(Bot.tRes).queue();
        Bot.tRes = null;
    }
}
