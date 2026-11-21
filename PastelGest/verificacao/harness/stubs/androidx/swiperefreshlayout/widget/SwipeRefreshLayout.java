package androidx.swiperefreshlayout.widget;
public class SwipeRefreshLayout extends android.view.ViewGroup { public SwipeRefreshLayout(android.content.Context c){super(c);} protected void onLayout(boolean a,int b,int c,int d,int e){}
  public interface OnRefreshListener { void onRefresh(); } public void setOnRefreshListener(OnRefreshListener l){} public void setRefreshing(boolean r){} }
