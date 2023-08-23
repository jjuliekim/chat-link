package me.julie.chatlink.server;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import me.julie.chatlink.server.data.ContactInfo;
import me.julie.chatlink.server.data.JsonManager;
import me.julie.chatlink.server.data.UserInfo;

import java.io.IOException;
import java.util.*;

public class ServerManager {
    private final JsonManager jsonManager = new JsonManager();

    public void startServer() throws IOException {
        var app = Javalin.create().start(20202);
        // load the files
        jsonManager.load();
        // save the files whenever you edit the info
        Map<WsContext, String> connections = new HashMap<>();
        app.ws("/chat", ws -> {
            ws.onMessage(ctx -> { // onMessage = u sending message to server
                System.out.println("received: " + ctx.message());
                // start prompt, check if username exists
                if (ctx.message().startsWith("username@")) {
                    String[] info = ctx.message().split("@");
                    if (!jsonManager.getLoginInfo().getLogins().containsKey(info[1])) {
                        // sign up
                        System.out.println("signing up");
                        ctx.send("Display Name -> ");
                    } else { // log in
                        System.out.println("logging in");
                        ctx.send("Password -> ");
                    }
                }

                // put new user info into json
                if (ctx.message().startsWith("signup@")) {
                    String[] info = ctx.message().split("@");
                    jsonManager.getLoginInfo().getLogins().put(info[1],
                            new UserInfo(info[1], info[2], info[3], new ArrayList<>()));
                    jsonManager.save();
                    connections.put(ctx, info[1]);
                    ctx.send("connected@Welcome " + info[3] + "!");
                }

                // check if username and password match
                if (ctx.message().startsWith("login@")) {
                    String[] info = ctx.message().split("@");
                    UserInfo user = jsonManager.getLoginInfo().getLogins().get(info[1]);
                    if (user.getPassword().equals(info[2])) {
                        connections.put(ctx, info[1]);
                        String name = jsonManager.getLoginInfo().getLogins().get(info[1]).getDisplayName();
                        ctx.send("connected@Logged in as " + name + "!");
                    } else {
                        ctx.send("Incorrect password.");
                        ctx.send("Password -> ");
                    }
                }

                // send the display name given the username
                if (ctx.message().startsWith("getDisplayName@")) {
                    String[] info = ctx.message().split("@");
                    ctx.send("displayName@" + jsonManager.getLoginInfo().getLogins().get(info[1]).getDisplayName());
                }

                // change the value of <username, displayname> given the key
                if (ctx.message().startsWith("changeDisplayName")) {
                    String[] info = ctx.message().split("@");
                    jsonManager.getLoginInfo().getLogins().get(info[1]).setDisplayName(info[2]);
                    jsonManager.save();
                    ctx.send("greenMessage@[UPDATED]");
                    ctx.send("displaySettings");
                }

                // sends contact display names
                if (ctx.message().equals("allContactNames")) {
                    int index = 1;
                    if (!jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts().isEmpty()) {
                        for (ContactInfo contact : jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts()) {
                            ctx.send("contactName@" + index + "@" + contact.getDisplayName());
                            index++;
                        }
                    }
                    ctx.send("displayContacts@" + index);
                }

                // create new contact in json
                if (ctx.message().startsWith("newContact@")) {
                    String[] info = ctx.message().split("@");
                    if (!jsonManager.getLoginInfo().getLogins().containsKey(info[1])) {
                        ctx.send("notValidUser");
                    } else {
                        jsonManager.getLoginInfo().getLogins()
                                .get(connections.get(ctx)).getContacts().add(new ContactInfo(info[1],
                                        jsonManager.getLoginInfo().getLogins().get(info[1]).getDisplayName()));
                        jsonManager.save();
                        ctx.send("greenMessage@[CONTACT ADDED]");
                    }
                    System.out.println("contact update");
                    ctx.send("displayContactsMenu");
                }

                // get the contact info
                if (ctx.message().startsWith("getContactInfo")) {
                    String[] info = ctx.message().split(" ");
                    ContactInfo contact = jsonManager.getLoginInfo().getLogins()
                            .get(connections.get(ctx)).getContacts().get(Integer.parseInt(info[1]) - 1);
                    ctx.send("contactInfo@" + contact.getUsername() + "@" + contact.getDisplayName());
                }

                // change contact's display name
                if (ctx.message().startsWith("changeContactName@")) {
                    String[] info = ctx.message().split("@");
                    int index = jsonManager.getLoginInfo().getLogins()
                            .get(connections.get(ctx)).getContactUsernames().indexOf(info[1]);
                    jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts()
                            .get(index).setDisplayName(info[2]);
                    jsonManager.save();
                    ctx.send("greenMessage@[UPDATED]");
                    ctx.send("displayContactsMenu");
                }

                // remove contact
                if (ctx.message().startsWith("removeContact@")) {
                    String[] info = ctx.message().split("@");
                    int index = jsonManager.getLoginInfo().getLogins()
                            .get(connections.get(ctx)).getContactUsernames().indexOf(info[1]);
                    jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts()
                            .remove(index);
                    jsonManager.save();
                    ctx.send("greenMessage@[CONTACT REMOVED]");
                    ctx.send("displayContactsMenu");
                }

                // load chat
                if (ctx.message().startsWith("openConversation@")) {
                    String[] info = ctx.message().split("@");
                    String username = info[1];
                    String otherUsername = info[2];
                    int index;
                    String otherDisplayName;
                    try {
                        index = Integer.parseInt(info[2]);
                        otherUsername = jsonManager.getLoginInfo().getLogins()
                                .get(connections.get(ctx)).getContactUsernames().get(index - 1);
                        otherDisplayName = jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts()
                                .get(index - 1).getDisplayName();
                    } catch (NumberFormatException e) {
                        index = jsonManager.getLoginInfo().getLogins()
                                .get(connections.get(ctx)).getContactUsernames().indexOf(otherUsername);
                        otherDisplayName = jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getContacts()
                                .get(index).getDisplayName();
                    }
                    String userDisplayName = jsonManager.getLoginInfo().getLogins().get(connections.get(ctx)).getDisplayName();
                    Set<List<String>> chatNames = jsonManager.getChatInfo().getChatLogs().keySet();
                    List<String> chatLog = new ArrayList<>();
                    if (chatNames.contains(Arrays.asList(username, otherUsername))) {
                        chatLog = jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(username, otherUsername));
                        jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(username, otherUsername)).set(0, "[OPENING CHAT WITH " + otherDisplayName + "]");
                    } else if (chatNames.contains(Arrays.asList(otherUsername,username))) {
                        chatLog = jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(otherUsername, username));
                        jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(username, otherUsername)).set(0, "[OPENING CHAT WITH " + otherDisplayName + "]");
                    } else {
                        ArrayList<String> newChat = new ArrayList<>();
                        newChat.add("[CREATING NEW CHAT WITH " + otherDisplayName + "]");
                        newChat.add("Type '*exit*' to exit.");
                        jsonManager.getChatInfo().getChatLogs().put(Arrays.asList(username, otherUsername), newChat);
                    }
                    for (String message : chatLog) {
                        if (message.startsWith(username + "(*)")) {
                            ctx.send("yourMessage(*)" + userDisplayName + "(*)" + message.substring(username.length() + 3));
                        } else if (message.startsWith(otherUsername + "(*)")) {
                            ctx.send("theirMessage(*)" + otherDisplayName + "(*)" + message.substring(otherUsername.length() + 3));
                        } else {
                            ctx.send("systemMessage(*)" + message);
                        }
                    }
                    jsonManager.save();
                }

                // displayName ->
                if (ctx.message().startsWith("responseConversation@")) {
                    String[] info = ctx.message().split("@");
                    ctx.send("arrowConversation(*)" +
                            jsonManager.getLoginInfo().getLogins().get(info[1]).getDisplayName() + "(*) -> " + "(*)" + info[2]);
                }

                // update chat
                if (ctx.message().startsWith("sendingMsg(*)")) {
                    String[] info = ctx.message().split("\\(\\*\\)");
                    String username = info[1];
                    String otherUsername = info[2];
                    String message = info[3];
                    if (jsonManager.getChatInfo().getChatLogs().containsKey(Arrays.asList(username, otherUsername))) {
                        jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(username, otherUsername)).add(username + "(*)" + message);
                    } else {
                        jsonManager.getChatInfo().getChatLogs().get(Arrays.asList(otherUsername, username)).add(username + "(*)" + message);
                    }
                    jsonManager.save();
                    ctx.send("arrowConversation(*)" + jsonManager.getLoginInfo().getLogins().get(username).getDisplayName()
                            + "(*) -> " + message + "(*)" + otherUsername);
                }

                // get all chats for the chat menu screen to send at once
                if (ctx.message().startsWith("allConversationPreview@")) {
                    String[] info = ctx.message().split("@");
                    Set<List<String>> chatNames = jsonManager.getChatInfo().getChatLogs().keySet();
                    ArrayList<String> usernames = new ArrayList<>();
                    ArrayList<String> previews = new ArrayList<>();
                    for (List<String> names : chatNames) {
                        if (names.contains(info[1])) {
                            usernames.add(names.get(0).equals(info[1]) ? names.get(1) : names.get(0));
                            int size = jsonManager.getChatInfo().getChatLogs().get(names).size();
                            if (size == 2) {
                                previews.add("[NO MESSAGES YET]");
                            } else {
                                String lastMessage = jsonManager.getChatInfo().getChatLogs().get(names).get(size - 1);
                                lastMessage = lastMessage.substring(lastMessage.indexOf("(*)") + 3);
                                if (lastMessage.length() > 30) {
                                    lastMessage = lastMessage.substring(0, 30) + "...";
                                }
                                previews.add("[" + lastMessage + "]");
                            }
                        }
                    }
                    ctx.send("chatPreviewList(*)" + usernames + "(*)" + previews);
                }

            });
            ws.onClose(connections::remove);
        });
    }
}
