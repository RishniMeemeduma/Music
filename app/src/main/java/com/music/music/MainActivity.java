package com.music.music;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.SeekBar;

import java.util.ArrayList;



public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    public static final String Broadcast_PLAY_NEW_AUDIO="com.music.music.PlayNewAudio";

    private MediaPlayerService player;
    SeekBar seekBar;
    MediaPlayer mediaPlayer;
    private Handler handler=new Handler();
    Runnable runnable;
    boolean serviceBound = false;
    ArrayList<Audio> audioList = new ArrayList<>();
    //service
    private MediaPlayerService musicSrv;
    private Intent playIntent;
    //bindin
    private boolean musicBound=false;
    //ui
    ImageView collapsingImageView;
    int imageIndex=0;

    //activiy and playback pause flags
    private boolean paused=false,playbackPaused=false;

    //controller
    private MusicController controller;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        Toolbar toolbar=(Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        collapsingImageView=(ImageView)findViewById(R.id.collapsingImageView);

        loadCollapsingImage(imageIndex);
        loadAudio();
        initRecyclerView();

        FloatingActionButton fab= (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(imageIndex == 4){
                    imageIndex =0;
                    loadCollapsingImage(imageIndex);
                }else {
                    loadCollapsingImage(++imageIndex);
                }
            }
        });
    }
    private void initRecyclerView(){
        if(audioList.size() >0){
            RecyclerView recyclerView =(RecyclerView) findViewById(R.id.recyclerview);
            RecyclerView_Adapter adapter=new RecyclerView_Adapter(audioList,this);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            recyclerView.addOnItemTouchListener(new CustomTouchListener(this, new OnItemClickListener() {
                @Override
                public void onClick(View view, int index) {
                    playAudio(index);
                }
            }));

        }
    }
private void loadCollapsingImage(int i){
    TypedArray array=getResources().obtainTypedArray(R.array.images);
    collapsingImageView.setImageDrawable(array.getDrawable(i));
}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_shuffle:
                //shuffle
                musicSrv.setShuffle();
                break;
            case R.id.action_end:
                stopService(playIntent);
                musicSrv=null;
                System.exit(0);
                break;
            case R.id.action_settings:
                return true;
                }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus",serviceBound);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder)
                    service;
            player = binder.getService();
            serviceBound = true;

           // Toast.makeText(MainActivity.this, "Service Bound", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;

        }
    };

    private void playAudio(int audioIndex){
        if(!serviceBound){
            StorageUtil storage= new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);
            Intent playerIntent = new Intent(this,MediaPlayerService.class);
             // playerIntent.putExtra("media",media);
            startService(playerIntent);
            
            bindService(playerIntent,serviceConnection , Context.BIND_AUTO_CREATE);
        }else{
            StorageUtil storage=new StorageUtil(getApplicationContext());
          //  MediaPlayerService.StorageUtil storage=new MediaPlayerService.StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);
           // playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");
            Intent broadcastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }
    }
    private void loadAudio(){
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection= MediaStore.Audio.Media.IS_MUSIC+"!=0";
        String sortOrder = MediaStore.Audio.Media.TITLE+" ASC";
        Cursor cursor = contentResolver.query(uri,null,selection,null,sortOrder);
        if (cursor !=null && cursor.getCount()>0){
            audioList = new ArrayList<>();
            while (cursor.moveToNext()){
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album= cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                audioList.add(new Audio(data,title,album,artist));
            }
        }
        cursor.close();
    }

    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv=null;
        super.onDestroy();
       if(serviceBound){
            unbindService(serviceConnection);
            player.stopSelf();
    }
}


    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getPosn();
        else return 0;
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getDur();
        else return 0;
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound)
            return musicSrv.isPng();
        return false;
    }

    @Override
    public void pause() {
        playbackPaused=true;
        musicSrv.pausePlayer();
    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public void start() {
        musicSrv.go();
    }

    //set the controller up
    private void setController(){
        controller = new MusicController(this);
        //set previous and next button listeners
        controller.setPrevNextListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrev();
            }
        });
        //set and show
        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.toolbar_layout));
        controller.setEnabled(true);
    }

    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    @Override
    protected void onPause(){
        super.onPause();
        paused=true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(paused){
            setController();
            paused=false;
        }
    }

    @Override
    protected void onStop() {
        controller.hide();
        super.onStop();
    }





}


