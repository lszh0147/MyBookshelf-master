package com.monke.monkeybook.view.fragment.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.monke.monkeybook.R;


public class AppCompatDialog extends AppCompatDialogFragment {

    private View mDialogView;

    public AppCompatDialog() {
        setStyle(STYLE_NO_TITLE, R.style.Style_Dialog);
    }

    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mDialogView = onCreateDialogView(inflater, container, savedInstanceState);
        if (mDialogView != null) {
            return mDialogView;
        }
        ViewGroup containerView = (ViewGroup) inflater.inflate(R.layout.dialog_design_alert, container, false);
        containerView.addView(onCreateDialogContentView(inflater, containerView, savedInstanceState));
        mDialogView = containerView;
        return mDialogView;
    }

    public View onCreateDialogView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    public View onCreateDialogContentView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return null;
    }

    public final boolean isViewCreated(){
        return mDialogView != null;
    }

    @SuppressWarnings("unchecked")
    public final <T> T findViewById(@IdRes int id) {
        if (mDialogView != null) {
            return (T) mDialogView.findViewById(id);
        }
        return null;
    }

    public final boolean isShowing() {
        final Dialog dialog = getDialog();
        return dialog != null && dialog.isShowing();
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            onDialogAttachWindow(window);
        }
    }

    protected void onDialogAttachWindow(@NonNull Window window) {
        window.setGravity(Gravity.CENTER);
        window.setLayout(getResources().getDimensionPixelSize(R.dimen.modialog_width), WindowManager.LayoutParams.WRAP_CONTENT);
    }
}
