package core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import util.Helper;
import wrapper.GuildWrapper;
import wrapper.UniEvent;
import wrapper.UserWrapper;

import java.util.*;

import static core.Bot.*;

/**
 * Manages voice and text ep distribution
 */
class EpDistributor
{
    /**
     * timer for {@link #voiceCheckTimerTask}  task
     */
    static final Timer voiceCheckTimer = new Timer();

    private static class Voice
    {
        /**
         * Voice user status flags
         */
        public static final int LEFT = -1, TALKED = 0, JOINED = 1;
        /**
         * maps names to status IDs
         */
        private static final String[] statusName = {"left", "talked", "joined"};

        public final User user;
        public final Guild guild;
        /**
         * -1: left voice<br>
         * 0: in voice<br>
         * 1: joined voice
         */
        public int status;
        /**
         * last voice check timestamp
         */
        public long lVoiceTime;

        /**
         * Create new voiceUser with status -1
         *
         * @param user  the guild member represented by the object
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
         *
         * @return hash
         */
        public int hash()
        {
            return Objects.hash(user.getIdLong(), guild.getIdLong());
        }

        /**
         * Saves a voice participant to a hash map if not existent<br>
         * The users status will be increased by 1 leaving it in 2 possible states:<br>
         * - if the user did not exist in voiceUsers and just has been created the status will change from 0 to 1 (joined)<br>
         * - if the user did exist in voiceUsers the status will be changed from -1 to 0
         *
         * @param targetMap
         * @return saved voice object
         */
        public Voice genStatus(LinkedList<Voice> targetMap)
        {
            Voice voice = null;
            for (Voice v : targetMap)
                if (hash() == v.hash())
                {
                    voice = v;
                    break;
                }

            if (voice == null) targetMap.push(voice = this);
            return voice.inc();
        }

        /**
         * increment status
         *
         * @return self
         */
        private Voice inc()
        {
            ++status;
            return this;
        }
    }

    /**
     * Check voice channels of every guild and update user ep
     */
    static final TimerTask voiceCheckTimerTask = new TimerTask()
    {
        private final LinkedList<EpDistributor.Voice> voiceUsers = new LinkedList<>();

        @Override
        public void run()
        {

            // reset status
            long time = new Date().getTime();
            voiceUsers.forEach(v -> v.status = Voice.LEFT);
            //Bot.Log("-- checkvoice");
            //Bot.Log(voiceUsers.toString());

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
                        if (n < 2) v.status = Voice.JOINED;
                    }
                });
            });

            //String _ll = voiceUsers.stream().map(Object::toString).reduce("", (a, b) -> a + "," + b);
            //if(!_ll.isEmpty()) Bot.Log(_ll);
            ArrayList<String> changed = new ArrayList<>();

            /* do actions on voiceUsers based on the user status:
             * -1: remove from voiceUsers
             *  0: apply ep and save guild to changed
             *  1: nothing
             */
            for (EpDistributor.Voice v : voiceUsers)
            {
                if (v.status != Voice.TALKED)
                    Log(v.guild.getId(), String.format("voice %s:%s %s", v.guild.getName(),
                        v.user.getName(), Helper.get(Voice.statusName, v.status + 1, "invalid " + v.status)));

                GuildWrapper g = Bot.getGuildData(v.guild.getId());
                if (g == null) continue;
                if (v.status == Voice.TALKED || v.status == Voice.LEFT)
                {
                    if (!changed.contains("g" + v.guild.getId())) changed.add("g" + v.guild.getId());
                    UserWrapper u = g.getUser(v.user.getId(), true);
                    int ep = (int) Math.round(g.voiceEppm * (time - v.lVoiceTime) / 6e4);
                    u.voiceEp += ep;
                    Log(v.guild.getId(), String.format("voice Ep: %s + %d = %d (%ss)",
                        v.user.getName(), ep, u.voiceEp, (int) (time - v.lVoiceTime) / 1000));
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
                else if (id.charAt(0) == 'd') voiceUsers.removeIf(v -> Long.parseLong(id.substring(1)) == v.hash());
            });
        }
    };

    /**
     * Start {@link #voiceCheckTimerTask}
     */
    static void scheduleVoiceCheck(long period)
    {
        voiceCheckTimer.scheduleAtFixedRate(voiceCheckTimerTask, 5000, period);
    }

    /**
     * handles guild messages
     */
    static void handleGuildMessage(UniEvent e)
    {
        Bot.withGuildData(e.guild.getId(), true, guild ->
        {
            if (guild.isAfk(e.channel.getIdLong())) return;

            UserWrapper user = guild.getUser(e.author.getId(), true);
            if (guild.isStatic(e.author.getId())) return;

            // check if text timeout reached
            long now = new Date().getTime();
            if (now - user.lTxtTime < 1000 * guild.textTimeout)
            {
                Log(e.guild.getId(), "Ep: " + e.author.getAsTag() + " spammed");
                return;
            }

            // remove double words
            String msg = e.msg.getContentStripped().toLowerCase();
            for (int i = 0; i < 3 && msg.matches(".*\\b(\\w+)\\b.*?\\b\\1\\b.*"); i++)
                msg = msg.replaceAll("\\b(\\w+)\\b(.*?)\\b\\1\\b", "$1$2");

            // replace links, remove whitespace, only count alphanumerics
            msg = msg
                .replaceAll("https?://[^\\s]+", "alinktosomewherelseinthewww")
                .replaceAll("\\W+", "")
                .replaceAll("(\\w)\1+", "$1"); // double chars

            // add ep
            int ep = (int) Math.ceil(guild.maxTextEp * Math.min(1.0, msg.length() / (float) guild.maxTextLength));
            ep += e.msg.getAttachments().size() * guild.maxTextEp / 2;
            if (ep == 0) return;
            user.textEp += ep;
            user.lTxtTime = now;
            Log(e.guild.getId(), String.format("text Ep: %s + %d = %d",
                e.author.getAsTag(), ep, user.textEp));
            updateLevel(e, user, guild);
        });
    }

    /**
     * Updates the user level based on the current ep and tries to update the member nickname, the level role and sends a notification
     *
     * @param e     UniEvent containing the message/voice author, the notify channel and the target guild
     * @param user
     * @param guild
     */
    static void updateLevel(UniEvent e, UserWrapper user, GuildWrapper guild)
    {
        // update level
        user.flush();
        int ep = user.textEp + user.voiceEp + guild.levelEp / 2;
        Bot.tRes = null;
        Role newRole = null;
        List<Role> mroles = e.member.getRoles();
        String newNickname = null;

        for (int level = e.evType == null && e.channel != null ? 0 : user.level + 1; level <= ep / guild.levelEp; level++)
        {
            user.level = level;

            if (!Bot.checkPerm(e, Permission.NICKNAME_MANAGE))
                newNickname = e.member.getEffectiveName().replaceFirst(" ?(Lv. \\d+)*$", " Lv. " + user.level);
            Log(guild.gid, "level: " + e.author.getAsTag() + " reached Lv. " + user.level);

            // update level role
            if (!guild.roles.containsKey(level)) continue;
            if (Bot.checkPerm(e, Permission.MANAGE_ROLES)) continue;

            for (Map.Entry<Integer, String> lvRole : guild.roles.entrySet())
            {
                Role r = tryGetRole(e, lvRole.getValue());
                if (r == null)
                    Bot.sendOwnerMessage("Invalid role " + e.guild.getName() + ":" + lvRole.getValue());
                else if (lvRole.getKey() == level) newRole = r;
                    // remove previous role
                else if (mroles.contains(r)) try
                {   e.guild.removeRoleFromMember(e.author.getId(), r).queue(); }
                catch (HierarchyException ignored) { }
            }
        }
        user.flush();

        if (newNickname != null && !e.member.getEffectiveName().equals(newNickname))
            try
            {
                e.member.modifyNickname(newNickname).queue();
            }
            catch (HierarchyException ignored) { }


        if (newRole != null && !mroles.contains(newRole))
        {
            e.guild.addRoleToMember(e.author.getId(), newRole).queue();
            Log(guild.gid, "level: " + e.author.getAsTag() + " is now " + newRole.getName());
            // TODO: if(ev.type == UniEvent.TEXT) e.channe.SendMessage(guild.gid, "Lv: " + e.author.getAsTag() + " is now " + r.getName()).queue();
        }

        // notify
        if (Bot.tRes != null && e.channel != null) e.channel.sendMessage(Bot.tRes).queue();
        Bot.tRes = null;
    }
}
