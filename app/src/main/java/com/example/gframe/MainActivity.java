package com.example.gframe;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.List;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import java.util.UUID;
import static android.speech.tts.TextToSpeech.SUCCESS;

public class MainActivity extends AppCompatActivity {

    SessionsClient sessionClient;
    SessionName sessionName;

    final static String uniqueid = UUID.randomUUID().toString();
    Button btSTT;
    TextView tvResponse;
    boolean ttsReady = false;
    TextToSpeech tts;

    private ActivityResultLauncher<Intent> sttLauncher;
    private Intent sttIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {

        btSTT = findViewById(R.id.btDialog);
        tvResponse = findViewById(R.id.tvResponse);

        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();
        tts = new TextToSpeech(this, status -> {
            if (status == SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("spa", "ES"));

            }
        });


        if (setupDFClient()) {

            btSTT.setOnClickListener(v -> {
                sttLauncher.launch(sttIntent);
            });
        }
        else{
                btSTT.setEnabled(false);
            }




    }


    private void sendToDialogFlow(){

        String text = tvResponse.getText().toString();
        tvResponse.setText("");
        if(!text.isEmpty()){
            sendMessageToBot(text);
        }
        else{
            Toast.makeText(this, "introduce algo ¿no?", Toast.LENGTH_LONG).show();
        }
    }

    private boolean setupDFClient() {

        boolean value = true;
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.client);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, uniqueid);
        }
        catch (Exception e) {
            showMessage("\nexception in setupBot: " + e.getMessage() + "\n");
            value = false;
        }
        return value;
    }
        private void sendMessageToBot(String message) {
            QueryInput input = QueryInput.newBuilder().setText(
                    TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        DetectIntentRequest detectIntentRequest =
                                DetectIntentRequest.newBuilder()
                                        .setSession(sessionName.toString())
                                        .setQueryInput(input)
                                        .build();
                        DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
                        //intent, action, sentiment
                        String action = detectIntentResponse.getQueryResult().getAction();
                        String intent = detectIntentResponse.getQueryResult().getIntent().toString();
                        String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();
                        if(detectIntentResponse != null) {
                            String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                            if(!botReply.isEmpty()) {
                                showMessage(botReply + "\n");
                            } else {
                                showMessage("something went wrong\n");
                            }
                        } else {
                            showMessage("connection failed\n");
                        }
                    } catch (Exception e) {
                        showMessage("\nexception in thread: " + e.getMessage() + "\n");
                        e.printStackTrace();
                    }
                }
            };
            thread.start();
        }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            tvResponse.setText(message);
            if (ttsReady){
                tts.speak(tvResponse.getText().toString(), TextToSpeech.QUEUE_ADD, null, null);}
        });
    }

    private Intent getSttIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hable ahora, Por favor");
        return intent;
    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String text = "";
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        text = r.get(0);
                    } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                        text = "No te entiendo";
                    }
                    sendMessageToBot(text);
                }
        );
    }



}