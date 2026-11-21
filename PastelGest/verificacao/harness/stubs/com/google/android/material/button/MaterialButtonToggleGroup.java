package com.google.android.material.button;
public class MaterialButtonToggleGroup extends android.widget.LinearLayout { public MaterialButtonToggleGroup(android.content.Context c){super(c);}
  public interface OnButtonCheckedListener { void onButtonChecked(MaterialButtonToggleGroup g, int checkedId, boolean isChecked); }
  public void addOnButtonCheckedListener(OnButtonCheckedListener l){} public int getCheckedButtonId(){return 0;} }
