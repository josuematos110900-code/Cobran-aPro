package com.google.android.material.navigation;
import android.view.MenuItem;
public abstract class NavigationBarView extends android.widget.FrameLayout { public NavigationBarView(android.content.Context c){super(c);}
  public interface OnItemSelectedListener { boolean onNavigationItemSelected(MenuItem item); }
  public interface OnItemReselectedListener { void onNavigationItemReselected(MenuItem item); }
  public void setOnItemSelectedListener(OnItemSelectedListener l){} public void setOnItemReselectedListener(OnItemReselectedListener l){}
  public void setSelectedItemId(int id){} }
