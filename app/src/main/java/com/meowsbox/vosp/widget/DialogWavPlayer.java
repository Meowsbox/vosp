/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.providers.RecordingsFileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogWavPlayer {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    static Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);
    public final String TAG = this.getClass().getName();
    private volatile boolean isSeekBarTouched = false;
    private AlertDialog dialog;
    private String fName;
    private ImageButton btnPlay;
    private ImageButton btnShare;
    private SeekBar seekBar;
    private TextView tvMediaTime;
    private Timer seekBarTimer;
    private MediaPlayer mediaPlayer;
    private boolean isMediaPlayerReady = false;
    private SimpleDateFormat sdfTimecode;
    private Date dTimecode;
    private String durationString = null;
    private StringBuilder sbTimeCode = new StringBuilder();

    public DialogWavPlayer(String wavFilePath) {
        fName = wavFilePath;
    }

    public DialogWavPlayer build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_wav_player, null);

        final String errorString = sipService.getLocalString("error", "ERROR");
        final String shareString = sipService.getLocalString("share_elip", "Share...");

        tvMediaTime = view.findViewById(R.id.tv_media_time);
        btnPlay = view.findViewById(R.id.btn_media_play);
        btnShare = view.findViewById(R.id.btn_media_share);
        seekBar = view.findViewById(R.id.media_seekbar);

        final String filePath = sipService.getAppExtStoragePath() + '/' + fName;

        final File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) return null; // sanity

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    updatePlayButtonState(btnPlay, false);
                }
            });
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();

            final int duration = mediaPlayer.getDuration();
            seekBar.setMax(duration > 0 ? duration - 1 : 0);
            tvMediaTime.setText(getTimeCode(mediaPlayer));
            pokeSeekBarTimer(true, mediaPlayer);

            btnPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "play onClick");
                    if (!isMediaPlayerReady) return;
                    if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                    else mediaPlayer.start();
                    updatePlayButtonState(btnPlay, mediaPlayer.isPlaying());
                }
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvMediaTime.setText(getTimeCode(progress, duration));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isSeekBarTouched = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (isMediaPlayerReady) {
                        mediaPlayer.seekTo(seekBar.getProgress());
                        tvMediaTime.setText(getTimeCode(mediaPlayer));
                    }
                    isSeekBarTouched = false;
                }
            });
            isMediaPlayerReady = true;
        } catch (IOException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        final AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
        builder.setView(view);
        dialog = builder.create();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                destroy();
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                destroy();
            }
        });

        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("audio/wav");
                i.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://" + RecordingsFileProvider.AUTHORITY + "/" + fName));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // grant the external mail app permission to read attachments
                dismiss();
                context.startActivity(Intent.createChooser(i, shareString));
            }
        });

        if (isMediaPlayerReady) {
            mediaPlayer.start();
            updatePlayButtonState(btnPlay, mediaPlayer.isPlaying());
        } else {
            tvMediaTime.setText(errorString);
        }
        return this;
    }

    public DialogWavPlayer buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        if (dialog != null) dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) {
            destroy();
            dialog.dismiss();
        }
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

    private void destroy() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onDismiss");
        if (mediaPlayer != null) {
            isMediaPlayerReady = false;
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        pokeSeekBarTimer(false, null);
    }

    private String getTimeCode(MediaPlayer mp) {
        final int currentPosition = mp.getCurrentPosition();
        final int duration = mp.getDuration();
        if (sdfTimecode == null) sdfTimecode = new SimpleDateFormat("mm:ss");
        if (dTimecode == null) dTimecode = new Date();

        dTimecode.setTime(duration);
        durationString = sdfTimecode.format(dTimecode);

        dTimecode.setTime(currentPosition);

        sbTimeCode.setLength(0);
        sbTimeCode.append(sdfTimecode.format(dTimecode));
        sbTimeCode.append('/');
        sbTimeCode.append(durationString);
        return sbTimeCode.toString();
    }

    private String getTimeCode(int currentPosition, int duration) {
        if (sdfTimecode == null) sdfTimecode = new SimpleDateFormat("mm:ss");
        if (dTimecode == null) dTimecode = new Date();

        dTimecode.setTime(duration);
        durationString = sdfTimecode.format(dTimecode);

        dTimecode.setTime(currentPosition);

        sbTimeCode.setLength(0);
        sbTimeCode.append(sdfTimecode.format(dTimecode));
        sbTimeCode.append('/');
        sbTimeCode.append(durationString);
        return sbTimeCode.toString();
    }

    private void pokeSeekBarTimer(boolean enable, final MediaPlayer mp) {
        if (enable) {
            if (seekBarTimer == null) {
                seekBarTimer = new Timer("seekBar Timer");
                seekBarTimer.scheduleAtFixedRate(new TimerTask() {
                    private Handler handler = null;

                    @Override
                    public void run() {
                        if (mp == null || seekBar == null) {
                            seekBarTimer.cancel();
                            seekBarTimer.purge();
                            seekBarTimer = null;
                            return;
                        }
                        if (handler == null) handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isMediaPlayerReady) return;
                                if (!isSeekBarTouched) {
                                    seekBar.setProgress(mp.getCurrentPosition());
                                    tvMediaTime.setText(getTimeCode(mediaPlayer));
                                }
                            }
                        });

                    }
                }, 0, 1000);
            }
        } else {
            if (seekBarTimer == null) return;
            seekBarTimer.cancel();
            seekBarTimer.purge();
            seekBarTimer = null;
        }
    }

    private void updatePlayButtonState(ImageButton imageButton, boolean isPlaying) {
        if (isPlaying) imageButton.setBackgroundResource(R.drawable.ic_pause_black_24dp);
        else imageButton.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
    }


}
