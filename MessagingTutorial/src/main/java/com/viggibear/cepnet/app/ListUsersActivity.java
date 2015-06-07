package com.viggibear.cepnet.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.rey.material.widget.SnackBar;

import java.util.ArrayList;
import java.util.List;

public class ListUsersActivity extends ActionBarActivity {

    private String currentUserId;
    private ArrayAdapter<String> namesArrayAdapter;
    private ArrayList<String> names;
    private ListView usersListView;
    private Button logoutButton;
    private MaterialDialog progressDialog;
    private BroadcastReceiver receiver = null;

    Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_users);

        showSpinner();

        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        setTitle("List of Users");

        logoutButton = (Button) findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopService(new Intent(getApplicationContext(), MessageService.class));
                ParseUser.logOut();
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    boolean doubleBackToExitPressedOnce = false;
    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            stopService(new Intent(getApplicationContext(), MessageService.class));
            ParseUser.logOut();
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 2000);
    }

    //display clickable a list of all users
    private void setConversationsList() {
        currentUserId = ParseUser.getCurrentUser().getObjectId();
        names = new ArrayList<>();

        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereNotEqualTo("objectId", currentUserId);
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> userList, com.parse.ParseException e) {
                if (e == null) {
                    for (int i = 0; i < userList.size(); i++) {
                        names.add(userList.get(i).getUsername().toString());
                    }

                    usersListView = (ListView) findViewById(R.id.usersListView);
                    namesArrayAdapter =
                            new ArrayAdapter<>(getApplicationContext(),
                                    R.layout.user_list_item, names);
                    usersListView.setAdapter(namesArrayAdapter);

                    usersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> a, View v, final int i, long l) {

                            MaterialDialog.Builder dialog = new MaterialDialog.Builder(ListUsersActivity.this)
                                    .callback(new MaterialDialog.ButtonCallback() {
                                        @Override
                                        public void onPositive(MaterialDialog dialog) {
                                            openConversation(names, i);
                                            dialog.dismiss();
                                        }

                                        @Override
                                        public void onNegative(MaterialDialog dialog) {
                                            editFriendRequest(names, i);
                                            dialog.dismiss();
                                        }
                                    })
                                    .title(getString(R.string.dialog_title))
                                    .content(names.get(i))
                                    .positiveText(R.string.chat);

                            openDialog(dialog, names.get(i));

                        }
                    });

                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error loading user list",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    //open a conversation with one person
    public void openConversation(final ArrayList<String> names, final int pos) {
        ParseQuery<ParseUser> query = ParseUser.getQuery();
        query.whereEqualTo("username", names.get(pos));
        query.findInBackground(new FindCallback<ParseUser>() {
            public void done(List<ParseUser> user, com.parse.ParseException e) {
                if (e == null) {
                    Intent intent = new Intent(getApplicationContext(), MessagingActivity.class);
                    intent.putExtra("RECIPIENT_NAME", names.get(pos));
                    intent.putExtra("RECIPIENT_ID", user.get(0).getObjectId());
                    startActivity(intent);
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Error finding that user",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void editFriendRequest(final ArrayList<String> names, final int pos) {
        final SnackBar mSnackBar = SnackBar.make(ListUsersActivity.this);
        mSnackBar.singleLine(true)
                .actionText("CLOSE")
                .actionTextColor(R.color.primary)
                .textColor(R.color.off_white)
                .textSize(R.integer.snackbar_text_size)
                .actionClickListener(new SnackBar.OnActionClickListener() {
                    @Override
                    public void onActionClick(SnackBar snackBar, int i) {
                        mSnackBar.dismiss();
                    }
                })
                .duration(2000);
        ParseQuery<ParseObject> query = ParseQuery.getQuery("FriendRequest");
        query.whereEqualTo("from_user", currentUserId);
        query.whereEqualTo("to_user", names.get(pos));
        query.getFirstInBackground(new GetCallback<ParseObject>() {
            @Override
            public void done(final ParseObject parseObject, ParseException e) {
                if (e == null) {
                    if (parseObject.getBoolean("friends")) {
                        new MaterialDialog.Builder(ListUsersActivity.this)
                                .title(R.string.confirm_removal)
                                .content("Remove '" + names.get(pos) + "' from your friends list")
                                .positiveText(R.string.unfriend)
                                .negativeText(R.string.cancel)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        parseObject.put("friends", false);
                                        doneToast("Friend Removed");
                                        parseObject.saveInBackground();
                                        dialog.dismiss();
                                    }

                                    @Override
                                    public void onNegative(MaterialDialog dialog) {
                                        dialog.dismiss();
                                    }
                                })
                                .show();
                    } else if (!parseObject.getBoolean("friends")) {
                        parseObject.put("friends", true);
                        doneToast("Friend Added");
                        parseObject.saveInBackground();
                    }
                } else {
                    if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                        ParseObject friendRequest = new ParseObject("FriendRequest");
                        friendRequest.put("from_user", currentUserId);
                        friendRequest.put("to_user", names.get(pos));
                        friendRequest.put("friends", true);
                        doneToast("Friend Added");
                        friendRequest.saveInBackground();

                    } else {
                        //unknown error, debug
                    }
                }
            }
        });
    }

    public void openDialog(final MaterialDialog.Builder builder, String user){
        ParseQuery<ParseObject> query = ParseQuery.getQuery("FriendRequest");
        query.whereEqualTo("from_user", currentUserId);
        query.whereEqualTo("to_user", user);
        query.getFirstInBackground(new GetCallback<ParseObject>() {
            @Override
            public void done(ParseObject parseObject, ParseException e) {
                if (e == null) {
                    if (parseObject.getBoolean("friends")) {
                        builder.negativeText(R.string.remove_friend);
                    }
                    else if (!parseObject.getBoolean("friends")) {
                        builder.negativeText(R.string.add_friend);
                    }
                } else {
                    if (e.getCode() == ParseException.OBJECT_NOT_FOUND) {
                        builder.negativeText(R.string.add_friend);
                    } else {

                    }
                }
                builder.show();
            }
        });
    }

    //show a loading spinner while the sinch client starts
    private void showSpinner() {
        progressDialog = new MaterialDialog.Builder(this)
                .title(R.string.progress_dialog)
                .content(R.string.progress_dialog)
                .progress(true, 0)
                .show();
        //progressDialog.show();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Boolean success = intent.getBooleanExtra("success", false);
                progressDialog.dismiss();
                if (!success) {
                    final SnackBar mSnackBar = SnackBar.make(ListUsersActivity.this);
                    mSnackBar.text("Connection Failed!")
                            .actionText("CLOSE")
                            .actionTextColor(R.color.primary)
                            .textColor(R.color.off_white)
                            .textSize(R.integer.snackbar_text_size)
                            .actionClickListener(new SnackBar.OnActionClickListener() {
                                @Override
                                public void onActionClick(SnackBar snackBar, int i) {
                                    mSnackBar.dismiss();
                                }
                            })
                            .duration(2000)
                            .show(ListUsersActivity.this);

                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("ListUsersActivity"));
    }

    @Override
    public void onResume() {
        setConversationsList();
        super.onResume();
    }

    public void doneToast(String doneText) {
        SuperActivityToast superToast = new SuperActivityToast(ListUsersActivity.this);
        superToast.setDuration(SuperToast.Duration.SHORT);
        superToast.setText(doneText);
        superToast.setIcon(R.drawable.ic_done_white_18dp, SuperToast.IconPosition.LEFT);
        superToast.show();
    }
}


