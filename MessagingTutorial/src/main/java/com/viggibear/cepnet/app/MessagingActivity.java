package com.viggibear.cepnet.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.github.johnpersano.supertoasts.SuperActivityToast;
import com.github.johnpersano.supertoasts.SuperToast;
import com.parse.FindCallback;
import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.messaging.Message;
import com.sinch.android.rtc.messaging.MessageClient;
import com.sinch.android.rtc.messaging.MessageClientListener;
import com.sinch.android.rtc.messaging.MessageDeliveryInfo;
import com.sinch.android.rtc.messaging.MessageFailureInfo;
import com.sinch.android.rtc.messaging.WritableMessage;

import java.util.Arrays;
import java.util.List;

public class MessagingActivity extends ActionBarActivity {

    private String recipientId;
    private EditText messageBodyField;
    private String messageBody;
    private MessageService.MessageServiceInterface messageService;
    private MessageAdapter messageAdapter;
    private ListView messagesList;
    private String currentUserId;
    private ServiceConnection serviceConnection = new MyServiceConnection();
    private MessageClientListener messageClientListener = new MyMessageClientListener();

    Toolbar mToolbar;
    String recipientName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.messaging);

        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);

        bindService(new Intent(this, MessageService.class), serviceConnection, BIND_AUTO_CREATE);

        Intent intent = getIntent();
        recipientName = intent.getStringExtra("RECIPIENT_NAME");
        recipientId = intent.getStringExtra("RECIPIENT_ID");
        setTitle(recipientName);
        currentUserId = ParseUser.getCurrentUser().getObjectId();

        messagesList = (ListView) findViewById(R.id.listMessages);
        messageAdapter = new MessageAdapter(this);
        messagesList.setAdapter(messageAdapter);
        populateMessageHistory();

        messageBodyField = (EditText) findViewById(R.id.messageBodyField);

        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
    }

    //get previous messages from parse & display
    private void populateMessageHistory() {
        String[] userIds = {currentUserId, recipientId};
        ParseQuery<ParseObject> query = ParseQuery.getQuery("ParseMessage");
        query.whereContainedIn("senderId", Arrays.asList(userIds));
        query.whereContainedIn("recipientId", Arrays.asList(userIds));
        query.orderByAscending("createdAt");
        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> messageList, com.parse.ParseException e) {
                if (e == null) {
                    for (int i = 0; i < messageList.size(); i++) {
                        WritableMessage message = new WritableMessage(messageList.get(i).get("recipientId").toString(), messageList.get(i).get("messageText").toString());
                        if (messageList.get(i).get("senderId").toString().equals(currentUserId)) {
                            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_OUTGOING);
                        } else {
                            messageAdapter.addMessage(message, MessageAdapter.DIRECTION_INCOMING);
                        }
                    }
                }
            }
        });
    }

    private void sendMessage() {
        messageBody = messageBodyField.getText().toString();
        if (messageBody.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_LONG).show();
            return;
        }

        messageService.sendMessage(recipientId, messageBody);
        messageBodyField.setText("");
    }

    @Override
    public void onDestroy() {
        messageService.removeMessageClientListener(messageClientListener);
        unbindService(serviceConnection);
        super.onDestroy();
    }

    private class MyServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            messageService = (MessageService.MessageServiceInterface) iBinder;
            messageService.addMessageClientListener(messageClientListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messageService = null;
        }
    }

    private class MyMessageClientListener implements MessageClientListener {
        @Override
        public void onMessageFailed(MessageClient client, Message message,
                                    MessageFailureInfo failureInfo) {
            Toast.makeText(MessagingActivity.this, "Message failed to send.", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onIncomingMessage(MessageClient client, Message message) {
            if (message.getSenderId().equals(recipientId)) {
                WritableMessage writableMessage = new WritableMessage(message.getRecipientIds().get(0), message.getTextBody());
                messageAdapter.addMessage(writableMessage, MessageAdapter.DIRECTION_INCOMING);
            }
        }

        @Override
        public void onMessageSent(MessageClient client, Message message, String recipientId) {

            final WritableMessage writableMessage = new WritableMessage(message.getRecipientIds().get(0), message.getTextBody());
            messageAdapter.addMessage(writableMessage, MessageAdapter.DIRECTION_OUTGOING);
            //only add message to parse database if it doesn't already exist there
            ParseQuery<ParseObject> query = ParseQuery.getQuery("ParseMessage");
            query.whereEqualTo("sinchId", message.getMessageId());
            query.findInBackground(new FindCallback<ParseObject>() {
                @Override
                public void done(List<ParseObject> messageList, com.parse.ParseException e) {
                    if (e == null) {
                        if (messageList.size() == 0) {
                            ParseObject parseMessage = new ParseObject("ParseMessage");
                            parseMessage.put("senderId", currentUserId);
                            parseMessage.put("recipientId", writableMessage.getRecipientIds().get(0));
                            parseMessage.put("messageText", writableMessage.getTextBody());
                            parseMessage.put("sinchId", writableMessage.getMessageId());
                            parseMessage.saveInBackground();
                        }
                    }
                }
            });
        }

        @Override
        public void onMessageDelivered(MessageClient client, MessageDeliveryInfo deliveryInfo) {}

        @Override
        public void onShouldSendPushData(MessageClient client, Message message, List<PushPair> pushPairs) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_messagingactivity, menu);
        final MenuItem editFriend = menu.findItem(R.id.edit_friend);
        ParseQuery<ParseObject> query = ParseQuery.getQuery("FriendRequest");
        query.whereEqualTo("from_user", currentUserId);
        query.whereEqualTo("to_user", recipientName);
        query.getFirstInBackground(new GetCallback<ParseObject>() {
            @Override
            public void done(final ParseObject parseObject, ParseException e) {
                if (e == null) {
                    if (parseObject.getBoolean("friends")) {
                        editFriend.setTitle(getString(R.string.remove_friend));
                        editFriend.setIcon(R.drawable.ic_remove_user);
                    } else if (!parseObject.getBoolean("friends")) {
                        editFriend.setTitle(getString(R.string.add_friend));
                        editFriend.setIcon(R.drawable.ic_person_add_white_18dp);
                    }
                } else {
                    if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                        editFriend.setTitle(getString(R.string.add_friend));
                        editFriend.setIcon(R.drawable.ic_person_add_white_18dp);

                    } else {
                        //unknown error, debug
                    }
                }
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        if (id==R.id.edit_friend) {
            ParseQuery<ParseObject> query = ParseQuery.getQuery("FriendRequest");
            query.whereEqualTo("from_user", currentUserId);
            query.whereEqualTo("to_user", recipientName);
            query.getFirstInBackground(new GetCallback<ParseObject>() {
                @Override
                public void done(final ParseObject parseObject, ParseException e) {
                    if (e == null) {
                        if (parseObject.getBoolean("friends")) {
                            new MaterialDialog.Builder(MessagingActivity.this)
                                    .title(R.string.confirm_removal)
                                    .content("Remove '"+recipientName+"' from your friends list")
                                    .positiveText(R.string.unfriend)
                                    .negativeText(R.string.cancel)
                                    .callback(new MaterialDialog.ButtonCallback() {
                                        @Override
                                        public void onPositive(MaterialDialog dialog) {
                                            parseObject.put("friends", false);
                                            parseObject.saveInBackground();
                                            dialog.dismiss();
                                            doneToast("Friend Removed");
                                            SystemClock.sleep(2000);
                                            VersionHelper.refreshActionBarMenu(MessagingActivity.this);
                                        }

                                        @Override
                                        public void onNegative(MaterialDialog dialog) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .show();
                        } else if (!parseObject.getBoolean("friends")) {
                            parseObject.put("friends", true);
                            parseObject.saveInBackground();
                            doneToast("Friend Added");
                            SystemClock.sleep(2000);
                            VersionHelper.refreshActionBarMenu(MessagingActivity.this);
                        }
                    } else {
                        if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                            ParseObject friendRequest = new ParseObject("FriendRequest");
                            friendRequest.put("from_user", currentUserId);
                            friendRequest.put("to_user", recipientName);
                            friendRequest.put("friends", true);
                            friendRequest.saveInBackground();
                            doneToast("Friend Added");
                            SystemClock.sleep(2000);
                            VersionHelper.refreshActionBarMenu(MessagingActivity.this);

                        } else {
                            //unknown error, debug
                        }
                    }
                }
            });
        }

        if (id == R.id.home){
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void doneToast(String doneText) {
        SuperActivityToast superToast = new SuperActivityToast(MessagingActivity.this);
        superToast.setDuration(SuperToast.Duration.SHORT);
        superToast.setText(doneText);
        superToast.setIcon(R.drawable.ic_done_white_18dp, SuperToast.IconPosition.LEFT);
        superToast.show();
    }
}




