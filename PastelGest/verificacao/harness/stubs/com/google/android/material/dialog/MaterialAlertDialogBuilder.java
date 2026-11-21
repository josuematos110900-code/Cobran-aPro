package com.google.android.material.dialog;
import android.content.DialogInterface;
public class MaterialAlertDialogBuilder { public MaterialAlertDialogBuilder(android.content.Context c){}
  public MaterialAlertDialogBuilder setTitle(CharSequence t){return this;} public MaterialAlertDialogBuilder setMessage(CharSequence m){return this;}
  public MaterialAlertDialogBuilder setView(android.view.View v){return this;}
  public MaterialAlertDialogBuilder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l){return this;}
  public MaterialAlertDialogBuilder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l){return this;}
  public androidx.appcompat.app.AlertDialog create(){return null;} public androidx.appcompat.app.AlertDialog show(){return null;} }
