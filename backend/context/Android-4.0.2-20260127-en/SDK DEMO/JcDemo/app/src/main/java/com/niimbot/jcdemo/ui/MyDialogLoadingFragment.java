package com.niimbot.jcdemo.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.niimbot.jcdemo.R;

public class MyDialogLoadingFragment extends DialogFragment {


    private String stateStr = "";
    private TextView state ;

    public MyDialogLoadingFragment(String stateStr) {
        this.stateStr = stateStr;
    }

    public void setStateStr(String stateStr) {
        this.stateStr = stateStr;
        state.setText(stateStr);
    }



    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_loading, null);
        state = view.findViewById(R.id.tv_connect_state);
        state.setText(stateStr);
        builder.setView(view);

        return builder.create();
    }



}
