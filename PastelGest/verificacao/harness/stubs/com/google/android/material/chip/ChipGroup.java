package com.google.android.material.chip;
import java.util.List;
public class ChipGroup extends android.view.ViewGroup { public ChipGroup(android.content.Context c){super(c);} protected void onLayout(boolean a,int b,int c,int d,int e){}
  public interface OnCheckedStateChangeListener { void onCheckedChanged(ChipGroup group, List<Integer> checkedIds); }
  public void setOnCheckedStateChangeListener(OnCheckedStateChangeListener l){} }
