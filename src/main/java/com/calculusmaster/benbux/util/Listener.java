package com.calculusmaster.benbux.util;

import com.calculusmaster.benbux.BenBux;
import com.calculusmaster.benbux.commands.Leaderboard;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Random;
import java.util.stream.Collectors;

public class Listener extends ListenerAdapter
{
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event)
    {
        User user = event.getAuthor();
        String[] msg = event.getMessage().getContentRaw().toLowerCase().trim().split("\\s+");
        JSONObject userData;
        Random r = new Random();

        if(msg[0].startsWith(Global.PREFIX))
        {
            msg[0] = msg[0].substring(Global.PREFIX.length()).toLowerCase();

            if(!Mongo.isRegistered(user))
            {
                Mongo.addUserData(user);
                Mongo.updateTimestamp(user, "work", event.getMessage().getTimeCreated().minusDays(Global.CMD_WORK_COOLDOWN[0] + 1));
                Mongo.updateTimestamp(user, "crime", event.getMessage().getTimeCreated().minusDays(Global.CMD_CRIME_COOLDOWN[0] + 1));
                Mongo.updateTimestamp(user, "steal", event.getMessage().getTimeCreated().minusDays(Global.CMD_STEAL_COOLDOWN[0] + 1));
            }
            userData = Mongo.UserInfo(user);
            boolean isNoob = userData.getInt("benbux") == Global.STARTING_BALANCE;

            if(!userData.getString("username").equals(user.getAsTag()))
            {
                Mongo.BenBuxDB.updateOne(Filters.eq("userID", user.getId()), Updates.set("username", user.getAsTag()));
                userData = Mongo.UserInfo(user);
            }

            if(!userData.has("timestamp_steal"))
            {
                Mongo.updateTimestamp(user, "steal", event.getMessage().getTimeCreated().minusDays(Global.CMD_STEAL_COOLDOWN[0] + 1));
                reply(event, getReplyEmbed(user.getAsTag(), "Try that command again"));
                return;
            }

            if(Global.CMD_WORK.contains(msg[0]) && msg.length == 1)
            {
                if(isNoob || !TimeUtils.isOnCooldown(userData, event, Global.CMD_WORK_COOLDOWN, "work"))
                {
                    int earnedAmount = r.nextInt(Global.MAX_WORK_AMOUNT);
                    Mongo.changeUserBalance(userData, user, earnedAmount);
                    reply(event, getReplyEmbed(user.getAsTag(), "Earned " + earnedAmount + " BenBux!"));

                    Mongo.updateTimestamp(user, "work", event.getMessage().getTimeCreated());
                }
                else reply(event, getCooldownEmbed(user.getAsTag(), TimeUtils.timeLeft(userData, event, Global.CMD_WORK_COOLDOWN, "work")));
            }
            else if(Global.CMD_CRIME.contains(msg[0]))
            {
                if(isNoob || !TimeUtils.isOnCooldown(userData, event, Global.CMD_CRIME_COOLDOWN, "crime"))
                {
                    int amount = r.nextInt(Global.MAX_CRIME_AMOUNT) * (r.nextInt(10) < 6 ? -1 : 1);
                    Mongo.changeUserBalance(userData, user, amount);
                    reply(event, getReplyEmbed(user.getAsTag(), (amount < 0 ? " lost " : " earned ") + Math.abs(amount) + " BenBux!"));

                    if(amount < 0) Mongo.updateTimestamp(user, "crime", event.getMessage().getTimeCreated());
                }
                else reply(event, getCooldownEmbed(user.getAsTag(), TimeUtils.timeLeft(userData, event, Global.CMD_CRIME_COOLDOWN, "crime")));
            }
            else if(Global.CMD_BAL.contains(msg[0]))
            {
                JSONObject targetData = null;
                if(msg.length > 1)
                {
                    targetData = new JSONObject(Mongo.BenBuxDB.find(Filters.eq("userID", getUserIDFromMention(msg[1]))).first().toJson());
                }

                if(msg.length > 1 && targetData.isEmpty()) reply(event, getReplyEmbed(user.getAsTag()));
                else reply(event, getReplyEmbed(msg.length > 1 ? targetData.getString("username") : user.getAsTag(), "Cash: **" + (msg.length > 1 ? targetData.getInt("benbux") : userData.getInt("benbux")) + "** BenBux\nBank: **" + (msg.length > 1 ? targetData.getInt("bank") : userData.getInt("bank")) + "** BenBux"));
            }
            else if(Global.CMD_DEPOSIT.contains(msg[0]))
            {
                if(msg.length != 2)
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }
                if(userData.getInt("benbux") < 0)
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }

                if(msg[1].toLowerCase().equals("all"))
                {
                    reply(event, getReplyEmbed(user.getAsTag(), "Deposited all money to your bank! (" + userData.getInt("benbux") + " BenBux)"));
                    Mongo.depositBank(userData, user, userData.getInt("benbux"));
                }
                else if(msg[1].chars().allMatch(Character::isDigit) && Integer.parseInt(msg[1]) <= userData.getInt("benbux") && Integer.parseInt(msg[1]) > 0)
                {
                    reply(event, getReplyEmbed(user.getAsTag(), "Deposited " + msg[1] + " BenBux to your bank!"));
                    Mongo.depositBank(userData, user, Integer.parseInt(msg[1]));
                }
                else reply(event, getReplyEmbed(user.getAsTag()));
            }
            else if(Global.CMD_WITHDRAW.contains(msg[0]))
            {
                if(msg.length != 2)
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }

                if(msg[1].toLowerCase().equals("all"))
                {
                    reply(event, getReplyEmbed(user.getAsTag(), "Withdrew all money from your bank! (" + userData.getInt("bank") + " BenBux)"));
                    Mongo.withdrawBank(userData, user, userData.getInt("bank"));
                }
                else if(msg[1].chars().allMatch(Character::isDigit) && Integer.parseInt(msg[1]) <= userData.getInt("bank") && Integer.parseInt(msg[1]) > 0)
                {
                    reply(event, getReplyEmbed(user.getAsTag(), "Withdrew " + msg[1] + " BenBux from your bank!"));
                    Mongo.withdrawBank(userData, user, Integer.parseInt(msg[1]));
                }
                else reply(event, getReplyEmbed(user.getAsTag()));
            }
            else if(Global.CMD_LEADERBOARD.contains(msg[0]))
            {
                reply(event, Leaderboard.getLeaderboard());
            }
            else if(Global.CMD_STEAL.contains(msg[0]))
            {
                if(msg.length != 2)
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }

                if(!TimeUtils.isOnCooldown(userData, event, Global.CMD_STEAL_COOLDOWN, "steal"))
                {
                    if(getUserIDFromMention(msg[1]).equals(user.getId())) reply(event, getReplyEmbed(user.getAsTag()));;

                    JSONObject victimData = new JSONObject(Mongo.BenBuxDB.find(Filters.eq("userID", getUserIDFromMention(msg[1]))).first().toJson());
                    boolean canSteal = new Random().nextInt(100) < 40;

                    System.out.println(victimData.toString());

                    if(canSteal && victimData.getInt("benbux") > 0)
                    {
                        int stolenAmount = new Random().nextInt(victimData.getInt("benbux"));
                        reply(event, getReplyEmbed(user.getAsTag(), "Stole " + stolenAmount + " BenBux from " + victimData.getString("username")));
                        Mongo.changeUserBalance(userData, user, stolenAmount);
                        Mongo.changeUserBalance(victimData, getUserIDFromMention(msg[1]), stolenAmount * -1);
                    }
                    else
                    {
                        int lostAmount = new Random().nextInt((userData.getInt("bank") + userData.getInt("benbux")) / 4);
                        boolean lost = new Random().nextInt(5) < 3;
                        if(lost) Mongo.changeUserBalance(userData, user, lostAmount * -1);
                        reply(event, getReplyEmbed(user.getAsTag(), "Robbery failed! You lost " + (lost ? lostAmount + " BenBux :(" : "nothing :)")));
                    }

                    Mongo.updateTimestamp(user, "steal", event.getMessage().getTimeCreated());
                }
                else reply(event, getCooldownEmbed(user.getAsTag(), TimeUtils.timeLeft(userData, event, Global.CMD_STEAL_COOLDOWN, "steal")));
            }
            else if(Global.CMD_PAY.contains(msg[0]))
            {
                if(msg.length != 3)
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }
                if(getUserIDFromMention(msg[1]).equals(user.getId()))
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                };

                JSONObject receiverData = new JSONObject(Mongo.BenBuxDB.find(Filters.eq("userID", getUserIDFromMention(msg[1]))).first().toJson());

                if(receiverData.isEmpty())
                {
                    reply(event, getReplyEmbed(user.getAsTag()));
                    return;
                }

                if(Integer.parseInt(msg[2]) <= 0) reply(event, getReplyEmbed(user.getAsTag()));
                else
                {
                    Mongo.changeUserBalance(userData, user, Integer.parseInt(msg[2]) * -1);
                    Mongo.changeUserBalance(receiverData, getUserIDFromMention(msg[1]), Integer.parseInt(msg[2]));

                    reply(event, getReplyEmbed(user.getAsTag(), "You paid **" + msg[2] + " BenBux** to" + getUserTagFromMention(msg[1]) + "!"));
                }
            }
            else if(Global.CMD_CHANGELOG.contains(msg[0]))
            {
                if(msg.length == 1 || msg[1].equals("latest")) reply(event, getReplyEmbed(user.getAsTag(), ChangelogEntry.getLatest()));
                else if(Global.changelogs.stream().noneMatch(cl -> cl.getVersion().equals(msg[1]))) reply(event, getReplyEmbed(user.getAsTag()));
                else reply(event, getReplyEmbed(user.getAsTag(), Global.changelogs.stream().filter(cl -> cl.getVersion().equals(msg[1])).collect(Collectors.toList()).get(0).getFullChangelog()));
            }
            else if(Global.CMD_VERSION.contains(msg[0]))
            {
                reply(event, getReplyEmbed(user.getAsTag(), "Version " + BenBux.VERSION));
            }
            else if(msg[0].toLowerCase().equals(Global.CMD_RESTART))
            {
                Mongo.removeUser(user);
                reply(event, getReplyEmbed(user.getAsTag(), "You restarted!"));
            }
            else reply(event, getReplyEmbed(user.getAsTag()));
        }
    }

    private static MessageEmbed getReplyEmbed(String userTag, String msg)
    {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(userTag);
        embed.setDescription(msg);
        embed.setColor(Global.getRandomColor());

        return embed.build();
    }

    private static MessageEmbed getCooldownEmbed(String userTag, String timeLeft)
    {
        return getReplyEmbed(userTag, "You can use this command again in\n" + timeLeft + "");
    }

    private static MessageEmbed getReplyEmbed(String userTag)
    {
        return getReplyEmbed(userTag, "Invalid Command");
    }

    private static void reply(MessageReceivedEvent e, MessageEmbed embed)
    {
        e.getChannel().sendMessage(embed).queue();
    }

    private static String getUserTagFromMention(String mention)
    {
        return Mongo.BenBuxDB.find(Filters.eq("userID", getUserIDFromMention(mention))).first() != null ? Mongo.BenBuxDB.find(Filters.eq("userID", getUserIDFromMention(mention))).first().getString("username") : "";
    }

    private static String getUserIDFromMention(String mention)
    {
        return mention.substring(mention.indexOf("!") + 1, mention.lastIndexOf(">"));
    }
}
