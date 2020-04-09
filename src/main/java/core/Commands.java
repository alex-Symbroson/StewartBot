package core;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static core.Bot.AUTHOR;

public class Commands extends ListenerAdapter {

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
        super.onPrivateMessageReceived(event);
        if(event.getAuthor().isBot()) return;

        Message msg = event.getMessage();
        List<String> attach = new ArrayList<>();
        msg.getAttachments().forEach(a->attach.add(a.getUrl()));

        Bot.Log(
            "\033[0;2m" + Bot.dateFormat.format(new Date()) +
            "\033[1;30mPrivate\033[2;90m::" +
            "\033[0;2m" + event.getAuthor().getAsTag() +
            "\033[2;90;3;2m(" + event.getAuthor().getId() + ")" +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            "\033[2;90;0;37m" + msg.getContentRaw()
        );

        handleEvent(new UniEvent(event));
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        super.onGuildMessageReceived(event);
        if(event.getAuthor().isBot()) return;

        Message msg = event.getMessage();
        List<String> attach = new ArrayList<>();
        msg.getAttachments().forEach(a->attach.add(a.getUrl()));

        Bot.Log(
            "\033[0;2m" + Bot.dateFormat.format(new Date()) +
            event.getGuild().getName() + "\033[2;90m::\033[0;2m" +
            event.getChannel().getName() + "\033[2;90m::\033[0;2m" +
            "\033[0;2m" + event.getAuthor().getAsTag() +
            "\033[2;90;3;2m(" + event.getAuthor().getId() + ")" +
            "\033[0;37m" + (attach.size() == 0 ? "" : Arrays.toString(attach.toArray())) + ": " +
            msg.getContentRaw()
        );

        handleEvent(new UniEvent(event));
    }

    private static void handleEvent(UniEvent e) {
        String msg = e.msg.getContentRaw();

        if (msg.startsWith(Bot.prefix))
        {
            msg = msg.substring(Bot.prefix.length()).trim();
            String[] args = msg.split("\\s+");
            handleCmd(e, args[0], Arrays.copyOfRange(args, 1, args.length));
        }
    }

    private static void handleCmd(UniEvent e, String cmd, String[] args) {
        switch(cmd) {
            case "info": {
                EmbedBuilder info = new EmbedBuilder();
                info.setTitle(Bot.name + " Bot");
                info.setDescription(Bot.desc);
                info.setColor(0xf45642);
                info.setAuthor(AUTHOR);

                if (e.channel != null && e.channel.getType() == ChannelType.TEXT)
                    info.setFooter("Created by " + AUTHOR, e.member.getUser().getAvatarUrl());

                e.channel.sendTyping().queue();
                e.channel.sendMessage(info.build()).queue();
                info.clear();
            }
            break;

            case "help": {
                e.channel.sendMessage(
                    "**" + Bot.name + " Command list**\n" +
                    "*command prefix: " + Bot.prefix + "*\n" +
                    "\n" +
                    "**info**: knows everything\n" +
                    "**help**: shows this help\n" +
                    "**dice**: rolls a dice\n" +
                    "**funfact**: tells you something you've always wanted to know\n" +
                    "\n" +
                    "Thanks for using " + Bot.name
                ).queue();
            }
            break;

            case "roll":
            case "dice": {
                int roll = new Random().nextInt(6) + 1;
                e.channel.sendMessage("<@" + e.author.getId() + ">'s roll: " + roll).queue();
            }
            break;

            case "funfact": {
                StringBuilder result = new StringBuilder();

                try {
                    URL url = new URL("http://www.randomfunfacts.com");

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    String line;
                    while ((line = rd.readLine()) != null) result.append(line);
                    rd.close();

                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                e.channel.sendMessage(result.toString().split("i>")[1].replace("</", "")).queue();
            }
            break;

            default:
                e.channel.sendMessage(Bot.name + " doesn't know '" + cmd + "'!").queue();
        }
    }
}
