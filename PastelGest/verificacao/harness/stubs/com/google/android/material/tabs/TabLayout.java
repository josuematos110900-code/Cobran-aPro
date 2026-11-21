package com.google.android.material.tabs;
public class TabLayout extends android.widget.HorizontalScrollView { public TabLayout(android.content.Context c){super(c);}
  public static class Tab { public Tab setText(CharSequence t){return this;} public int getPosition(){return 0;} }
  public interface OnTabSelectedListener { void onTabSelected(Tab t); void onTabUnselected(Tab t); void onTabReselected(Tab t); }
  public Tab newTab(){return null;} public void addTab(Tab t){} public void addOnTabSelectedListener(OnTabSelectedListener l){} }
