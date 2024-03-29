package chat.safe.safechat;

import android.content.Context;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.java_websocket.drafts.Draft_6455;

public class Chat extends AppCompatActivity {
    private WebSocketClient cc;
    private String curUser;
    private String curConvo;
    private String destUser;
    private String curUserPublicKey;
    private String destUserPublicKey;
    private String curUserPrivateKey;
    ArrayList<String> conversation;
    ConverstationHistoryListAdapter adapter;
    Button bn_send;
    EditText msg_to_send;
    ListView lvData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        destUser = getIntent().getStringExtra("USERTOCHAT");
        curUser = SaveSharedPreference.getUsername(getApplicationContext());
        curUserPublicKey = SaveSharedPreference.getPublicKey(getApplicationContext());
        destUserPublicKey = getIntent().getStringExtra("DESTPUBLICKEY");
        curUserPrivateKey = SaveSharedPreference.getPrivateKey(getApplicationContext());
        curConvo = curUser + "$" + destUser;
        conversation = new ArrayList<>();
        connectWebSocket();

        lvData = (ListView) findViewById(R.id.lv_conversation);
        adapter = new ConverstationHistoryListAdapter(getApplicationContext(), conversation);
        lvData.setAdapter(adapter);

        bn_send = (Button)findViewById(R.id.bn_conv_send);
        msg_to_send = (EditText)findViewById(R.id.et_conv_msg);
        bn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager inputManager = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);

                inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
                String msg = msg_to_send.getText().toString().trim();

                msg_to_send.setText("");

                if(msg != null && !msg.equals("")){
                    String symmetricKey = SymmetricCipher.generateRandomKey();

                    String encWithCurUserPublicKey = URLEncoder.generateEncryptedMsg(msg, symmetricKey, curUserPublicKey);
                    String encWithDestUserPublicKey = URLEncoder.generateEncryptedMsg(msg, symmetricKey, destUserPublicKey);
                    //msg = msg + "(encrypted with " + curUser + "'s public key)$" + msg + "(encrypted with " + destUser + "'s public key)";
                    msg = encWithCurUserPublicKey + "$" + encWithDestUserPublicKey;
                    cc.send("@" + destUser + " " + msg);
                }
            }
        });



        //conversation.add("PJ: hi");

        //while(!cc.isOpen()){
            
       // }
        //cc.send("Hello There!");

        TextView tv = (TextView) findViewById(R.id.tv_chat);
        tv.setText("Chatting with " + destUser);
    }

    public void close(View v){
        cc.close();
        finish();
    }

    private void connectWebSocket() {
        Draft[] drafts = {new Draft_6455()};
        String w = ServerInfo.WSIP + "/websocket/" + curConvo;

        try {
            Log.d("Socket:", "Trying socket");
            cc = new WebSocketClient(new URI(w),(Draft) drafts[0]) {
                @Override
                public void onMessage(final String message) {
                    Log.d("", "run() returned: " + message);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            String takenoff = "";
                            String tm1 = curUser + ": ";
                            String msg = message;
                            if(message.indexOf(tm1) != -1) {
                                msg = message.substring(message.indexOf(tm1) + tm1.length(), message.length());
                                takenoff = tm1;
                            }

                            String tm2 = destUser + ": ";
                            if(message.indexOf(tm2) != -1) {
                                msg = message.substring(message.indexOf(tm2) + tm2.length(), message.length());
                                takenoff = tm2;
                            }

                            String[] parts = msg.split("%");
                            String payloadCipher = parts[0];
                            String encryptedKey = parts[1];
                            String symmetricKey = RSACipher.decryptWithPrivate(curUserPrivateKey, encryptedKey);
                            String plainTextMessage = SymmetricCipher.decrypt(symmetricKey, payloadCipher, true);

                            plainTextMessage = takenoff + plainTextMessage;

                            conversation.add(plainTextMessage);
                            adapter.notifyDataSetChanged();
                            lvData.setSelection(adapter.getCount() - 1);
                        }
                    });

                }

                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d("OPEN", "run() returned: " + "is connecting");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d("CLOSE", "onClose() returned: " + reason);
                }

                @Override
                public void onError(Exception e)
                {
                    Log.d("Exception:", e.toString());
                }
            };
        }
        catch (URISyntaxException e) {
            Log.d("Exception:", e.getMessage().toString());
            e.printStackTrace();
        }
        cc.connect();
    }
}
